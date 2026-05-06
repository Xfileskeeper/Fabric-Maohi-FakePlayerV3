package com.maohi.fakeplayer.ai;

import com.maohi.fakeplayer.Personality;
import com.maohi.fakeplayer.TaskType;
import com.maohi.fakeplayer.network.PacketHelper;
import com.maohi.mixin.PlayerInventoryAccessor;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 方块放置模拟器 (V3)
 *
 * V5.23 修复:
 *   原实现 tryPlaceTorch 在同一 tick 内连续打 4 个包(切槽→交互→挥手→切回原槽位),
 *   反作弊看到的就是"0ms 切槽 + 0ms 切回"——任何检测 hotbar swap 频率的插件都会报警。
 *   真实客户端切换 hotbar 至少要 1 tick 渲染 + 玩家手指反应 ≈ 100~200ms,使用方块再切回
 *   通常需要 200~400ms。
 *
 *   改成 3 阶段状态机,跨 tick 推进:
 *     stage 0 → 1: 切到火把槽,记录 placeAtTick = now + 3~6 tick
 *     stage 1 → 2: 到达 placeAtTick,interactBlock+swing,记录 restoreAtTick = now + 4~8 tick
 *     stage 2 → 0: 到达 restoreAtTick,切回原槽位,完成。
 *
 *   过程中如果 stage>0 立刻退出,不会重复触发。
 */
public class BlockPlacer {

	/** 放置阶段相邻包之间的最小 tick 间隔(50ms/tick) */
	private static final int PLACE_DELAY_MIN = 3;   // 150ms
	private static final int PLACE_DELAY_MAX = 6;   // 300ms
	private static final int RESTORE_DELAY_MIN = 4; // 200ms
	private static final int RESTORE_DELAY_MAX = 8; // 400ms

	/**
	 * 检查并尝试放置火把(状态机 tick)
	 * 触发条件: 正在挖矿/探索 + 环境太暗 + 包里有火把
	 * VPM 每 tick 调用一次。
	 */
	public static void tryPlaceTorch(ServerPlayerEntity player, Personality personality) {
		long now = player.getEntityWorld().getTime();

		// 状态机推进:已经在某个阶段中 → 不重新发起,只推进
		if (personality.torchPlaceStage > 0) {
			advanceTorchStateMachine(player, personality, now);
			return;
		}

		// 1. 只有挖矿或探索状态才会插火把
		if (personality.currentTask != TaskType.MINING &&
			personality.currentTask != TaskType.EXPLORING) {
			return;
		}

		// 2. 频率控制:每 tick 5% 概率检查,避免密密麻麻全是火把
		if (ThreadLocalRandom.current().nextInt(20) != 0) return;

		BlockPos pos = player.getBlockPos();

		// 3. 亮度判定:低于 7 才插火把
		int lightLevel = player.getEntityWorld().getLightLevel(LightType.BLOCK, pos);
		if (lightLevel >= 7) return;
		// 露天且白天不需要插火把
		if (player.getEntityWorld().getLightLevel(LightType.SKY, pos) > 10 && player.getEntityWorld().isDay()) {
			return;
		}

		// 4. 检查快捷栏 (0-8) 是否有火把
		PlayerInventory inv = player.getInventory();
		int torchSlot = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(Items.TORCH)) {
				torchSlot = i;
				break;
			}
		}
		// 包里没火把就摸黑挖,真人也这样
		if (torchSlot == -1) return;

		// 5. 目标方块:假人脚下 — 必须是非空气才能放火把
		BlockPos blockUnder = pos.down();
		if (player.getEntityWorld().getBlockState(blockUnder).isAir()) return;

		// 6. 已经在脚下或当前手就是火把 → 直接走 stage 1 略过切槽
		int currentSlot = ((PlayerInventoryAccessor) inv).getSelectedSlot();

		// === stage 0 → 1: 切到火把槽,推进到等待放置阶段 ===
		personality.torchOriginalSlot = currentSlot;
		personality.torchTargetSlot = torchSlot;
		personality.torchPlaceBlockPos = blockUnder;
		personality.torchPlaceAtTick = now + PLACE_DELAY_MIN
			+ ThreadLocalRandom.current().nextInt(PLACE_DELAY_MAX - PLACE_DELAY_MIN + 1);
		personality.torchPlaceStage = 1;

		// 当前手已经是火把(currentSlot == torchSlot)就跳过发切槽包,但 stage 仍走流程
		if (currentSlot != torchSlot) {
			PacketHelper.setSelectedSlot(player, torchSlot);
		}
	}

	/**
	 * V5.23: 推进火把放置状态机的下一步。
	 * 在 stage>0 时被调用;到达计划时间才执行对应包,否则直接 return。
	 */
	private static void advanceTorchStateMachine(ServerPlayerEntity player, Personality personality, long now) {
		// === stage 1 → 2: 到达放置时刻,执行 interactBlock + 挥手 ===
		if (personality.torchPlaceStage == 1 && now >= personality.torchPlaceAtTick) {
			BlockPos blockUnder = personality.torchPlaceBlockPos;
			if (blockUnder == null) {
				resetTorchState(personality);
				return;
			}
			// 校验目标方块仍然合法(假人可能已经走开/方块被破坏)
			if (player.getEntityWorld().getBlockState(blockUnder).isAir()) {
				resetTorchState(personality);
				return;
			}
			// 校验槽位仍然有火把(可能被消耗光了)
			ItemStack target = player.getInventory().getStack(personality.torchTargetSlot);
			if (target.isEmpty() || !target.isOf(Items.TORCH)) {
				resetTorchState(personality);
				return;
			}
			BlockHitResult hit = new BlockHitResult(
				Vec3d.ofCenter(blockUnder).add(0, 0.5, 0),
				Direction.UP,
				blockUnder,
				false
			);
			PacketHelper.interactBlock(player, Hand.MAIN_HAND, hit);
			PacketHelper.swingHand(player, Hand.MAIN_HAND);

			personality.torchRestoreAtTick = now + RESTORE_DELAY_MIN
				+ ThreadLocalRandom.current().nextInt(RESTORE_DELAY_MAX - RESTORE_DELAY_MIN + 1);
			personality.torchPlaceStage = 2;
			return;
		}

		// === stage 2 → 0: 切回原槽位 ===
		if (personality.torchPlaceStage == 2 && now >= personality.torchRestoreAtTick) {
			int original = personality.torchOriginalSlot;
			int currentSlot = ((PlayerInventoryAccessor) player.getInventory()).getSelectedSlot();
			if (original != currentSlot) {
				PacketHelper.setSelectedSlot(player, original);
			}
			resetTorchState(personality);
		}
	}

	private static void resetTorchState(Personality p) {
		p.torchPlaceStage = 0;
		p.torchOriginalSlot = 0;
		p.torchTargetSlot = 0;
		p.torchPlaceBlockPos = null;
		p.torchPlaceAtTick = 0L;
		p.torchRestoreAtTick = 0L;
	}
}
