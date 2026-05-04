package com.maohi.fakeplayer.ai.phase;

import com.maohi.fakeplayer.TimingConstants;
import com.maohi.fakeplayer.VirtualPlayerManager;
import com.maohi.fakeplayer.VirtualPlayerManager.Personality;
import com.maohi.fakeplayer.VirtualPlayerManager.TaskType;
import com.maohi.fakeplayer.VirtualPlayerManager.GrowthPhase;
import com.maohi.fakeplayer.ai.AchievementSimulator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 第三阶段：钻石时代 (V3)
 */
public final class PhaseDiamondAge {

    private PhaseDiamondAge() {}

    /** 识别是否为钻石矿 (V5.5) */
    public static boolean isDiamondOre(BlockState state) {
        return state != null && (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE));
    }

    /** 标记钻石挖掘成功并推进状态 (V5.5) */
    public static void markDiamondOreMined(ServerPlayerEntity player, Personality personality) {
        if (player == null || personality == null) return;

        // 1. 设置物理挖掘证据与时间戳
        personality.hasMinedDiamondOre = true;
        personality.lastDiamondOreMinedAt = System.currentTimeMillis();

        // 2. 静默推进成长阶段
        if (personality.growthPhase != GrowthPhase.DIAMOND_AGE) {
            personality.growthPhase = GrowthPhase.DIAMOND_AGE;
            personality.phaseEnteredAt = System.currentTimeMillis();
        }
        
        // 3. 触发真实成就发放（基于已就绪的状态）
        // NOTE: 如果调用处有 server 变量，建议通过 AchievementSimulator.forceGrantAchievement 触发
        // 此处逻辑依赖后续 tick 检查或强制触发
    }

    public static void assignTask(ServerPlayerEntity player, Personality personality,
                                   java.util.function.BiFunction<net.minecraft.server.world.ServerWorld, BlockPos, BlockPos> findOre,
                                   java.util.function.BiFunction<net.minecraft.server.world.ServerWorld, BlockPos, BlockPos> findLog,
                                   java.util.function.Supplier<net.minecraft.entity.mob.HostileEntity> findHunt) {
        // 钻石层挖矿：Y=-50 ~ -60
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 50) {
            int mineY = -50 - ThreadLocalRandom.current().nextInt(10);
            BlockPos target = findOre.apply(player.getEntityWorld(), player.getBlockPos());
            if (target == null) target = new BlockPos(player.getBlockX() + rnd(10) - 5, mineY, player.getBlockZ() + rnd(10) - 5);
            set(personality, TaskType.MINING, target, TimingConstants.TASK_TIMEOUT_WORK);
        } else if (roll < 70) {
            BlockPos target = findLog.apply(player.getEntityWorld(), player.getBlockPos());
            if (target == null) target = player.getBlockPos().add(rnd(60) - 30, 0, rnd(60) - 30);
            set(personality, TaskType.WOODCUTTING, target, TimingConstants.TASK_TIMEOUT_WORK);
        } else if (roll < 85) {
            net.minecraft.entity.mob.HostileEntity huntTarget = findHunt.get();
            if (huntTarget != null) {
                personality.currentTask = TaskType.HUNTING;
                personality.taskTarget = huntTarget.getBlockPos();
                personality.huntTargetUuid = huntTarget.getUuid();
                personality.taskExpireTime = System.currentTimeMillis() + 30_000L;
                return;
            }
            set(personality, TaskType.EXPLORING, player.getBlockPos().add(rnd(80) - 40, 0, rnd(80) - 40), TimingConstants.TASK_TIMEOUT_EXPLORE);
        } else {
            set(personality, TaskType.EXPLORING, player.getBlockPos().add(rnd(80) - 40, 0, rnd(80) - 40), TimingConstants.TASK_TIMEOUT_EXPLORE);
        }
    }

    private static void set(Personality p, TaskType type, BlockPos target, long timeout) {
        p.currentTask = type;
        p.taskTarget = target;
        p.taskExpireTime = System.currentTimeMillis() + timeout;
    }

    private static int rnd(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
}
