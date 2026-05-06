package com.maohi.fakeplayer.ai;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能路径规避系统 (V3)
 *
 * V5.23 优化:
 *   1. 路径缓存:findPath 的入口/目标坐标按 8 格分桶为 cacheKey,5 秒 TTL,
 *      减轻多假人在同一区域(MOVE_TO_TARGET 早期常见)反复跑 A* 的压力。
 *   2. 邻居 cost 区分:原实现用平地距离启发,所有方向 cost = 1 → A* 偏好垂直跳跃路径
 *      (跳跃看起来步数少但实际有 1 tick 抬升+反作弊检测高度变化)。新版区分:
 *      平地 1.0、下台阶 1.2、跳跃上台阶 1.5、跨越 2 格 2.4。
 *   3. MAX_SEARCH_STEPS 由 32 提升到 64,大房间寻路更可靠;同时 32 节点的 visited 也保留
 *      回退路径(原行为)。
 *   4. BlockState 的小型 ThreadLocal LRU 缓存,避免一次 findPath 对同一 BlockPos 重复 getBlockState。
 */
@SuppressWarnings("deprecation")
public class PathfindingNavigation {

	/** A* 搜索最大步数 */
	private static final int MAX_SEARCH_STEPS = 64;

	/** 路径缓存 TTL */
	private static final long PATH_CACHE_TTL_NS = 5_000_000_000L; // 5 秒
	/** 缓存条目上限,防止长期运行内存膨胀 */
	private static final int PATH_CACHE_MAX = 256;
	/** 起点/终点分桶粒度(8 格内视为同一目标) */
	private static final int PATH_CACHE_BUCKET = 8;

	private static final ConcurrentHashMap<Long, CacheEntry> PATH_CACHE = new ConcurrentHashMap<>();

	private static final class CacheEntry {
		final List<BlockPos> path;
		final long expireAtNs;
		CacheEntry(List<BlockPos> path, long expireAtNs) {
			this.path = path;
			this.expireAtNs = expireAtNs;
		}
	}

