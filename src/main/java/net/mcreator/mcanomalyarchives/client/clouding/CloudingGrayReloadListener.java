package net.mcreator.mcanomalyarchives.client.clouding;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** F3+T 等资源重载时主动清空灰白贴图缓存（与"缺失重建"兜底并存） */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CloudingGrayReloadListener {

	@SubscribeEvent
	public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(new PreparableReloadListener() {
			@Override
			public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
					ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
					Executor backgroundExecutor, Executor gameExecutor) {
				return CompletableFuture.runAsync(CloudingGrayTextures::clearCache, backgroundExecutor)
						.thenCompose(barrier::wait);
			}
		});
	}
}
