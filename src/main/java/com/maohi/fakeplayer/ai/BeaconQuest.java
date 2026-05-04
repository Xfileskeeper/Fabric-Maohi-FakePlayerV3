package com.maohi.fakeplayer.ai;

import com.maohi.fakeplayer.VirtualPlayerManager;
import com.maohi.fakeplayer.VirtualPlayerManager.Personality;
import com.maohi.fakeplayer.VirtualPlayerManager.TaskType;
import com.maohi.fakeplayer.VirtualPlayerManager.GrowthPhase;
import com.maohi.fakeplayer.network.PacketHelper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 信标挑战长线任务 AI (V5.19)
 * 完整模拟从下界打骷髅头到放置信标的拟真流程
 */
public final class BeaconQuest {

    private BeaconQuest() {} // 工具类

    public static void tickBeaconQuest(ServerPlayerEntity player, Personality personality) {
        if (personality == null) return;
        
        // 只有进入下界阶段后才可能开启信标任务
        if (personality.growthPhase == null || personality.growthPhase.ordinal() < GrowthPhase.NETHER.ordinal()) return;

        ServerWorld world = player.getServerWorld();
        long now = System.currentTimeMillis();

        switch (personality.beaconStage) {
            case NOT_STARTED -> tryStartQuest(player, personality);
            case SEEKING_FORTRESS -> tickSeekingFortress(player, personality, world);
            case HUNTING_WITHER_SKELETONS -> tickHuntingWitherSkeletons(player, personality, world);
            case GATHERING_SOUL_SAND -> tickGatheringSoulSand(player, personality, world);
            case BUILDING_WITHER -> tickBuildingWither(player, personality, world);
            case FIGHTING_WITHER -> tickFightingWither(player, personality, world);
            case GATHERING_BEACON_MATERIALS -> tickGatheringBeaconMaterials(player, personality, world);
            case CRAFTING_BEACON -> tickCraftingBeacon(player, personality);
            case BUILDING_PYRAMID -> tickBuildingPyramid(player, personality, world);
            case PLACING_BEACON -> tickPlacingBeacon(player, personality, world);
            case DONE -> {}
        }
    }

    private static void tryStartQuest(ServerPlayerEntity player, Personality personality) {
        // 节流：每 10 分钟评估一次
        if (ThreadLocalRandom.current().nextInt(12000) != 0) return;
        
        // 必须在下界维度才能开始找要塞
        if (player.getEntityWorld().getRegistryKey() != net.minecraft.world.World.NETHER) return;

        enterStage(personality, BeaconQuestStage.SEEKING_FORTRESS);
    }

    private static void tickSeekingFortress(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        if (ThreadLocalRandom.current().nextInt(100) != 0) return;

        // 寻找要塞砖块密集区
        boolean found = false;
        BlockPos current = player.getBlockPos();
        for (int r = -32; r <= 32; r += 8) {
            BlockPos check = current.add(r, 0, 0);
            if (world.getBlockState(check).isOf(Blocks.NETHER_BRICKS)) {
                found = true;
                break;
            }
        }

        if (found) {
            enterStage(personality, BeaconQuestStage.HUNTING_WITHER_SKELETONS);
        } else {
            // 没找到则随机移动找要塞
            if (personality.currentTask == TaskType.IDLE) {
                personality.taskTarget = current.add(rnd(60)-30, 0, rnd(60)-30);
                personality.currentTask = TaskType.EXPLORING;
                personality.taskExpireTime = System.currentTimeMillis() + 30000L;
            }
        }
    }

