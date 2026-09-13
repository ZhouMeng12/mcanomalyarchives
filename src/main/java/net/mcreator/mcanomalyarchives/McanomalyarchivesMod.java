package net.mcreator.mcanomalyarchives;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.TickTask;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.init.*;
import net.mcreator.mcanomalyarchives.events.*;
import net.mcreator.mcanomalyarchives.event.PurpleStalkerHandler;
import net.mcreator.mcanomalyarchives.event.PurpleDogPettingHandler;
import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.commands.SanityCommand;
import net.mcreator.mcanomalyarchives.commands.PurpleGuiCommand;
import net.mcreator.mcanomalyarchives.client.dialogue.DialogueBubbleRenderer;
import net.mcreator.mcanomalyarchives.client.SanityMonitorHudOverlay;
import net.mcreator.mcanomalyarchives.client.SanityClientHandler;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;

@Mod("mcanomalyarchives")
public class McanomalyarchivesMod {
	public static final Logger LOGGER = LogManager.getLogger(McanomalyarchivesMod.class);
	public static final String MODID = "mcanomalyarchives";

	public McanomalyarchivesMod(IEventBus modEventBus) {
		// Start of user code block mod constructor
		// 舒适度配置（震动强度 / 眨眼黑屏 / 陨石是否破坏地形）。
		// 这行故意不引用任何构造器参数：ModContainer 由 ComfortConfig 自己通过 ModList 取
		// （已从 FML 字节码确认：ModList 的静态字段在 constructMods 之前就赋值好了）。
		// 这样 MCreator 无论把构造器签名生成成什么样，这行都能编译 ——
		// 2026-09-12 就因为构造器被改回单参版本而炸过一次编译。
		net.mcreator.mcanomalyarchives.config.ComfortConfig.register();
		// End of user code block mod constructor
		NeoForge.EVENT_BUS.register(this);
		modEventBus.addListener(this::registerNetworking);
		McanomalyarchivesModSounds.REGISTRY.register(modEventBus);
		McanomalyarchivesModBlocks.REGISTRY.register(modEventBus);
		McanomalyarchivesModBlockEntities.REGISTRY.register(modEventBus);
		McanomalyarchivesModItems.REGISTRY.register(modEventBus);
		McanomalyarchivesModEntities.REGISTRY.register(modEventBus);
		McanomalyarchivesModTabs.REGISTRY.register(modEventBus);
		McanomalyarchivesModMobEffects.REGISTRY.register(modEventBus);
		McanomalyarchivesModFluids.REGISTRY.register(modEventBus);
		McanomalyarchivesModFluidTypes.REGISTRY.register(modEventBus);
		// Start of user code block mod init
		McanomalyarchivesModAttachments.REGISTRY.register(modEventBus);
		McanomalyarchivesModDataComponents.REGISTRY.register(modEventBus);
		// 注册 AnbulaEntity 的 ITEM_HANDLER capability（供 TACZ reload 使用）
		modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
			event.registerEntity(Capabilities.ItemHandler.ENTITY, McanomalyarchivesModEntities.ANBULA.get(), (entity, ctx) -> entity instanceof AnbulaEntity a ? a.getAmmoHandler() : null);
		});
		PURPLEHAND_SOUNDS.register(modEventBus);
		PlayerLookAtCornPoppyListener.init();
		CornPoppyAngryListener.init();
		PurpleGuiCommand.init();
		SanityCommand.init();
		PlayerJoinEventListener.init();
		PoppyEdgeEventListener.init();
		AddictionClientHandler.init();
		GardenStructureListener.init();
		// StrangeFishingRodLootHandler 已通过 @EventBusSubscriber 自动注册
		SanityEventHandler.init();
		SanityRecoveryHandler.init();
		SanityClientHandler.init();
		PurpleDogPettingHandler.init();
		SanityMonitorHudOverlay.init();
		DialogueBubbleRenderer.init();
		PurpleMonsterTriggerHandler.init();
		PurpleStalkerHandler.init();
		StangeCloudSpawnHandler.init();
		// StrangeTreeHandler 已停用：实体由结构直接放置
		StrangeTreeBurrowHandler.init();
		StrangeTreeHandler.init();
		StrangeTreeAmbushHandler.init();
		StrangeTreeRelocateHandler.init();
		CloudArmorSetBonusHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepLookHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepLuckyHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepCalamityHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepStructureSpawnHandler.init();
		net.mcreator.mcanomalyarchives.client.BlinkClientHandler.init();
		net.mcreator.mcanomalyarchives.codex.CodexScanner.init(); // 见闻录：周期扫描解锁
		net.mcreator.mcanomalyarchives.codex.CodexItemHandler.init(); // 见闻录：右键打开
		// End of user code block mod init
	}

	// Start of user code block mod methods
	public static final DeferredRegister<SoundEvent> PURPLEHAND_SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> PURPLEHAND_SHOOT = PURPLEHAND_SOUNDS.register("purplehand_shoot", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "purplehand_shoot")));
	// End of user code block mod methods
	private static boolean networkingRegistered = false;
	private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

	private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
	}

	public static <T extends CustomPacketPayload> void addNetworkMessage(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
		if (networkingRegistered)
			throw new IllegalStateException("Cannot register new network messages after networking has been registered");
		MESSAGES.put(id, new NetworkMessage<>(reader, handler));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void registerNetworking(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar(MODID);
		MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
		networkingRegistered = true;
	}

	private static final Queue<IntObjectPair<Runnable>> workToBeScheduled = new ConcurrentLinkedQueue<>();
	private static final PriorityQueue<TickTask> workQueue = new PriorityQueue<>(Comparator.comparingInt(TickTask::getTick));

	public static void queueServerWork(int delay, Runnable action) {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)
			workToBeScheduled.add(new IntObjectImmutablePair<>(delay, action));
	}

	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		int currentTick = event.getServer().getTickCount();
		IntObjectPair<Runnable> work;
		while ((work = workToBeScheduled.poll()) != null) {
			workQueue.add(new TickTask(currentTick + work.leftInt(), work.right()));
		}
		while (!workQueue.isEmpty() && currentTick >= workQueue.peek().getTick()) {
			workQueue.poll().run();
		}
	}
}