package net.mcreator.mcanomalyarchives.anomaly.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.mcreator.mcanomalyarchives.block.CloudDemPortalBlock;

/**
 * 会吐小伪云的云室传送门。
 *
 * <p>它只是 {@link CloudDemPortalBlock} 的一个子类，唯一的不同是**把随机刻用起来**：
 * 原版的门在随机刻里会刷僵尸猪灵，MCreator 给云室门生成的 {@code randomTick} 是空实现（有意去掉），
 * 这里在同一个方法里改成"掷骰子吐一只小伪云"——所以**照旧不调 {@code super}**。
 *
 * <p>为什么用随机刻而不是自己起一个 tick 循环：随机刻只在**有玩家的已加载区块**里跑（原版规则），
 * "没人看的地方不演"是免费拿到的，而且不需要扫描方块、不需要维护门户位置表。
 *
 * <p><b>注意</b>：这个类在注册表里替代了原来的 {@code CloudDemPortalBlock::new}
 * （见 {@code init/McanomalyarchivesModBlocks.java} 的注册行）。那一行在 MCreator 生成区里，
 * 万一被重新生成回退成 {@code CloudDemPortalBlock::new}，**不会报错、只会悄悄地不再吐云**，
 * 所以守卫里有 {@code small-cloud-portal-registration} 这条规则盯着它。
 */
public class SmallCloudEmittingPortalBlock extends CloudDemPortalBlock {

	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// 不调 super：保持"门里不刷僵尸猪灵"的现状，只做吐云这一件事
		SmallCloudEmitter.tryEmit(level, pos, state, random);
	}
}