    private static void tickHuntingWitherSkeletons(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        int skullCount = countItem(player.getInventory(), Items.WITHER_SKELETON_SKULL);
        if (skullCount >= 3) {
            enterStage(personality, BeaconQuestStage.GATHERING_SOUL_SAND);
            return;
        }

        // 寻找凋零骷髅
        if (ThreadLocalRandom.current().nextInt(100) == 0) {
            Box box = player.getBoundingBox().expand(32.0);
            List<WitherSkeletonEntity> targets = world.getEntitiesByClass(WitherSkeletonEntity.class, box, e -> e.isAlive());
            if (!targets.isEmpty()) {
                personality.taskTarget = targets.get(0).getBlockPos();
                personality.currentTask = TaskType.HUNTING;
                personality.huntTargetUuid = targets.get(0).getUuid();
                personality.taskExpireTime = System.currentTimeMillis() + 30000L;
            }
        }
    }

    private static void tickGatheringSoulSand(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        int sandCount = countItem(player.getInventory(), Items.SOUL_SAND);
        if (sandCount >= 4) {
            enterStage(personality, BeaconQuestStage.BUILDING_WITHER);
            return;
        }

        // 扫附近灵魂沙
        if (personality.currentTask == TaskType.IDLE) {
            BlockPos target = findNearestBlock(world, player.getBlockPos(), 16, Blocks.SOUL_SAND);
            if (target != null) {
                personality.taskTarget = target;
                personality.currentTask = TaskType.MINING;
                personality.taskExpireTime = System.currentTimeMillis() + 30000L;
            }
        }
    }

