package com.maohi.fakeplayer.ai.phase;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Phase 任务上下文:封装 5 个 phase 共用的查找回调。
 * 不是所有 phase 都用所有字段(EnderDragon 只用 findHunt 等),冗余字段会被忽略。
 *
 * V5.20:从 VirtualPlayerManager 内联 lambda 提取
 */
public final class PhaseContext {
	/** 查找最近的矿石方块(IRON_AGE / DIAMOND_AGE / NETHER 用) */
	public final BiFunction<ServerWorld, BlockPos, BlockPos> findOre;

	/** 查找最近的木头方块(STONE_AGE / IRON_AGE / DIAMOND_AGE 用) */
	public final BiFunction<ServerWorld, BlockPos, BlockPos> findLog;

	/** 查找一个可猎杀目标(IRON_AGE / DIAMOND_AGE / NETHER / ENDGAME 用) */
	public final Supplier<HostileEntity> findHunt;

	public PhaseContext(BiFunction<ServerWorld, BlockPos, BlockPos> findOre,
	                    BiFunction<ServerWorld, BlockPos, BlockPos> findLog,
	                    Supplier<HostileEntity> findHunt) {
		this.findOre = findOre;
		this.findLog = findLog;
		this.findHunt = findHunt;
	}
}
