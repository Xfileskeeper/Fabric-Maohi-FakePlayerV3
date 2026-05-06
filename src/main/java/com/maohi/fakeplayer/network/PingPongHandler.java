package com.maohi.fakeplayer.network;

import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * PingPong 处理器
 * 专门负责处理与服务端的 KeepAlive (心跳) 交互。
 * 核心目标:让假人的 ping 时间序列在 ML/统计检测下与真实玩家不可区分。
 *
 * V5.22 修复(完整版):
 *   原实现:50~200ms 均匀分布——真实玩家 ping 是带自相关的右偏长尾分布,
 *          所有假人均匀采样且无时序相关,在 PCA/ACF 检测里非常显眼。
 *
 *   新实现 4 重拟真:
 *     1) per-player 基线: 每个连接首包时 roll 一次 baseline∈[55,150]ms,
 *        模拟物理距离差异(同服务器不同玩家 ping 中位数本就不同)。
 *     2) AR(1) 自相关: 相邻 KeepAlive 之间用 ρ=0.7 平滑,贴合真实网络
 *        相邻包延迟强相关的特征(真实 ACF lag-1 ≈ 0.6~0.8)。
 *     3) 右偏抖动: 用对数正态(而非高斯)给基线叠加 jitter,产出右偏长尾,
 *        匹配真实 ping 分布的偏度(skewness ≈ 1.5)。
 *     4) 重传尖峰: 1/80 概率注入 300~600ms 尖峰(模拟丢包/WiFi 干扰)。
 *
 *   另:调度池拒绝时静默丢弃,避免服务器关停时刷异常。
 */
public class PingPongHandler {

	/** 每玩家基线范围(物理距离差异) */
	private static final long BASELINE_MIN_MS = 55L;
	private static final long BASELINE_MAX_MS = 150L;

	/** AR(1) 平滑系数: 越大相邻包越相关(真实 lag-1 ACF ≈ 0.6~0.8) */
	private static final double AR1_RHO = 0.72;

	/** 对数正态 jitter 参数: ln(jitter)~N(LN_MU, LN_SIGMA),保证右偏 */
	private static final double LN_MU = 2.6;     // exp(2.6) ≈ 13.5ms 中位 jitter
	private static final double LN_SIGMA = 0.55; // 长尾扩散

	/** 全局 clamp 边界 */
	private static final long LATENCY_MIN_MS = 20L;
	private static final long LATENCY_MAX_MS = 400L;

	/** 偶发尖峰: 模拟 TCP 重传 / WiFi 干扰 */
	private static final int SPIKE_PROBABILITY_DENOMINATOR = 80;
	private static final long SPIKE_MIN_MS = 300L;
	private static final long SPIKE_MAX_MS = 600L;

	/**
	 * per-player 状态:基线 + 上次延迟(用于 AR(1))。
	 * 用 WeakHashMap+synchronized,handler 断连后自动 GC,无内存泄漏。
	 */
	private static final Map<ServerPlayNetworkHandler, PingState> STATE_MAP =
			Collections.synchronizedMap(new WeakHashMap<>());

	private static final class PingState {
		final long baselineMs;
		long lastDelayMs;
		PingState(long baseline) {
			this.baselineMs = baseline;
			this.lastDelayMs = baseline;
		}
	}

	/**
	 * 处理收到的 KeepAlive 包,异步返回响应。
	 * @param id      服务端发来的 KeepAlive ID
	 * @param handler 假人当前的网络处理器
	 * @param pool    用于调度的共享线程池
	 */
	public static void respondToKeepAlive(long id, ServerPlayNetworkHandler handler, ScheduledExecutorService pool) {
		if (handler == null || pool == null) return;

		long delay = sampleLatencyMs(handler);

		try {
			pool.schedule(() -> {
				try {
					handler.onKeepAlive(new KeepAliveC2SPacket(id));
				} catch (Throwable t) {
					// 静默处理:连接已断开
				}
			}, delay, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException ignored) {
			// 服务器关停时调度池已 shutdown,无需告警
		}
	}

	/**
	 * 采样下一次 KeepAlive 延迟。
	 * 时序模型: delay[t] = ρ·delay[t-1] + (1-ρ)·(baseline + lognormal_jitter)
	 *           1/80 概率覆盖为重传尖峰
	 */
	private static long sampleLatencyMs(ServerPlayNetworkHandler handler) {
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		PingState state = STATE_MAP.computeIfAbsent(handler, h ->
				new PingState(BASELINE_MIN_MS + rng.nextLong(BASELINE_MAX_MS - BASELINE_MIN_MS + 1)));

		// 1/80 概率: 网络抖动尖峰,直接覆盖并打断自相关链
		if (rng.nextInt(SPIKE_PROBABILITY_DENOMINATOR) == 0) {
			long spike = SPIKE_MIN_MS + rng.nextLong(SPIKE_MAX_MS - SPIKE_MIN_MS + 1);
			state.lastDelayMs = spike;
			return spike;
		}

		// 对数正态 jitter: 右偏长尾,匹配真实 ping 分布形状
		double lnSample = LN_MU + rng.nextGaussian() * LN_SIGMA;
		double jitter = Math.exp(lnSample);
		// 50% 概率给 jitter 加负号,让基线两侧都有抖动(否则只能向上偏)
		if (rng.nextBoolean()) jitter = -jitter * 0.4; // 向下抖动幅度更小,保留右偏

		double target = state.baselineMs + jitter;

		// AR(1) 平滑: 相邻包强相关
		double smoothed = AR1_RHO * state.lastDelayMs + (1.0 - AR1_RHO) * target;

		long clamped = (long) Math.max(LATENCY_MIN_MS, Math.min(LATENCY_MAX_MS, smoothed));
		state.lastDelayMs = clamped;
		return clamped;
	}
}