    private static void tickBuildingWither(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        // 远离核心区放置，选择一个相对开阔的地点
        if (personality.witherBuildPos == null) {
            personality.witherBuildPos = player.getBlockPos().add(rnd(20)-10, 0, rnd(20)-10);
            return;
        }

        // 真实性要求：走到位置
        if (player.getBlockPos().getSquaredDistance(personality.witherBuildPos) > 16.0) {
            personality.taskTarget = personality.witherBuildPos;
            personality.currentTask = TaskType.EXPLORING;
            return;
        }

        // 拟真延迟：放方块
        if (now() - personality.beaconStageEnteredAt < 5000L) return;

        // V5.19 修复：手动生成凋零实体并清理结构（world.setBlockState 不会触发 vanilla 召唤检测）
        WitherEntity wither = EntityType.WITHER.create(world, net.minecraft.entity.SpawnReason.MOB_SUMMONED);
        if (wither != null) {
            BlockPos base = personality.witherBuildPos;
            Vec3d spawnAt = Vec3d.ofCenter(base.up(2));
            wither.refreshPositionAndAngles(spawnAt.x, spawnAt.y, spawnAt.z, 0, 0);
            wither.setInvulTimer(220); // 召唤无敌期
            world.spawnEntity(wither);

            // 清理召唤结构方块
            world.setBlockState(base, Blocks.AIR.getDefaultState());
            world.setBlockState(base.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(base.up().west(), Blocks.AIR.getDefaultState());
            world.setBlockState(base.up().east(), Blocks.AIR.getDefaultState());
            world.setBlockState(base.up(2), Blocks.AIR.getDefaultState());
            world.setBlockState(base.up(2).west(), Blocks.AIR.getDefaultState());
            world.setBlockState(base.up(2).east(), Blocks.AIR.getDefaultState());

            enterStage(personality, BeaconQuestStage.FIGHTING_WITHER);
        }
    }

    private static void tickFightingWither(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        if (hasItem(player.getInventory(), Items.NETHER_STAR)) {
            enterStage(personality, BeaconQuestStage.GATHERING_BEACON_MATERIALS);
            return;
        }

        // 寻找附近的凋零实体
        Box box = player.getBoundingBox().expand(64.0);
        List<WitherEntity> withers = world.getEntitiesByClass(WitherEntity.class, box, e -> e.isAlive());
        if (!withers.isEmpty()) {
            personality.taskTarget = withers.get(0).getBlockPos();
            personality.currentTask = TaskType.HUNTING;
            personality.huntTargetUuid = withers.get(0).getUuid();
            personality.taskExpireTime = System.currentTimeMillis() + 60000L;
        } else {
            // V5.19 修复：延长超时并确认实体确实消失才回滚
            if (now() - personality.beaconStageEnteredAt > 600000L) {
                enterStage(personality, BeaconQuestStage.HUNTING_WITHER_SKELETONS);
            }
        }
    }

    private static void tickGatheringBeaconMaterials(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        PlayerInventory inv = player.getInventory();
        if (countItem(inv, Items.GLASS) >= 5 && countItem(inv, Items.OBSIDIAN) >= 3) {
            enterStage(personality, BeaconQuestStage.CRAFTING_BEACON);
            return;
        }
        
        // V5.19 修复：移除 stub 注入，改由 SurvivalMechanics 真实路径或任务奖励获得
        // 此处暂不处理，等待下一步真实采集逻辑接入
    }

    private static void tickCraftingBeacon(ServerPlayerEntity player, Personality personality) {
        if (hasItem(player.getInventory(), Items.BEACON)) {
            enterStage(personality, BeaconQuestStage.BUILDING_PYRAMID);
            return;
        }
        // 逻辑交由 SurvivalMechanics.tickCrafting 处理，此处仅检查
    }

    private static void tickBuildingPyramid(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        // 在地面主世界寻找合适位置
        if (player.getEntityWorld().getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

        if (personality.beaconPlacePos == null) {
            personality.beaconPlacePos = player.getBlockPos().add(rnd(10)-5, 0, rnd(10)-5);
            return;
        }

        // 放一个最简单的 3x3 铁块基座
        BlockPos base = personality.beaconPlacePos.down();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(base.add(x, 0, z), Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        enterStage(personality, BeaconQuestStage.PLACING_BEACON);
    }

    private static void tickPlacingBeacon(ServerPlayerEntity player, Personality personality, ServerWorld world) {
        if (personality.beaconPlacePos == null) return;
        
        PlayerInventory inv = player.getInventory();
        int beaconSlot = findItemSlot(inv, Items.BEACON);
        if (beaconSlot == -1) return; // V5.19 修复：没信标则退出，不执行 setBlockState

        // 走到位置并放置
        if (player.getBlockPos().getSquaredDistance(personality.beaconPlacePos) > 16.0) {
            personality.taskTarget = personality.beaconPlacePos;
            personality.currentTask = TaskType.EXPLORING;
            return;
        }

        // 真实交互
        PacketHelper.setSelectedSlot(player, beaconSlot);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(personality.beaconPlacePos), Direction.UP, personality.beaconPlacePos.down(), false);
        PacketHelper.interactBlock(player, Hand.MAIN_HAND, hit);
        
        // V5.19 修复：移除 setBlockState 兜底，验证放置成功后才推进
        if (world.getBlockState(personality.beaconPlacePos).isOf(Blocks.BEACON)) {
            enterStage(personality, BeaconQuestStage.DONE);
        }
    }

    private static void enterStage(Personality personality, BeaconQuestStage stage) {
        personality.beaconStage = stage;
        personality.beaconStageEnteredAt = System.currentTimeMillis();
    }

    private static int countItem(PlayerInventory inv, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) count += inv.getStack(i).getCount();
        }
        return count;
    }

    private static boolean hasItem(PlayerInventory inv, net.minecraft.item.Item item) {
        return countItem(inv, item) > 0;
    }

    private static int findItemSlot(PlayerInventory inv, net.minecraft.item.Item item) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) return i;
        }
        return -1; // V5.19 修复：没找到返回 -1
    }

    private static BlockPos findNearestBlock(ServerWorld world, BlockPos center, int radius, net.minecraft.block.Block block) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) { // V5.19 修复：缩小 Y 轴搜索范围，极大提升性能
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = center.add(x, y, z);
                    if (world.getBlockState(p).isOf(block)) return p;
                }
            }
        }
        return null;
    }

    private static int rnd(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    private static long now() { return System.currentTimeMillis(); }
}
