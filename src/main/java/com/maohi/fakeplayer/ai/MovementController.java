package com.maohi.fakeplayer.ai;

import com.maohi.fakeplayer.GrowthPhase;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 智能运动控制器 (V3)
 *
 * V5.22 优化:
 *   - findPath 失败冷却 5s,杜绝主线程每 tick 跑 A*(不可达目标时的 lag 元凶)
 *   - 到达检测距离统一(终点 / waypoint 都用 2.25,防止终点抽搐)
 *   - noiseTime 周期性 mod,避免 double 累积进入退化抖动
 *   - sightseeing 早期阶段豁免,基础成就期不被"看风景"吃 5 秒
 *   - 方向切换误触发概率从 1/10 降到 1/40
 */
public class MovementController {

	/** 寻路失败后的冷却时长(毫秒) */
	private static final long PATHFIND_FAIL_COOLDOWN_MS = 5_000L;

	/**
	 * 噪声采样时间步进。每 tick 递增,但用 mod 防止 double 进入退化区间。
	 * 周期 = 1_000_000 tick ≈ 13.9 小时,远超单 session 时长,且周期内不会重复抖动模式。
	 */
	private static double noiseTime = 0;
	private static final double NOISE_TIME_PERIOD = 1_000_000.0;

	/**
	 * 简化 Perlin 噪声（1D）
	 * 基于多频率正弦叠加，模拟 Perlin 噪声的自然漂浮感
	 */
	private static float perlinLike(double phase, double time, float amp) {
		double v = Math.sin(phase + time * 0.013) * 0.5
				+ Math.sin(phase * 1.7 + time * 0.047) * 0.3
				+ Math.sin(phase * 2.3 + time * 0.11) * 0.2;
		return (float) (v * amp);
	}

	/** 每 tick 递增噪声时间(防 double 累积失真) */
	public static void tickNoise() {
		noiseTime = (noiseTime + 1.0) % NOISE_TIME_PERIOD;
	}

	/** 获取当前噪声时间 */
	public static double getNoiseTime() {
		return noiseTime;
	}

	/**
	 * 停止所有移动输入
	 * V3.2 1.21.11 适配：通过 Access Widener 直接设置 LivingEntity 的 forwardSpeed/sidewaysSpeed 字段
	 */
	private static void stopMovement(ServerPlayerEntity p) {
		p.forwardSpeed = 0.0f;
		p.sidewaysSpeed = 0.0f;
		p.setSprinting(false);
	}

	/**
	 * 设置前进输入
	 * @param forward 前进速度 (-1.0 ~ 1.0)
	 * @param sideways 横向速度 (-1.0 ~ 1.0)
	 */
	private static void setMovement(ServerPlayerEntity p, float forward, float sideways) {
		com.maohi.fakeplayer.Personality pers =
			com.maohi.fakeplayer.Personality.get(p);
		
		if (pers != null) {
			// V5.2 Keyboard Fingerprint: WASD 松键间隙模拟
			if (pers.keyReleaseMicroGapTicks > 0) {
				pers.keyReleaseMicroGapTicks--;
				p.forwardSpeed = 0.0f;
				p.sidewaysSpeed = 0.0f;
				return;
			}
			
			// 方向大切换时产生随机停顿
			// V5.22: 误触发概率从 1/10 降到 1/40——原值在每次启停时几乎必触发,
			//        导致 mining"接近目标→减速→停"反复抽搐
			if (Math.abs(forward - p.forwardSpeed) > 0.5f || Math.abs(sideways - p.sidewaysSpeed) > 0.5f) {
				if (ThreadLocalRandom.current().nextInt(40) == 0) {
					pers.keyReleaseMicroGapTicks = 1 + ThreadLocalRandom.current().nextInt(2); // 50-100ms
					return;
				}
			}
		}

		p.forwardSpeed = forward;
		p.sidewaysSpeed = sideways;
	}

