package net.mcreator.mcanomalyarchives.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

/**
 * Detects if a player is wearing the sanity monitor, supporting both
 * vanilla head slot and optional Curios head slot.
 * Also provides energy consumption and item stack retrieval for HUD.
 */
public class SanityMonitorDetector {

    private static final Class<?> curiosApiClass;
    private static boolean curiosChecked = false;
    private static boolean curiosAvailable = false;

    // 缓存的 Curios MethodHandle 引用，避免每帧反射
    private static MethodHandle getCuriosInventoryHandle;
    private static MethodHandle findFirstCurioHandle;

    // Curios 查找结果缓存 20 tick（1 秒）
    private static Player lastCuriosPlayer;
    private static ItemStack lastCuriosResult;
    private static int lastCuriosTick = Integer.MIN_VALUE;

    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        } catch (ClassNotFoundException ignored) {
        }
        curiosApiClass = clazz;
    }

    public static boolean isCuriosAvailable() {
        if (!curiosChecked) {
            curiosAvailable = curiosApiClass != null;
            curiosChecked = true;
        }
        return curiosAvailable;
    }

    /**
     * Check if the player has the sanity monitor equipped.
     */
    public static boolean isWearingSanityMonitor(Player player) {
        return getDetectorItemStack(player) != null;
    }

    /**
     * Get the detector ItemStack the player is wearing, or null if not equipped.
     */
    public static ItemStack getDetectorItemStack(Player player) {
        // 原版盔甲栏每帧直接查（廉价，保证 HUD 电量显示实时）
        for (int i = 36; i <= 39; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == McanomalyarchivesModItems.DETECTER.get()) {
                return stack;
            }
        }
        // Curios 查询结果缓存 20 tick，避免每帧反射
        if (player == lastCuriosPlayer && Math.abs(player.tickCount - lastCuriosTick) < 20) {
            return lastCuriosResult;
        }
        ItemStack curiosResult = findInCurios(player);
        lastCuriosPlayer = player;
        lastCuriosResult = curiosResult;
        lastCuriosTick = player.tickCount;
        return curiosResult;
    }

    /** Curios 槽位查找（结果由外层缓存） */
    private static ItemStack findInCurios(Player player) {
        // Check Curios head slot
        if (isCuriosAvailable()) {
            try {
                // 缓存 MethodHandle，避免每帧反射
                if (getCuriosInventoryHandle == null) {
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                    getCuriosInventoryHandle = lookup.findStatic(curiosApiClass, "getCuriosInventory",
                        MethodType.methodType(Object.class, Player.class));
                }
                Object inventory = getCuriosInventoryHandle.invoke(player);
                if (inventory != null) {
                    if (findFirstCurioHandle == null) {
                        Class<?> inventoryClass = inventory.getClass();
                        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                        findFirstCurioHandle = lookup.findVirtual(inventoryClass, "findFirstCurio",
                            MethodType.methodType(Optional.class, Item.class));
                    }
                    @SuppressWarnings("unchecked")
                    Optional<Object> result = (Optional<Object>) findFirstCurioHandle.invoke(inventory, McanomalyarchivesModItems.DETECTER.get());
                    if (result != null && result.isPresent()) {
                        Object slotResult = result.get();
                        if (slotResult instanceof ItemStack stack) {
                            return stack;
                        }
                        // Try to get stack via slotResult
                        try {
                            Class<?> slotClass = slotResult.getClass();
                            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                            MethodHandle stackHandle = lookup.findVirtual(slotClass, "stack",
                                MethodType.methodType(ItemStack.class));
                            return (ItemStack) stackHandle.invoke(slotResult);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 反射失败时重置缓存
                getCuriosInventoryHandle = null;
                findFirstCurioHandle = null;
            }
        }
        return null;
    }
}
