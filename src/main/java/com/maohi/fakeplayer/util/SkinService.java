package com.maohi.fakeplayer.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maohi.common.HttpUtils;
import com.maohi.common.JsonUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mojang 皮肤服务封装 (V3)
 *
 * V5.23 修复:失败分类冷却,不再所有失败都 5min。
 *   - 玩家名不存在 (404): 永久缓存 null,玩家名不会突然存在
 *   - 429 限流: 用服务器 Retry-After 或 5min(已在 V5.23 第一次完成)
 *   - 超时: 30s 后可重试(临时网络问题)
 *   - IO 异常: 1min 重试
 *   - HTTP 5xx 等: 5min 重试
 *   - properties 为空(罕见状态): 1 小时后重试
 */
public final class SkinService {

	private SkinService() {} // 工具类禁止实例化

	/** 皮肤属性记录,用于注入 GameProfile */
	public record SkinProperty(String name, String value, String signature) {}

	// === 缓存:同一名字不重复请求 ===
	private static final Map<String, SkinProperty> cache = new ConcurrentHashMap<>();
	private static final Map<String, Long> fetchTime = new ConcurrentHashMap<>();
	/** V5.23: 失败到下次允许重试的截止时间(ms),与 fetchTime 配合做差异化退避 */
	private static final Map<String, Long> retryAfterAbsMs = new ConcurrentHashMap<>();
	/** V5.23: 永久不存在的玩家名,直接拒绝(404 缓存) */
	private static final Map<String, Boolean> notFoundForever = new ConcurrentHashMap<>();

	private static final long CACHE_TTL_MS = 3600_000L; // 1 小时缓存

	// === 频率限制:最多每 10 秒 1 次请求 ===
	private static final AtomicLong lastRequestTime = new AtomicLong(0);
	private static final long MIN_INTERVAL_MS = 10_000L; // 10 秒间隔

	// === 全局 429 退避:被限流后冷却 5 分钟(默认),或服务器 Retry-After ===
	private static volatile long cooldownUntil = 0;
	private static final long COOLDOWN_MS = 300_000L; // 5 分钟

	// === V5.23 失败分类退避时长 ===
	private static final long BACKOFF_TIMEOUT_MS = 30_000L;       // 网络超时:短退避
	private static final long BACKOFF_IO_MS = 60_000L;            // IO 异常:中退避
	private static final long BACKOFF_HTTP_ERROR_MS = 300_000L;   // 5xx 等:长退避
	private static final long BACKOFF_EMPTY_PROPS_MS = 3600_000L; // 罕见:一小时

