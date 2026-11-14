package net.mcreator.mcanomalyarchives.client.rewind;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.client.TreeQuakeClientHandler;
import net.mcreator.mcanomalyarchives.network.RewindVisualPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class RewindClient {
    private static Object world;
    private static BlockPos center;
    private static int age, radius = 50;
    private static long lastPacket;
    private static List<RewindVisualPacket.Pose> poses = List.of();
    private static SoundInstance sound;
    private RewindClient() { }

    public static void accept(RewindVisualPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (packet.age() >= 60) {
            if (world == mc.level && packet.center().equals(center)) apply(packet.poses());
            clear();
            TreeQuakeClientHandler.startShake(.12f, 5);
            return;
        }
        boolean beginning = world != mc.level || center == null || !center.equals(packet.center());
        if (beginning) clear();
        world = mc.level; center = packet.center(); age = packet.age(); radius = packet.radius(); lastPacket = System.currentTimeMillis(); poses = packet.poses();
        if (beginning) {
            if (strength() > 0) {
                sound = new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives_rewind", "rewind"),
                        SoundSource.RECORDS, .9f, 1f, SoundInstance.createUnseededRandom(), false, 0,
                        SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                mc.getSoundManager().play(sound);
                TreeQuakeClientHandler.startShake(.22f, 7);
            }
        }
    }
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return center != null && world == mc.level && mc.player != null && (mc.isPaused() || System.currentTimeMillis() - lastPacket < 5000);
    }
    public static boolean localFrozen() { return active() && strength() > 0; }
    public static int radius() { return radius; }
    public static BlockPos center() { return center; }
    public static float age(float partial) { return Math.min(60, age + partial); }
    public static float strength() {
        Minecraft mc = Minecraft.getInstance();
        if (center == null || mc.player == null) return 0;
        return (float)Math.max(0, Math.min(1, (radius - mc.player.position().distanceTo(Vec3.atCenterOf(center))) / 5));
    }
    public static void clear() {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null; world = null; center = null; poses = List.of(); age = 0;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active()) { clear(); return; }
        if (!mc.isPaused()) apply(poses);
    }
    private static void apply(List<RewindVisualPacket.Pose> poses) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var pose : poses) {
            Entity entity = mc.level.getEntity(pose.id());
            if (entity == null) continue;
            entity.setOldPosAndRot();
            entity.setPos(pose.position());
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (!localFrozen()) return;
        var input = event.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false;
    }
    @SubscribeEvent public static void entityTick(EntityTickEvent.Pre event) {
        if (!active() || !event.getEntity().level().isClientSide || event.getEntity() == Minecraft.getInstance().player) return;
        if (event.getEntity().distanceToSqr(Vec3.atCenterOf(center)) < (radius + 4) * (radius + 4)) event.setCanceled(true);
    }
}