	/**
	 * 智能执行一帧的位移计算
	 * V4: 接入 A* 路径缓存，遇到障碍时绕路而不是放弃
	 * @return true 表示到达目标或无路可走，需要重新分配目标点
	 */
	public static boolean doSmartMove(ServerPlayerEntity p, BlockPos target, double moveStep,
			double noisePhaseYaw, double noisePhasePitch) {
		if (target == null) { stopMovement(p); return true; }

		com.maohi.fakeplayer.Personality pers =
			com.maohi.fakeplayer.Personality.get(p);

		// V5.0 A: 物理跳跃检测 (识别 1 格坑)
		BlockPos ahead = p.getBlockPos().offset(p.getHorizontalFacing());
		if (p.isOnGround() && p.getEntityWorld().getBlockState(ahead).isAir() 
			&& !p.getEntityWorld().getBlockState(ahead.offset(p.getHorizontalFacing())).isAir()) {
			p.jump();
		}

		// 平滑转向逻辑
		if (pers != null && pers.sightseeingTicks > 0) {
			pers.sightseeingTicks--;
			stopMovement(p);
			p.setYaw(p.getYaw() + perlinLike(noisePhaseYaw * 0.8, noiseTime, 1.5f));
			return false;
		}
		// V5.22: 早期阶段(石器/铁器)豁免 sightseeing,基础成就期不能被"看风景"吃 5 秒
		boolean lateGame = pers != null && pers.growthPhase != null
			&& pers.growthPhase.ordinal() >= GrowthPhase.DIAMOND_AGE.ordinal();
		if (lateGame && ThreadLocalRandom.current().nextInt(300) == 0) {
			pers.sightseeingTicks = 60 + ThreadLocalRandom.current().nextInt(100);
			stopMovement(p);
			return false;
		}

		ServerWorld world = p.getEntityWorld();
		Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());

		// 到达目标
		// V5.22: 阈值统一为 2.25(原 1.5),与 waypoint 到达检测一致,防终点附近抽搐
		double dx = target.getX() + 0.5 - pos.x;
		double dz = target.getZ() + 0.5 - pos.z;
		if (dx * dx + dz * dz <= 2.25) { stopMovement(p); return true; }

		// ★ A* 路径跟随：如果有缓存路径且目标未变，沿路径走
		BlockPos nextWaypoint = target;
		if (pers != null) {
			// 目标变了就清路径
			if (!target.equals(pers.pathGoal)) {
				pers.currentPath.clear();
				pers.pathGoal = null;
			}
			// V5.22: findPath 失败冷却——避免主线程每 tick 跑 A*
			//   原实现:目标不可达 → 路径恒为空 → 每 tick 都重算 → 每秒 20 次 A*
			//   现在:findPath 返回空就冷却 5 秒,期间直线朝 target 走(碰墙交给下面的避障)
			long now = System.currentTimeMillis();
			if (pers.currentPath.isEmpty() && now >= pers.pathfindCooldownUntil) {
				java.util.List<BlockPos> path = PathfindingNavigation.findPath(world, p.getBlockPos(), target);
				if (!path.isEmpty()) {
					pers.currentPath.addAll(path);
					pers.pathGoal = target;
				} else {
					// 找不到路径,冷却避免反复算
					pers.pathfindCooldownUntil = now + PATHFIND_FAIL_COOLDOWN_MS;
				}
			}
			// 消费已到达的路径点
			while (!pers.currentPath.isEmpty()) {
				BlockPos wp = pers.currentPath.peek();
				double wdx = wp.getX() + 0.5 - pos.x;
				double wdz = wp.getZ() + 0.5 - pos.z;
				if (wdx * wdx + wdz * wdz <= 2.25) pers.currentPath.poll();
				else break;
			}
			if (!pers.currentPath.isEmpty()) nextWaypoint = pers.currentPath.peek();
		}

		// 朝向下一个路径点
		double ndx = nextWaypoint.getX() + 0.5 - pos.x;
		double ndz = nextWaypoint.getZ() + 0.5 - pos.z;
		double ndist = Math.sqrt(ndx * ndx + ndz * ndz);
		if (ndist < 0.01) ndist = 0.01;

		// V5.2 Mouse Fingerprint: Fitts' Law 拟真视角曲线 (快速逼近 -> 减速微调)
		float targetYaw = (float) (MathHelper.atan2(ndz, ndx) * (180F / Math.PI)) - 90.0F;
		float targetPitch = (float) (MathHelper.atan2(-p.getY() + nextWaypoint.getY(), ndist) * (180F / Math.PI));
		
		float currentYaw = p.getYaw();
		float currentPitch = p.getPitch();
		
		float diff = MathHelper.wrapDegrees(targetYaw - currentYaw);
		float absDiff = Math.abs(diff);
		