	/**
	 * 异步获取皮肤数据(Mojang API)
	 * V3.3: 缓存优先 + 频率限制 + 429 退避
	 * V5.23: 失败分类(NOT_FOUND 永久缓存 / TIMEOUT/IO 短退避 / HTTP_ERROR 长退避)
	 *
	 * @param name 玩家名
	 * @return 皮肤属性,失败返回 null
	 */
	public static SkinProperty fetchSkinProperties(String name) {
		// 0. V5.23: 永久不存在名 → 直接拒绝
		if (notFoundForever.containsKey(name)) return null;

		// 1. 缓存命中且未过期 → 直接返回
		SkinProperty cached = cache.get(name);
		if (cached != null) {
			Long ft = fetchTime.get(name);
			if (ft != null && System.currentTimeMillis() - ft < CACHE_TTL_MS) return cached;
			// 过期,清理
			cache.remove(name);
			fetchTime.remove(name);
		}
		// V5.23: 上次失败后未到重试时间 → 拒绝
		Long retryAt = retryAfterAbsMs.get(name);
		if (retryAt != null && System.currentTimeMillis() < retryAt) return null;

		// 2. 全局 429 退避期内 → 跳过
		if (System.currentTimeMillis() < cooldownUntil) return null;

		// 3. 频率限制:10 秒内最多 1 次
		long now = System.currentTimeMillis();
		long last = lastRequestTime.get();
		if (now - last < MIN_INTERVAL_MS) return null;
		if (!lastRequestTime.compareAndSet(last, now)) return null; // CAS 失败 = 别的线程抢先了

		// 4. 真正请求
		try {
			// Step 1: 获取 UUID
			String uuidJson = HttpUtils.fetchText("https://api.mojang.com/users/profiles/minecraft/" + name, 8000);
			if (uuidJson == null) {
				applyBackoffByReason(name, HttpUtils.getLastFailReason());
				return null;
			}
			String uuid = JsonUtils.extractJson(uuidJson, "id");
			if (uuid == null) {
				// JSON 结构异常,按 IO 处理
				applyBackoffByReason(name, HttpUtils.FailReason.IO);
				return null;
			}

			// Step 2: 获取皮肤属性
			String profileJson = HttpUtils.fetchText("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false", 8000);
			if (profileJson == null) {
				applyBackoffByReason(name, HttpUtils.getLastFailReason());
				return null;
			}

			JsonObject profileObj = JsonParser.parseString(profileJson).getAsJsonObject();
			if (profileObj.has("properties")) {
				JsonArray props = profileObj.getAsJsonArray("properties");
				if (props.size() > 0) {
					JsonObject prop = props.get(0).getAsJsonObject();
					String pName = prop.get("name").getAsString();
					String pValue = prop.get("value").getAsString();
					String pSig = prop.has("signature") ? prop.get("signature").getAsString() : null;
					SkinProperty result = new SkinProperty(pName, pValue, pSig);
					// 成功 → 写缓存,清除任何上次失败痕迹
					cache.put(name, result);
					fetchTime.put(name, System.currentTimeMillis());
					retryAfterAbsMs.remove(name);
					return result;
				}
			}
			// V5.23: properties 空数组属罕见状态,1h 退避避免反复打 API
			retryAfterAbsMs.put(name, System.currentTimeMillis() + BACKOFF_EMPTY_PROPS_MS);
			fetchTime.put(name, System.currentTimeMillis());
		} catch (Exception e) {
			// 429 走 fetchText 内部 → FailReason.RATE_LIMITED
			HttpUtils.FailReason reason = HttpUtils.getLastFailReason();
			if (reason == HttpUtils.FailReason.RATE_LIMITED
				|| (e.getMessage() != null && e.getMessage().contains("429"))) {
				long retryAfter = HttpUtils.getRetryAfterMs();
				long cooldown = retryAfter > 0 ? retryAfter : COOLDOWN_MS;
				cooldownUntil = System.currentTimeMillis() + cooldown;
				org.slf4j.LoggerFactory.getLogger("Server thread")
					.warn("Skin API rate limited, cooling {}s (server hint: {})",
						cooldown / 1000, retryAfter > 0 ? "yes" : "no");
			} else {
				org.slf4j.LoggerFactory.getLogger("Server thread")
					.debug("Skin fetch failed {}: {}", name, e.getMessage());
			}
			applyBackoffByReason(name, reason);
		}
		return null;
	}

	/** V5.23: 根据 HttpUtils 的失败原因决定该名字下次允许重试的时间 */
	private static void applyBackoffByReason(String name, HttpUtils.FailReason reason) {
		long now = System.currentTimeMillis();
		fetchTime.put(name, now);
		switch (reason) {
			case NOT_FOUND -> {
				// 永久缓存,玩家名不存在不会突然存在
				notFoundForever.put(name, Boolean.TRUE);
				retryAfterAbsMs.remove(name);
			}
			case TIMEOUT -> retryAfterAbsMs.put(name, now + BACKOFF_TIMEOUT_MS);
			case IO -> retryAfterAbsMs.put(name, now + BACKOFF_IO_MS);
			case HTTP_ERROR -> retryAfterAbsMs.put(name, now + BACKOFF_HTTP_ERROR_MS);
			case RATE_LIMITED -> {
				// 全局 cooldownUntil 已经在 catch 里设置过了,per-name 也跟着
				retryAfterAbsMs.put(name, cooldownUntil);
			}
			default -> retryAfterAbsMs.put(name, now + BACKOFF_IO_MS);
		}
	}

	/** 清理过期缓存(可由外部定时调用) */
	public static void evictExpired() {
		long now = System.currentTimeMillis();
		fetchTime.entrySet().removeIf(e -> {
			if (now - e.getValue() > CACHE_TTL_MS) {
				cache.remove(e.getKey());
				return true;
			}
			return false;
		});
		// V5.23: retryAfterAbsMs 已过期的清掉(避免 Map 长期膨胀)
		retryAfterAbsMs.entrySet().removeIf(e -> e.getValue() < now);
		// notFoundForever 不清,玩家名永久无效
	}
}
