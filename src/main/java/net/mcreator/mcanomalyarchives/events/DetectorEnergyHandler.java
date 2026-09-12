package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModDataComponents;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

/**
 * 检测仪电量改为服务端扣减（每 50 tick 扣 1 FE）：
 * 重登不再回满，多人下无法作弊，数据组件自动同步到客户端 HUD。
 */
@EventBusSubscriber
public class DetectorEnergyHandler {

	private static Class<?> curiosApiClass;
	private static boolean curiosChecked = false;
	private static MethodHandle getCuriosInventoryHandle;
	private static MethodHandle findFirstCurioHandle;

	static {
		try {
			curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
		} catch (ClassNotFoundException ignored) {
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 50 == 0) {
			ItemStack detector = findDetector(player);
			if (detector == null) return;
			Integer energy = detector.get(McanomalyarchivesModDataComponents.ENERGY);
			if (energy == null || energy <= 0) return;
			detector.set(McanomalyarchivesModDataComponents.ENERGY, Math.max(0, energy - McanomalyarchivesModDataComponents.ENERGY_CONSUME_PER_TICK));
		}
	}

	/** 背包 + 盔甲栏 + 副手 + Curios 栏里找检测仪 */
	private static ItemStack findDetector(ServerPlayer player) {
		ItemStack curios = findInCurios(player);
		if (curios != null) return curios;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() == McanomalyarchivesModItems.DETECTER.get()) return stack;
		}
		for (ItemStack stack : player.getInventory().armor) {
			if (stack.getItem() == McanomalyarchivesModItems.DETECTER.get()) return stack;
		}
		for (ItemStack stack : player.getInventory().offhand) {
			if (stack.getItem() == McanomalyarchivesModItems.DETECTER.get()) return stack;
		}
		return null;
	}

	/** Curios 栏查找（与服务端兼容的反射，失败安全回退） */
	private static ItemStack findInCurios(ServerPlayer player) {
		if (curiosApiClass == null) return null;
		if (!curiosChecked) {
			curiosChecked = true;
		}
		try {
			if (getCuriosInventoryHandle == null) {
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				getCuriosInventoryHandle = lookup.findStatic(curiosApiClass, "getCuriosInventory",
						MethodType.methodType(Object.class, Player.class));
			}
			Object inventory = getCuriosInventoryHandle.invoke(player);
			if (inventory == null) return null;
			if (findFirstCurioHandle == null) {
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				findFirstCurioHandle = lookup.findVirtual(inventory.getClass(), "findFirstCurio",
						MethodType.methodType(Optional.class, Item.class));
			}
			@SuppressWarnings("unchecked")
			Optional<Object> result = (Optional<Object>) findFirstCurioHandle.invoke(inventory, McanomalyarchivesModItems.DETECTER.get());
			if (result != null && result.isPresent()) {
				Object slotResult = result.get();
				if (slotResult instanceof ItemStack stack) return stack;
				try {
					MethodHandles.Lookup lookup = MethodHandles.publicLookup();
					MethodHandle stackHandle = lookup.findVirtual(slotResult.getClass(), "stack",
							MethodType.methodType(ItemStack.class));
					return (ItemStack) stackHandle.invoke(slotResult);
				} catch (Exception ignored) {
				}
			}
		} catch (Throwable ignored) {
			getCuriosInventoryHandle = null;
			findFirstCurioHandle = null;
		}
		return null;
	}
}
