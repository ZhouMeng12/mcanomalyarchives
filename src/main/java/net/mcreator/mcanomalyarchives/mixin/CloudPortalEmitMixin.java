package net.mcreator.mcanomalyarchives.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.mcreator.mcanomalyarchives.anomaly.cloud.SmallCloudEmitter;
import net.mcreator.mcanomalyarchives.block.CloudDemPortalBlock;

/**
 * 让云室传送门在随机刻里吐小伪云。
 *
 * <p><b>为什么是 mixin 而不是"注册一个子类"</b>：一开始的做法是写
 * {@code SmallCloudEmittingPortalBlock extends CloudDemPortalBlock}，
 * 再把 {@code init/McanomalyarchivesModBlocks.java} 的注册行换成它。
 * 那条注册行在 MCreator 生成区里，作者 2026-09-18 在 MCreator 里跑了一次客户端，
 * 生成区被重写、注册行退回 {@code CloudDemPortalBlock::new} ——
 * **不报错、不崩溃，只是门从此不再吐云**，正好就是这个 mixin 要根治的失效方式。
 * mixin 配置与 mixin 类都在手写区，MCreator 不碰。
 *
 * <p>注入点在 {@code randomTick} 的 HEAD，并且**不取消**调用 ——
 * 云室门自己的 {@code randomTick} 是空实现（有意去掉原版"门里刷僵尸猪灵"），
 * 所以这里只是在它前面插一句，行为不变。
 *
 * <p>随机刻本身只在有玩家的已加载区块里跑，所以"没人看的地方不演"是免费拿到的。
 */
@Mixin(CloudDemPortalBlock.class)
public abstract class CloudPortalEmitMixin {

	@Inject(method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V", at = @At("HEAD"))
	private void mcanomalyarchives$emitSmallCloud(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		SmallCloudEmitter.tryEmit(level, pos, state, random);
	}
}
