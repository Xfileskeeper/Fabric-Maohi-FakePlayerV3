package com.maohi.fakeplayer.ai;

/**
 * 信标挑战阶段子状态机 (V5.19)
 */
public enum BeaconQuestStage {
    NOT_STARTED,
    SEEKING_FORTRESS,      // 在下界找下界要塞
    HUNTING_WITHER_SKELETONS,  // 在要塞打凋零骷髅，攒头
    GATHERING_SOUL_SAND,   // 收集 4 个灵魂沙
    BUILDING_WITHER,       // 放置 T 形结构
    FIGHTING_WITHER,       // 战斗
    GATHERING_BEACON_MATERIALS, // 玻璃 + 黑曜石
    CRAFTING_BEACON,       // 合成信标
    BUILDING_PYRAMID,      // 放金字塔
    PLACING_BEACON,        // 顶上放信标
    DONE
}
