package com.maohi.fakeplayer.ai.phase;

import com.maohi.fakeplayer.TimingConstants;
import com.maohi.fakeplayer.Personality;
import com.maohi.fakeplayer.TaskType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 第二阶段：铁器时代 (V3)
 *
 * 目标：获取铁矿、制作铁器全套，为钻石时代做准备
 * 进入条件：背包有石器（石镐/石剑/石斧）
 * 毕业条件：背包拥有钻石镐或钻石剑
 *
 * 任务优先级：
 *   1. 挖矿为主（找铁矿/煤矿，走矿石层 Y=8~15）55%
 *   2. 砍树补充木材 20%
 *   3. 打猎获取食物/经验 15%
 *   4. 探索 10%
 *
 * 待完善：
 *   - 找熔炉/制作熔炉冶炼铁锭
 *   - 制作铁器三件套
 *   - 建造简易基地
 */
public final class PhaseIronAge implements Phase {

    public static final Phase INSTANCE = new PhaseIronAge();

    private PhaseIronAge() {}

    @Override
    public void assignTask(ServerPlayerEntity player, Personality personality, PhaseContext ctx) {
        int roll = ThreadLocalRandom.current().nextInt(100);

        if (roll < 55) {
            BlockPos target = ctx.findOre.apply(player.getEntityWorld(), player.getBlockPos());
            if (target == null) {
                // V5.22: 找不到矿就先走到附近随机点(脚下 5 格内 down 1~3),
                //   让假人跑去挖石头,而不是给一个绝对到不了的 Y=8 目标
                int dx = rnd(10) - 5;
                int dz = rnd(10) - 5;
                int dy = -1 - ThreadLocalRandom.current().nextInt(3);
                target = player.getBlockPos().add(dx, dy, dz);
            }
            set(personality, TaskType.MINING, target, TimingConstants.TASK_TIMEOUT_WORK);
        } else if (roll < 75) {
            BlockPos target = ctx.findLog.apply(player.getEntityWorld(), player.getBlockPos());
            if (target == null) target = player.getBlockPos().add(rnd(60) - 30, 0, rnd(60) - 30);
            set(personality, TaskType.WOODCUTTING, target, TimingConstants.TASK_TIMEOUT_WORK);
        } else if (roll < 90) {
            net.minecraft.entity.mob.HostileEntity huntTarget = ctx.findHunt.get();
            if (huntTarget != null) {
                personality.currentTask = TaskType.HUNTING;
                personality.taskTarget = huntTarget.getBlockPos();
                personality.huntTargetUuid = huntTarget.getUuid();
                personality.taskExpireTime = System.currentTimeMillis() + 30_000L;
                return;
            }
            set(personality, TaskType.EXPLORING, player.getBlockPos().add(rnd(60) - 30, 0, rnd(60) - 30), TimingConstants.TASK_TIMEOUT_EXPLORE);
        } else {
            set(personality, TaskType.EXPLORING, player.getBlockPos().add(rnd(60) - 30, 0, rnd(60) - 30), TimingConstants.TASK_TIMEOUT_EXPLORE);
        }
    }

    private static void set(Personality p, TaskType type, BlockPos target, long timeout) {
        p.currentTask = type;
        p.taskTarget = target;
        p.taskExpireTime = System.currentTimeMillis() + timeout;
    }

    private static int rnd(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
}
