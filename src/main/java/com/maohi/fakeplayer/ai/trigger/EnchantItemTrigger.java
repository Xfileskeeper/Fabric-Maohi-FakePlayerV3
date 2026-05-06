package com.maohi.fakeplayer.ai.trigger;

import com.maohi.fakeplayer.GrowthPhase;
import com.maohi.fakeplayer.Personality;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Enchanter: 用附魔台附魔物品 (V5.22 第五阶段 — 终局 占位)
 *
 * TODO:
 *   - 检测背包是否有附魔台 + 青金石 + 附魔书/工具
 *   - 寻找或放置附魔台(需要 4 个黑曜石 + 2 个钻石 + 1 本书)
 *   - 真实发包:打开附魔界面 → 选择槽位 → 消耗经验
 *   - vanilla 触发:EnchantmentScreenHandler.enchant → enchanted_item criterion → [story/enchant_item]
 *
 * 阶段判定:ENDGAME 起(此前假人没附魔台材料)
 * 注意:附魔需要经验值——假人的 xp 来源主要靠击杀怪物和挖矿,ENDGAME 阶段应当足够
 */
public final class EnchantItemTrigger implements AchievementTrigger {

	public static final EnchantItemTrigger INSTANCE = new EnchantItemTrigger();
	private static final String ADV_ID = "story/enchant_item";

	private EnchantItemTrigger() {}

	@Override
	public String advancementId() { return ADV_ID; }

	@Override
	public long[] nextIntervalRange() { return new long[]{120_000L, 600_000L}; } // 2~10min

	@Override
	public boolean shouldRun(ServerPlayerEntity player, Personality personality) {
		if (TriggerUtil.alreadyUnlocked(personality, ADV_ID)) return false;
		return personality.growthPhase != null
			&& personality.growthPhase.ordinal() >= GrowthPhase.ENDGAME.ordinal();
	}

	@Override
	public void tryTrigger(ServerPlayerEntity player, Personality personality) {
		// TODO V5.23:接入附魔台寻找/放置 + 附魔界面交互
	}
}
