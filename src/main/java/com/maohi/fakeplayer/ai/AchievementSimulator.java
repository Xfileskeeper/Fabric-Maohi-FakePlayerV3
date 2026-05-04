package com.maohi.fakeplayer.ai;

import com.maohi.fakeplayer.VirtualPlayerManager;
import com.maohi.fakeplayer.VirtualPlayerManager.Personality;
import com.maohi.fakeplayer.network.FakeClientConnection;
import com.maohi.fakeplayer.TimingConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

/**
 * 成就模拟器 (V3)
 */
public final class AchievementSimulator {

	private AchievementSimulator() {} // 工具类

	private static final String[] ADV_SEQUENCE = {
		"story/mine_stone", "story/upgrade_tools", "story/smelt_iron",
		"story/mine_diamond", "nether/obtain_crying_obsidian"
	};
	
	private static boolean isDiamondAchievement(String advancementId) {
		return "story/mine_diamond".equals(advancementId)
				|| "minecraft:story/mine_diamond".equals(advancementId);
	}

	private static boolean canGrantDiamondAchievement(VirtualPlayerManager.Personality personality) {
		if (personality == null) return false;
		// V5.17: 贴合 vanilla 行为 — 只要 phase 推到 DIAMOND_AGE 即可（即背包里有钻石），
		// 不要求"必须自己挖到钻石矿"。vanilla 的 [Diamonds!] 也只看 inventory_changed 事件。
		return personality.growthPhase != null
			&& personality.growthPhase.ordinal() >= VirtualPlayerManager.GrowthPhase.DIAMOND_AGE.ordinal();
	}

	/**
	 * 检查并尝试解锁成就
	 * @param server Minecraft 服务器实例
	 * @param p 假人玩家实体
	 * @param personality 假人个性数据
	 * @param playtimeMs 在线时长（毫秒）
	 * @param dataDirtyRef 数据脏标记回调（解锁后需设为 true）
	 */
	public static void tick(MinecraftServer server, ServerPlayerEntity p, Personality personality, long playtimeMs, Runnable markDirty) {
		// V5.17: 同步 vanilla 已自然触发的成就到 personality.unlockedAdvancements
		// 防止 vanilla 已发广播后 simulator 30 秒内再重复发一句吹牛 chat
		for (String adv : ADV_SEQUENCE) {
			if (personality.unlockedAdvancements.contains(adv)) continue;
			AdvancementEntry entry = server.getAdvancementLoader().get(Identifier.of(adv));
			if (entry == null) continue;
			if (p.getAdvancementTracker().getProgress(entry).isDone()) {
				personality.unlockedAdvancements.add(adv);
			}
		}

		int nextIdx = -1;
		for (int i = 0; i < ADV_SEQUENCE.length; i++) {
			if (!personality.unlockedAdvancements.contains(ADV_SEQUENCE[i])) {
				nextIdx = i;
				break;
			}
		}

		if (nextIdx == -1) return; // 全部解锁完毕

		int roll = ThreadLocalRandom.current().nextInt(1000);
		boolean success = false;
		int xpLevel = p.experienceLevel;
		// V5.17: 假人很少打怪获取真实 XP，把累计挖掘数折算成等效等级
		// 真实玩家挖矿也涨经验，每 10 个方块约等于 1 级（贴近原版挖矿掉落经验球节奏）
		int effectiveLevel = xpLevel + (personality.blocksMinedTotal / 10);

		if (nextIdx == 0 && playtimeMs > TimingConstants.ACHIEVEMENT_TIER1_PLAYTIME && roll < 900) success = true;
		else if (nextIdx == 1 && playtimeMs > TimingConstants.ACHIEVEMENT_TIER2_PLAYTIME && effectiveLevel >= 3 && roll < 700) success = true;
		else if (nextIdx == 2 && playtimeMs > TimingConstants.ACHIEVEMENT_TIER3_PLAYTIME && effectiveLevel >= 5 && roll < 300) success = true;
		else if (nextIdx == 3 && playtimeMs > TimingConstants.ACHIEVEMENT_TIER4_PLAYTIME && effectiveLevel >= 10 && roll < 80) {
			if (canGrantDiamondAchievement(personality)) {
				success = true;
			}
		}
		else if (nextIdx == 4 && playtimeMs > TimingConstants.ACHIEVEMENT_TIER5_PLAYTIME && effectiveLevel >= 15 && roll < 10) success = true;

		if (success) {
			String adv = ADV_SEQUENCE[nextIdx];
			// 二次门禁校验（针对 Diamonds!）
			if (isDiamondAchievement(adv) && !canGrantDiamondAchievement(personality)) {
				return;
			}
			personality.unlockedAdvancements.add(adv);
			personality.hasUnlockedThisSession = true;
			markDirty.run();

			// V5.15+V5.17 特性：成就触发物资升级，每档成就附带"下一阶段启动物资"
			// 与 vanilla 玩家自然进度感对齐：达成里程碑后能继续推进，避免假人卡死在阶段间
			net.minecraft.entity.player.PlayerInventory inv = p.getInventory();
			if (adv.equals("story/mine_stone")) {
				// Tier 1: 给几块圆石 + 石镐 → STONE_AGE 装备成型
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.COBBLESTONE, 3 + ThreadLocalRandom.current().nextInt(5)));
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE_PICKAXE, 1));
			} else if (adv.equals("story/upgrade_tools")) {
				// Tier 2: 给少量原铁 + 煤炭 → 让 autoSmeltOres 有材料工作，推进 IRON_AGE
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.RAW_IRON, 2 + ThreadLocalRandom.current().nextInt(3)));
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL, 2 + ThreadLocalRandom.current().nextInt(3)));
			} else if (adv.equals("story/smelt_iron")) {
				// Tier 3: 给铁镐 → 可以挖钻石矿，自然推进到 DIAMOND_AGE
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_PICKAXE, 1));
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT, 2 + ThreadLocalRandom.current().nextInt(3)));
			} else if (adv.equals("story/mine_diamond")) {
				// Tier 4: 给黑曜石 + 打火石 → 让 PhaseDiamondAge 走上建造下界传送门路径
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.OBSIDIAN, 10 + ThreadLocalRandom.current().nextInt(4)));
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.FLINT_AND_STEEL, 1));
			} else if (adv.equals("nether/obtain_crying_obsidian")) {
				// Tier 5: 在下界拿到哭泣黑曜石；附送末影珍珠 + 烈焰棒 → 为去末地铺路
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.ENDER_PEARL, 12 + ThreadLocalRandom.current().nextInt(5)));
				inv.offerOrDrop(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BLAZE_ROD, 6 + ThreadLocalRandom.current().nextInt(3)));
			}

			// 主线程安全发放荣誉，加入随机延迟增加凌乱美
			int jitterMs = ThreadLocalRandom.current().nextInt(TimingConstants.JITTER_MIN_MS, TimingConstants.JITTER_MAX_MS);
			server.execute(() -> {
				Identifier id = Identifier.of(adv);
				AdvancementEntry entry = server.getAdvancementLoader().get(id);
				if (entry != null) {
					PlayerAdvancementTracker tracker = p.getAdvancementTracker();
					FakeClientConnection.KEEP_ALIVE_POOL.schedule(() -> {
						server.execute(() -> {
							for (String criterion : entry.value().criteria().keySet()) {
								tracker.grantCriterion(entry, criterion);
							}
						});
					}, jitterMs, java.util.concurrent.TimeUnit.MILLISECONDS);
				}
			});
		}
	}
}