		float lerpFactor = com.maohi.fakeplayer.ai.BehavioralDistributionValidator.getAlignedRotationLerp();
		
		// V5.7 P0 鼠标轨迹模拟：菲茨定律 (Fitts' Law) + 过冲 (Overshoot)
		if (absDiff > 15.0f) {
			// 阶段 1 & 2: 快速逼近与减速
			lerpFactor *= 2.5f; 
			// 10% 概率产生过冲 (Overshoot)
			if (ThreadLocalRandom.current().nextInt(100) < 10 && absDiff > 45.0f) {
				targetYaw += (diff > 0 ? 5.0f : -5.0f); // 故意转过头 5 度
			}
		} else if (absDiff < 2.0f) {
			// 阶段 3 & 4: 微调与确认
			lerpFactor *= 0.3f;
		}

		float newYaw = MathHelper.lerp(lerpFactor, currentYaw, targetYaw);
		float newPitch = MathHelper.lerp(lerpFactor, currentPitch, targetPitch);
		
		// 叠加高斯噪声模拟生理手抖 (Tremor)
		newYaw += (float)(ThreadLocalRandom.current().nextGaussian() * 0.05) + perlinLike(noisePhaseYaw, noiseTime, 1.2f);
		newPitch += (float)(ThreadLocalRandom.current().nextGaussian() * 0.05) + perlinLike(noisePhasePitch, noiseTime, 1.5f);
		
		p.setYaw(newYaw);
		p.setPitch(newPitch);

		// V5.2 Keyboard Fingerprint: 双击方向键冲刺误触发模拟
		if (p.isOnGround() && !p.isSprinting() && ThreadLocalRandom.current().nextInt(1000) == 0) {
			p.setSprinting(true); // 突然手滑冲刺一下
		}

		// 前方碰撞检测
		double nx = pos.x + ndx / ndist;
		double nz = pos.z + ndz / ndist;
		BlockPos nextPos = BlockPos.ofFloored(nx, pos.y, nz);

		if (PathfindingNavigation.isDangerAhead(world, nextPos)) {
			stopMovement(p);
			if (pers != null) pers.currentPath.clear();
			return true;
		}

		boolean isBlocked = !world.getBlockState(nextPos).getCollisionShape(world, nextPos).isEmpty()
			|| !world.getBlockState(nextPos.up()).getCollisionShape(world, nextPos.up()).isEmpty();

		if (isBlocked) {
			boolean canJump = world.getBlockState(nextPos.up(1)).getCollisionShape(world, nextPos.up(1)).isEmpty()
				&& world.getBlockState(nextPos.up(2)).getCollisionShape(world, nextPos.up(2)).isEmpty()
				&& world.getBlockState(p.getBlockPos().up(2)).getCollisionShape(world, p.getBlockPos().up(2)).isEmpty();
			if (canJump) {
				if (p.isOnGround()) {
					p.setSprinting(ndist > 4.0);
					setMovement(p, 1.0f, 0.0f);
					p.jump();
					p.addVelocity(ndx / ndist * 0.1, 0, ndz / ndist * 0.1);
				}
			} else {
				// 无法跳越：清路径，下次重新计算
				if (pers != null) pers.currentPath.clear();
				stopMovement(p);
				return false; // 不放弃任务，等下次 tick 重算路径
			}
		} else {
			p.setSprinting(ndist > 4.0);
			float lateralDrift = perlinLike(noisePhaseYaw * 1.2, noiseTime, 0.5f);
			if (ThreadLocalRandom.current().nextInt(150) == 0)
				lateralDrift += ThreadLocalRandom.current().nextFloat() * 0.8f - 0.4f;
			
			// V4.4 情绪修正：死后 5 分钟内速度降低 30% (跑尸沮丧模拟)
			float speedFactor = 1.0f;
			long serverTicks = p.getEntityWorld().getServer().getTicks();
			if (pers != null && serverTicks - pers.lastDeathTick < 6000) {
				speedFactor = 0.7f;
			}
			
			setMovement(p, (0.8f + ThreadLocalRandom.current().nextFloat() * 0.2f) * speedFactor, lateralDrift * speedFactor);
			p.travel(new Vec3d(p.sidewaysSpeed, 0, p.forwardSpeed));
		}

		return false;
	}
}
