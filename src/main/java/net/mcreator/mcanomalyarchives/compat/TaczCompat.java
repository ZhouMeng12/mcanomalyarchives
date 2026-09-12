package net.mcreator.mcanomalyarchives.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * TACZ (Timeless and Classics Guns: Zero) 软依赖兼容层。
 * 使用反射加载 TACZ API，零编译依赖。
 * 参考 {@code SanityMonitorDetector.java} 的 Class.forName() + 缓存模式。
 * <p>
 * IGunOperator（Mixin 注入所有 LivingEntity）：
 *   shoot(Supplier&lt;Float&gt;, Supplier&lt;Float&gt;) → ShootResult
 *   reload()
 * IGun 静态方法：
 *   getIGunOrNull(ItemStack) → IGun | null
 *   getCurrentAmmoCount(ItemStack) → int
 *   getGunId(ItemStack) → ResourceLocation
 * GunItemDataAccessor（IGun 子接口）：
 *   setCurrentAmmoCount(ItemStack, int) → void
 */
public final class TaczCompat {

    private static boolean checked;
    private static boolean available;

    // IGun 静态方法缓存
    private static Method getIGunOrNullMethod;
    private static Method getCurrentAmmoCountMethod;
    private static Method getGunIdMethod;

    // IGunOperator 方法（通过 entity.getClass() 运行时查找）
    // 不缓存 Method 实例，因为不同 Entity 子类可能由不同 Mixin 注入
    // 实际运行时 Mixin 统一注入到 LivingEntity，所以缓存是安全的
    private static Method shootMethod;

    static {
        try {
            Class.forName("com.tacz.guns.api.item.IGun");
            available = true;
        } catch (ClassNotFoundException ignored) {
            available = false;
        }
        checked = true;
    }

    private TaczCompat() {}

    // ==================== 公开查询 ====================

    public static boolean isAvailable() {
        if (!checked) {
            try {
                Class.forName("com.tacz.guns.api.item.IGun");
                available = true;
            } catch (ClassNotFoundException ignored) {
                available = false;
            }
            checked = true;
        }
        return available;
    }

    // ==================== GunStack 工厂 ====================

    /** TACZ 通用枪械物品注册名 */
    private static final String GUN_ITEM_ID = "tacz:modern_kinetic_gun";

