package net.mcreator.mcanomalyarchives.client.earth;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.mcreator.mcanomalyarchives.client.TreeQuakeClientHandler;
import net.mcreator.mcanomalyarchives.network.EarthVisualPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class EarthClient {
    private static Object world;
    private static BlockPos center;
    private static int age;
    private static long received, receivedTick;
    private static boolean rewind, clearing;
    private static long dustReleased;
    public static void accept(EarthVisualPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(p.dimension())) return;
        if (p.age() < 0) {
            if (world == mc.level && center != null && age >= 600 && clearing && !p.rewind()) {
                clearing = false; dustReleased = mc.level.getGameTime(); age = 799;
                received = System.currentTimeMillis(); receivedTick = mc.level.getGameTime();
            } else if (dustReleased == 0 || p.rewind()) clear();
            return;
        }
        if (p.rewind() || p.age() < 600) dustReleased = 0;
        else if (clearing && !p.clearing()) dustReleased = mc.level.getGameTime();
        clearing = p.clearing();
        boolean explosion = p.age() >= 600 && p.age() < 640 && (world != mc.level || age < 600) && !p.rewind();
        world = mc.level; center = p.center(); age = p.age(); rewind = p.rewind(); received = System.currentTimeMillis(); receivedTick = mc.level.getGameTime();
        if (explosion && mc.player != null) {
            // Positional blast audio is scheduled by the server; do not duplicate it here.
            TreeQuakeClientHandler.startShake(1.1f, 180);
        }
    }
    public static boolean active() {
        var mc = Minecraft.getInstance();
        return center != null && world == mc.level && mc.player != null && !mc.player.isDeadOrDying()
            && (mc.isPaused() || System.currentTimeMillis() - received < 4000);
    }
    public static BlockPos center() { return center; }
    public static float age(float partial) {
        if (rewind || Minecraft.getInstance().level == null) return age;
        float extrapolated = age + Math.min(5, Math.max(0, Minecraft.getInstance().level.getGameTime() - receivedTick) + partial);
        return Math.min(age < 600 ? 599.99f : 799.99f, extrapolated);
    }
    public static float dust(float partial) {
        if (!active() || rewind || age < 600) return 0;
        if (clearing) return 1;
        return dustReleased == 0 ? 0 : Math.max(0, 1 - (Minecraft.getInstance().level.getGameTime() - dustReleased + partial) / 60f);
    }
    private static void clear() { center = null; world = null; age = 0; clearing = false; dustReleased = 0; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!active()) clear();
        else if (!rewind && age > 80 && age < 600 && Minecraft.getInstance().level.getGameTime() % 10 == 0)
            TreeQuakeClientHandler.startShake(Math.min(.2f, age / 4000f), 8);
    }
}
