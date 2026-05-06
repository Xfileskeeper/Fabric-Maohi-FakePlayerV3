package com.maohi.fakeplayer.tick;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * findNearestBlock 缓存(V5.20:从 VirtualPlayerManager 提取)
 *
 * 把 8x8x8 区块网格内的查找结果缓存 30 秒,叠加 MSPT 自适应半径(Lag Guard)。
 * 假人挖完一个方块时,通过 invalidate() 清掉对应位置缓存,避免回头再挖空气。
 *
 * 线程安全:由 ConcurrentHashMap 保证。
 */
public final class BlockScanCache {

	private static final long CACHE_TTL_MS = 30_000L;

	// key = "x>>3,y>>3,z>>3,type"; value = [BlockPos, expireTime]
	private final Map<String, Object[]> cache = new ConcurrentHashMap<>();

	/**
	 * 查找最近的方块。
	 * MSPT 自适应:
	 *   ≤35  → 半径 20(流畅)
	 *   ≤50  → 半径 12(轻卡)
	 *   >50  → 半径 8(卡顿)
	 *
	 * V5.22 性能加固:
	 *   - 扫描顺序改为"同心壳扩展"(切比雪夫距离),贴脸方块 O(1) 命中;
	 *     取代原 cube-scan 冷启动最坏 10 万次 getBlockState
	 *   - 矿石 Y 向深度从 60 压到 20(假人当前 Y 已是挖矿层,再下探 20 足够)
	 *   - 使用 BlockPos.Mutable 避免每格 new 一个 BlockPos
	 */
	public BlockPos findNearestBlock(MinecraftServer server, ServerWorld world, BlockPos pos, int radius, String type) {
		String cacheKey = key(pos, type);
		Object[] cached = cache.get(cacheKey);
		if (cached != null && System.currentTimeMillis() < (long) cached[1]) return (BlockPos) cached[0];

		double mspt = server.getAverageTickTime();
		int maxRadius;
		if (mspt <= 35) maxRadius = 20;
		else if (mspt <= 50) maxRadius = 12;
		else maxRadius = 8;
		if (radius > maxRadius) radius = maxRadius;

		boolean isOre = type.contains("ore");
		int yMin = isOre ? -20 : -2;
		int yMax = 2;

		BlockPos result = scanShells(world, pos, radius, yMin, yMax, type);
		cache.put(cacheKey, new Object[]{result, System.currentTimeMillis() + CACHE_TTL_MS});
		return result;
	}

	/**
	 * 从中心向外扩散的壳层扫描——切比雪夫距离 d 的外壳扫完再扩到 d+1,
	 * 返回真正"最近"的匹配方块,贴脸命中时 O(1)。
	 */
	private static BlockPos scanShells(ServerWorld world, BlockPos pos, int radius, int yMin, int yMax, String type) {
		int maxD = Math.max(radius, Math.max(Math.abs(yMin), Math.abs(yMax)));
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int d = 0; d <= maxD; d++) {
			int dxMin = -Math.min(d, radius);
			int dxMax = Math.min(d, radius);
			int dyMin = -Math.min(d, -yMin);
			int dyMax = Math.min(d, yMax);
			int dzMin = -Math.min(d, radius);
			int dzMax = Math.min(d, radius);
			for (int dx = dxMin; dx <= dxMax; dx++) {
				for (int dy = dyMin; dy <= dyMax; dy++) {
					for (int dz = dzMin; dz <= dzMax; dz++) {
						// 只扫当前壳层的外皮——内部在上次迭代已扫过
						if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != d) continue;
						m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
						if (net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(m).getBlock()).getPath().contains(type)) {
							return m.toImmutable();
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * 失效指定位置 + 类型的缓存(假人挖完该方块后调用)
	 */
	public void invalidate(BlockPos pos, String type) {
		cache.remove(key(pos, type));
	}

	private static String key(BlockPos pos, String type) {
		return (pos.getX() >> 3) + "," + (pos.getY() >> 3) + "," + (pos.getZ() >> 3) + "," + type;
	}
}
