package net.mcreator.mcanomalyarchives;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.sounds.SoundEvent;
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

import java.util.Map;
import java.util.HashMap;

@Mod("mcanomalyarchives")
public class McanomalyarchivesMod {
	public static final Logger LOGGER = LogManager.getLogger(McanomalyarchivesMod.class);
	public static final String MODID = "mcanomalyarchives";

	public McanomalyarchivesMod(IEventBus modEventBus) {
		// Start of user code block mod constructor
		// 世界规则先注册/迁移；客户端舒适度只控制震动与眨眼。
		// 这行故意不引用任何构造器参数：ModContainer 由 ComfortConfig 自己通过 ModList 取
		// （已从 FML 字节码确认：ModList 的静态字段在 constructMods 之前就赋值好了）。
		// 这样 MCreator 无论把构造器签名生成成什么样，这行都能编译 ——
		// 2026-09-12 就因为构造器被改回单参版本而炸过一次编译。
		net.mcreator.mcanomalyarchives.config.ServerGameplayConfig.register();
		net.mcreator.mcanomalyarchives.config.ComfortConfig.register();
		// End of user code block mod constructor
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
		// 客户端专用初始化：必须包在 dist 判断里。
		// 这些 handler 一旦在专用服务器上被类加载，就会顺着 net.minecraft.client.* 一路拉下去，
		// RuntimeDistCleaner 判定 invalid dist DEDICATED_SERVER，整个模组加载失败（2026-10-05 实测）。
		// 注意：加运行时 if 判断本身不够——类体里出现 new XxxScreen(...) 会在字节码校验阶段就触发加载。
		if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
			AddictionClientHandler.init();
		}
		GardenStructureListener.init();
		// StrangeFishingRodLootHandler 已通过 @EventBusSubscriber 自动注册
		SanityEventHandler.init();
		SanityRecoveryHandler.init();
		if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
			SanityClientHandler.init(); // 客户端专用，见上
		}
		PurpleDogPettingHandler.init();
		if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
			SanityMonitorHudOverlay.init(); // 客户端专用，见上
			DialogueBubbleRenderer.init(); // 客户端专用，见上
		}
		PurpleMonsterTriggerHandler.init();
		PurpleStalkerHandler.init();
		StangeCloudSpawnHandler.init();
		// 结构补实体仍由此入口处理；怪树玩法模块自动订阅事件。
		StrangeTreeHandler.init();
		CloudArmorSetBonusHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepLookHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepLuckyHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepCalamityHandler.init();
		net.mcreator.mcanomalyarchives.events.PinkSheepStructureSpawnHandler.init();
		if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
			net.mcreator.mcanomalyarchives.client.BlinkClientHandler.init(); // 客户端专用，见上
		}
		net.mcreator.mcanomalyarchives.codex.CodexScanner.init(); // 见闻录：周期扫描解锁
		net.mcreator.mcanomalyarchives.codex.CodexItemHandler.init(); // 见闻录：右键打开
		net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagHandler.init(); // 百变命名牌：右键实体 / 铁砧命名
		net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagTicker.init(); // 百变命名牌：行为覆盖层
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

	/** 生成区兼容入口；任务状态与生命周期全部归手写 runtime 模块。 */
	public static void queueServerWork(int delay, Runnable action) {
		net.mcreator.mcanomalyarchives.runtime.ServerTasks.schedule(delay, action);
	}
}
