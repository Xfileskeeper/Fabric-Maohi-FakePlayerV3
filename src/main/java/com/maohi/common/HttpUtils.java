package com.maohi.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 请求工具类 (V3)
 *
 * V5.23 修复:
 *   1. 原实现 conn.disconnect() 在 BufferedReader 的 finally 里——若在 openConnection
 *      之后、getInputStream 之前抛异常,finally 不执行 → 连接泄漏。改为外层 try/finally
 *      + null 判空,任何路径都保证 disconnect。
 *   2. 429 Too Many Requests 服务器会返回 Retry-After header,原实现直接丢弃,调用方
 *      只能用固定 5min 冷却。新增 getRetryAfterMs() ThreadLocal,读到的值供调用方做
 *      智能退避(若服务端没给则返回 -1)。
 *   3. 新增 4MB 响应体上限,防止恶意/异常服务器返回巨大 body 撑爆 server thread。
 */
public final class HttpUtils {

	private HttpUtils() {} // 工具类禁止实例化

	/** V5.23: 单次响应体上限,Mojang 皮肤 JSON 通常 < 5KB,4MB 足够覆盖任何合理场景 */
	private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

	/**
	 * V5.23: 当上次 fetchText 因 429 失败时,服务器返回的 Retry-After 毫秒数。
	 * -1 表示服务端没给或上次成功。ThreadLocal 隔离,SkinService 等调用方可读。
	 */
	private static final ThreadLocal<Long> LAST_RETRY_AFTER_MS = ThreadLocal.withInitial(() -> -1L);

	/**
	 * V5.23: 上次失败原因。SkinService 等调用方读这个做差异化退避:
	 *   NOT_FOUND → 玩家名不存在,可永久缓存 null
	 *   RATE_LIMITED → 走 LAST_RETRY_AFTER_MS
	 *   TIMEOUT → 短退避(可立刻重试)
   *   IO → 中等退避
	 *   HTTP_ERROR → 长退避
	 *   OK → 上次成功
	 */
	public enum FailReason { OK, NOT_FOUND, RATE_LIMITED, TIMEOUT, IO, HTTP_ERROR }
	private static final ThreadLocal<FailReason> LAST_FAIL_REASON = ThreadLocal.withInitial(() -> FailReason.OK);

	/** 调用方在 fetchText 之后立刻调用,获取本线程上次 429 的 Retry-After(ms),无则 -1。 */
	public static long getRetryAfterMs() {
		return LAST_RETRY_AFTER_MS.get();
	}

	/** V5.23: 调用方在 fetchText 之后立刻调用,获取本线程上次失败原因。 */
	public static FailReason getLastFailReason() {
		return LAST_FAIL_REASON.get();
	}

	/**
	 * 通用 HTTP GET 文本获取
	 * 原 Maohi.fetchText() 逻辑完整迁移
	 *
	 * @param urlStr  请求 URL
	 * @param timeout 连接/读取超时(毫秒)
	 * @return 响应文本,失败或 404/204 返回 null
	 */
	public static String fetchText(String urlStr, int timeout) {
		LAST_RETRY_AFTER_MS.set(-1L); // 重置上次状态
		LAST_FAIL_REASON.set(FailReason.OK);
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) new java.net.URI(urlStr).toURL().openConnection();
			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(timeout);
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");

			int responseCode = conn.getResponseCode();
			// 404 (Not Found) 或 204 (No Content) 说明资源不存在,静默返回
			if (responseCode == 404 || responseCode == 204) {
				LAST_FAIL_REASON.set(FailReason.NOT_FOUND);
				return null;
			}
			// 429 (Too Many Requests) → 解析 Retry-After 并抛特殊异常让调用方退避
			if (responseCode == 429) {
				LAST_RETRY_AFTER_MS.set(parseRetryAfterMs(conn));
				LAST_FAIL_REASON.set(FailReason.RATE_LIMITED);
				throw new IOException("HTTP 429 Too Many Requests");
			}
			// 其他非 200 响应码视为异常
			if (responseCode != 200) {
				LAST_FAIL_REASON.set(FailReason.HTTP_ERROR);
				throw new IOException("HTTP code: " + responseCode);
			}

			// V5.23: 用 InputStream 读取原始字节,带 4MB 上限保护
			try (InputStream in = conn.getInputStream();
			     BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				StringBuilder sb = new StringBuilder();
				char[] buf = new char[8192];
				int totalChars = 0;
				int n;
				while ((n = br.read(buf)) > 0) {
					totalChars += n;
					if (totalChars > MAX_BODY_BYTES) {
						throw new IOException("Response body exceeds " + MAX_BODY_BYTES + " bytes");
					}
					sb.append(buf, 0, n);
				}
				// m7 fix: 保留多行格式,但去掉末尾单个换行(原行为)
				if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
					sb.setLength(sb.length() - 1);
				}
				return sb.toString();
			}
		} catch (java.net.SocketTimeoutException ste) {
			// 超时:已在 LAST_FAIL_REASON 是 OK 时覆盖为 TIMEOUT(429 路径已先 set 不会丢)
			if (LAST_FAIL_REASON.get() == FailReason.OK) LAST_FAIL_REASON.set(FailReason.TIMEOUT);
			org.slf4j.LoggerFactory.getLogger("Server thread")
				.debug("HTTP timeout (" + urlStr + "): " + ste.getMessage());
			return null;
		} catch (Exception e) {
			// 其他异常:OK 状态(还没 set 过)说明是 IO 类异常
			if (LAST_FAIL_REASON.get() == FailReason.OK) LAST_FAIL_REASON.set(FailReason.IO);
			// 仅在调试模式或严重网络故障时打印,避免刷屏
			org.slf4j.LoggerFactory.getLogger("Server thread")
				.debug("HTTP fetch failed (" + urlStr + "): " + e.getMessage());
			return null;
		} finally {
			// V5.23 修复:无论 openConnection 之后哪一步抛异常,都保证连接释放
			if (conn != null) {
				try { conn.disconnect(); } catch (Throwable ignored) {}
			}
		}
	}

	/**
	 * V5.23: 解析 429 响应的 Retry-After header。
	 * 标准支持两种格式:秒数(整数)或 HTTP-date。这里只解析常见的整数秒。
	 * @return 毫秒数,无法解析返回 -1
	 */
	private static long parseRetryAfterMs(HttpURLConnection conn) {
		String h = conn.getHeaderField("Retry-After");
		if (h == null || h.isEmpty()) return -1L;
		try {
			long seconds = Long.parseLong(h.trim());
			if (seconds < 0 || seconds > 86400) return -1L; // 异常值兜底:最多 1 天
			return seconds * 1000L;
		} catch (NumberFormatException nfe) {
			// HTTP-date 格式不解析,让调用方走默认退避
			return -1L;
		}
	}
}