    /**
     * 创建一把指定的 TACZ 枪械 ItemStack（含正确的 NBT：GunId、GunFireMode）。
     * TACZ 枪械物品统一为 tacz:modern_kinetic_gun，枪型由 NBT GunId 区分。
     *
     * @param gunId 枪型 ID，如 "tacz:ak47"
     * @return 枪械 ItemStack，若 TACZ 不可用则返回 EMPTY
     */
    public static ItemStack createGunStack(String gunId) {
        if (!isAvailable()) return ItemStack.EMPTY;
        net.minecraft.world.item.Item gunItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(GUN_ITEM_ID));
        if (gunItem == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(gunItem);
        // 通过反射调用 IGun / GunItemDataAccessor API 设置 GunId + FireMode
        try {
            if (getIGunOrNullMethod == null) {
                Class<?> igunClass = Class.forName("com.tacz.guns.api.item.IGun");
                getIGunOrNullMethod = igunClass.getMethod("getIGunOrNull", ItemStack.class);
            }
            Object igun = getIGunOrNullMethod.invoke(null, stack);
            if (igun != null) {
                // GunItemDataAccessor.setGunId(ItemStack, ResourceLocation)
                Method setGunId = igun.getClass().getMethod("setGunId", ItemStack.class, ResourceLocation.class);
                setGunId.invoke(igun, stack, ResourceLocation.parse(gunId));
                // setFireMode(ItemStack, FireMode) — FireMode 是枚举，需反射获取 AUTO 值
                Class<?> fireModeClass = Class.forName("com.tacz.guns.api.item.gun.FireMode");
                Method setFireMode = igun.getClass().getMethod("setFireMode", ItemStack.class, fireModeClass);
                Object autoMode = fireModeClass.getMethod("valueOf", String.class).invoke(null, "AUTO");
                setFireMode.invoke(igun, stack, autoMode);
            }
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    // ==================== IGun 物品检测 ====================

    /**
     * 判断 ItemStack 是否为 TACZ 枪械。
     */
    public static boolean isGun(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return false;
        try {
            if (getIGunOrNullMethod == null) {
                Class<?> igunClass = Class.forName("com.tacz.guns.api.item.IGun");
                getIGunOrNullMethod = igunClass.getMethod("getIGunOrNull", ItemStack.class);
            }
            Object result = getIGunOrNullMethod.invoke(null, stack);
            return result != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 获取枪械当前弹药量。
     */
    public static int getAmmoCount(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return 0;
        try {
            if (getCurrentAmmoCountMethod == null) {
                Class<?> igunClass = Class.forName("com.tacz.guns.api.item.IGun");
                getCurrentAmmoCountMethod = igunClass.getMethod("getCurrentAmmoCount", ItemStack.class);
            }
            Object result = getCurrentAmmoCountMethod.invoke(null, stack);
            return result instanceof Integer i ? i : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * 获取枪械 ID（ResourceLocation 字符串形式，如 "tacz:ak47"）。
     */
    public static String getGunId(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return "";
        try {
            if (getGunIdMethod == null) {
                Class<?> igunClass = Class.forName("com.tacz.guns.api.item.IGun");
                getGunIdMethod = igunClass.getMethod("getGunId", ItemStack.class);
            }
            Object result = getGunIdMethod.invoke(null, stack);
            return result != null ? result.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 判断是否为特定枪型（如 "tacz:ak47"）。
     */
    public static boolean isSpecificGun(ItemStack stack, String gunId) {
        return gunId.equals(getGunId(stack));
    }

    // ==================== 弹药管理 ====================

    /**
     * 直接设置枪械当前弹药数（绕过换弹流程）。
     * 通过 IGun → GunItemDataAccessor 反射调用。
     */
    public static void setAmmo(ItemStack stack, int ammo) {
        if (!isAvailable() || stack.isEmpty()) return;
        try {
            // 与其他查询方法一致：先懒加载方法缓存，避免 getIGunOrNullMethod 为 null 时 NPE
            if (getIGunOrNullMethod == null) {
                Class<?> igunClass = Class.forName("com.tacz.guns.api.item.IGun");
                getIGunOrNullMethod = igunClass.getMethod("getIGunOrNull", ItemStack.class);
            }
            Object igun = getIGunOrNullMethod.invoke(null, stack);
            if (igun == null) return;
            // GunItemDataAccessor.setCurrentAmmoCount(ItemStack, int)
            Method setAmmoMethod = igun.getClass().getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
            setAmmoMethod.invoke(igun, stack, ammo);
        } catch (Exception ignored) {
        }
    }

    /**
     * 获取枪械的最大弹容量。
     * 反射链：TimelessAPI.getCommonGunIndex(gunId) → GunData.getAmmoAmount()
     */
    public static int getMaxAmmo(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return -1;
        try {
            // TimelessAPI.getCommonGunIndex(ResourceLocation)
            Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            Method getIndex = apiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
            Object optional = getIndex.invoke(null, ResourceLocation.parse(getGunId(stack)));
            if (optional == null) return -1;
            // Optional.orElse(null)
            Method orElse = optional.getClass().getMethod("orElse", Object.class);
            Object index = orElse.invoke(optional, (Object) null);
            if (index == null) return -1;
            // CommonGunIndex.getGunData()
            Method getGunData = index.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(index);
            if (gunData == null) return -1;
            // GunData.getAmmoAmount()
            Method getAmount = gunData.getClass().getMethod("getAmmoAmount");
            Object result = getAmount.invoke(gunData);
            return result instanceof Integer i ? i : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * 获取枪械对应的弹药物品 ID（如 "tacz:762x39"）。
     */
    public static String getAmmoTypeId(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return "";
        try {
            // TimelessAPI.getCommonGunIndex(ResourceLocation)
            Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            Method getIndex = apiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
            Object optional = getIndex.invoke(null, ResourceLocation.parse(getGunId(stack)));
            if (optional == null) return "";
            Method orElse = optional.getClass().getMethod("orElse", Object.class);
            Object index = orElse.invoke(optional, (Object) null);
            if (index == null) return "";
            // CommonGunIndex.getGunData()
            Method getGunData = index.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(index);
            if (gunData == null) return "";
            // GunData.getAmmoId()
            Method getAmmoId = gunData.getClass().getMethod("getAmmoId");
            Object result = getAmmoId.invoke(gunData);
            return result != null ? result.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 给枪械填满弹药（设为大值，实体无限弹药）。
     */
    public static void refillAmmo(ItemStack stack) {
        setAmmo(stack, 999);
    }

    // ==================== IGunOperator 实体射击 ====================

    /**
     * 调用 IGunOperator.shoot()（Mixin 注入到 LivingEntity）。
     * @return ShootResult 枚举名（"SUCCESS", "COOL_DOWN", "NO_AMMO" 等），失败时返回 null
     */
    public static String tryShoot(LivingEntity entity, float pitch, float yaw) {
        if (!isAvailable()) return null;
        try {
            if (shootMethod == null) {
                shootMethod = entity.getClass().getMethod("shoot", Supplier.class, Supplier.class);
            }
            Object result = shootMethod.invoke(entity,
                    (Supplier<Float>) () -> pitch,
                    (Supplier<Float>) () -> yaw);
            return result != null ? result.toString() : null;
        } catch (Exception ignored) {
            shootMethod = null; // 重置缓存，可能 ClassLoader 变化
            return null;
        }
    }

    /**
     * 调用 IGunOperator.reload()（Mixin 注入到 LivingEntity）。
     */
    public static void tryReload(LivingEntity entity) {
        if (!isAvailable()) return;
        try {
            Method reloadMethod = entity.getClass().getMethod("reload");
            reloadMethod.invoke(entity);
        } catch (Exception ignored) {
        }
    }

    /**
     * 查询实体是否正在换弹（ReloadState.isReloading()）。
     */
    public static boolean isReloading(LivingEntity entity) {
        if (!isAvailable()) return false;
        try {
            Method m = entity.getClass().getMethod("getSynReloadState");
            Object state = m.invoke(entity);
            Method isReloading = state.getClass().getMethod("isReloading");
            Object result = isReloading.invoke(state);
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 初始化实体的 ShooterDataHolder（TACZ 不会自动为 non-player 实体调用）。
     * 必须在使用 IGunOperator 射击前调用，否则 shoot() 内部状态异常。
     */
    public static void initializeShooter(LivingEntity entity) {
        if (!isAvailable()) return;
        try {
            Method initMethod = entity.getClass().getMethod("initialData");
            initMethod.invoke(entity);
        } catch (Exception ignored) {
        }
    }
}