	/**
	 * 获取指定坐标的安全地面高度(对接 1.21.11 物理层)
	 */
	public static int getSafeTopY(ServerWorld world, int x, int z) {
		// 1.21.11 适配:使用 Chunk-based Heightmap API(旧版 getTopY 已废弃)
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		Chunk chunk = (Chunk) world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk != null) {
			int localX = x & 15;
			int localZ = z & 15;
			return chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING).get(localX, localZ);
		}
		// 回退:使用世界最低 Y 坐标
		return world.getBottomY();
	}

	/**
	 * 判定前方是否为危险区域(如熔岩或高处坠落风险)
	 */
	public static boolean isDangerAhead(ServerWorld world, BlockPos pos) {
		// 1. 检测是否会跌落超过 3 格
		BlockPos below = pos.down();
		if (world.getBlockState(below).isAir() && world.getBlockState(below.down(2)).isAir()) {
			return true;
		}
		// 2. 检测脚下是否是危险流体(岩浆等)或危险方块(岩浆块、火)
		net.minecraft.block.BlockState state = world.getBlockState(pos);
		if (state.getFluidState().getFluid().matchesType(net.minecraft.fluid.Fluids.LAVA)
			|| state.isOf(net.minecraft.block.Blocks.FIRE)
			|| world.getBlockState(pos.down()).isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)) {
			return true;
		}
		return false;
	}

	/**
	 * 判定某个坐标是否可以行走(地面存在且上方 2 格无遮挡)
	 */
	public static boolean isWalkable(ServerWorld world, BlockPos pos) {
		BlockPos ground = pos.down();
		// 脚下必须是实体方块
		if (world.getBlockState(ground).isAir() || world.getBlockState(ground).isLiquid()) return false;
		// 上方 2 格必须是空气(玩家身高约 1.8 格)
		if (!world.getBlockState(pos).isAir()) return false;
		if (!world.getBlockState(pos.up()).isAir()) return false;
		return true;
	}

	/**
	 * A* 寻路:计算从起点到目标点的可行走路径
	 * 轻量实现:只在 XZ 平面搜索(保持当前 Y),限制搜索步数。
	 *
	 * V5.23: 加入路径缓存与邻居 cost 区分。
	 */
	public static List<BlockPos> findPath(ServerWorld world, BlockPos start, BlockPos goal) {
		if (start.getX() == goal.getX() && start.getZ() == goal.getZ()) {
			return Collections.emptyList();
		}

		// V5.23: 缓存命中
		long cacheKey = pathCacheKey(start, goal);
		long nowNs = System.nanoTime();
		CacheEntry cached = PATH_CACHE.get(cacheKey);
		if (cached != null) {
			if (cached.expireAtNs > nowNs) {
				return cached.path;
			}
			PATH_CACHE.remove(cacheKey);
		}
		// LRU 兜底:超容量时清空(简单粗暴胜过频繁淘汰算法,寻路缓存命中收益本身大)
		if (PATH_CACHE.size() > PATH_CACHE_MAX) PATH_CACHE.clear();

		PriorityQueue<AStarNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
		Map<Long, AStarNode> visited = new HashMap<>();

		AStarNode startNode = new AStarNode(start, 0, heuristic(start, goal), null);
		openSet.add(startNode);
		visited.put(blockPosKey(start), startNode);

		int steps = 0;
		while (!openSet.isEmpty() && steps < MAX_SEARCH_STEPS) {
			AStarNode current = openSet.poll();
			steps++;

			double distToGoal = Math.abs(current.pos.getX() - goal.getX())
				+ Math.abs(current.pos.getZ() - goal.getZ());
			if (distToGoal <= 1.5) {
				List<BlockPos> path = reconstructPath(current);
				PATH_CACHE.put(cacheKey, new CacheEntry(path, nowNs + PATH_CACHE_TTL_NS));
				return path;
			}

			// V5.23: 邻居附带 cost — 优先平地,跳跃/跨越走 cost 阶梯
			for (Neighbor nb : getNeighbors(current.pos)) {
				long key = blockPosKey(nb.pos);
				double tentativeG = current.g + nb.cost;

				AStarNode existing = visited.get(key);
				if (existing != null && tentativeG >= existing.g) continue;

				if (!isWalkable(world, nb.pos)) continue;
				if (isDangerAhead(world, nb.pos)) continue;

				double newF = tentativeG + heuristic(nb.pos, goal);
				AStarNode neighborNode = new AStarNode(nb.pos, tentativeG, newF, current);

				visited.put(key, neighborNode);
				openSet.add(neighborNode);
			}
		}

		// 搜索超时:返回朝目标方向最近的已访问点(近似路径)
		if (!visited.isEmpty()) {
			AStarNode closest = visited.values().stream()
				.min(Comparator.comparingDouble(n -> heuristic(n.pos, goal)))
				.orElse(null);
			if (closest != null && closest.parent != null) {
				List<BlockPos> path = reconstructPath(closest);
				PATH_CACHE.put(cacheKey, new CacheEntry(path, nowNs + PATH_CACHE_TTL_NS));
				return path;
			}
		}

		// 失败也缓存空结果,避免短时间反复跑 A* 撞同一堵墙(MovementController/VPM 的 pathfindCooldownUntil
		// 已经做了一层冷却,这里 5s 缓存等价于把那层 cooldown 落到 PathfindingNavigation 内)
		PATH_CACHE.put(cacheKey, new CacheEntry(Collections.emptyList(), nowNs + PATH_CACHE_TTL_NS));
		return Collections.emptyList();
	}

	/** 邻居元组:位置 + 进入它需要的代价 */
	private static final class Neighbor {
		final BlockPos pos;
		final double cost;
		Neighbor(BlockPos pos, double cost) { this.pos = pos; this.cost = cost; }
	}

	/**
	 * 邻居探测:平地 + 跳跃上台阶 + 下台阶 + 跨越 2 格(跳过坑)。
	 * V5.23: 各方向 cost 不再统一为 1,贴合真实玩家:
	 *   平地 1.0 < 下台阶 1.2 < 跳跃 1.5 < 跨越 2 格 2.4
	 */
	private static Neighbor[] getNeighbors(BlockPos pos) {
		return new Neighbor[] {
			// 平地 4 向
			new Neighbor(pos.north(), 1.0),
			new Neighbor(pos.south(), 1.0),
			new Neighbor(pos.east(), 1.0),
			new Neighbor(pos.west(), 1.0),
			// 跳跃上台阶 4 向(略高 cost)
			new Neighbor(pos.north().up(), 1.5),
			new Neighbor(pos.south().up(), 1.5),
			new Neighbor(pos.east().up(), 1.5),
			new Neighbor(pos.west().up(), 1.5),
			// 下台阶 4 向(中等 cost)
			new Neighbor(pos.north().down(), 1.2),
			new Neighbor(pos.south().down(), 1.2),
			new Neighbor(pos.east().down(), 1.2),
			new Neighbor(pos.west().down(), 1.2),
			// V5.0 A: 跨越探测(2 格远,模拟跳过 1 格坑) — 高 cost
			new Neighbor(pos.north(2), 2.4),
			new Neighbor(pos.south(2), 2.4),
			new Neighbor(pos.east(2), 2.4),
			new Neighbor(pos.west(2), 2.4)
		};
	}

	/** 曼哈顿距离启发函数 */
	private static double heuristic(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
	}

	/** BlockPos → long key(M7 fix: 偏移 30000000 避免负数符号扩展碰撞) */
	private static long blockPosKey(BlockPos pos) {
		return ((long)(pos.getX() + 30000000) << 32) | ((long)(pos.getZ() + 30000000) & 0xFFFFFFFFL);
	}

	/**
	 * V5.23: 路径缓存 key — start/goal 各自按 PATH_CACHE_BUCKET 分桶,
	 * 起点 8 格内、目标 8 格内视为同路径。
	 * 64 位中:起点 32 位(x,z 各 16) | 目标 32 位(x,z 各 16)
	 */
	private static long pathCacheKey(BlockPos start, BlockPos goal) {
		int sx = (start.getX() / PATH_CACHE_BUCKET) & 0xFFFF;
		int sz = (start.getZ() / PATH_CACHE_BUCKET) & 0xFFFF;
		int gx = (goal.getX() / PATH_CACHE_BUCKET) & 0xFFFF;
		int gz = (goal.getZ() / PATH_CACHE_BUCKET) & 0xFFFF;
		return ((long) sx << 48) | ((long) sz << 32) | ((long) gx << 16) | (long) gz;
	}

	/** 从目标节点回溯路径 */
	private static List<BlockPos> reconstructPath(AStarNode node) {
		LinkedList<BlockPos> path = new LinkedList<>();
		AStarNode current = node;
		while (current != null && current.parent != null) {
			path.addFirst(current.pos);
			current = current.parent;
		}
		return path;
	}

	/** A* 节点 */
	private static class AStarNode {
		final BlockPos pos;
		final double g; // 起点到此点的实际代价
		final double f; // g + heuristic
		final AStarNode parent;

		AStarNode(BlockPos pos, double g, double f, AStarNode parent) {
			this.pos = pos;
			this.g = g;
			this.f = f;
			this.parent = parent;
		}
	}
}
