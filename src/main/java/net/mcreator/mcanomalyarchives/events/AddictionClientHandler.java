package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyAddictionRules;
import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyScareSequence;
import net.mcreator.mcanomalyarchives.network.PoppyAddictionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.mcreator.mcanomalyarchives.client.render.PoppyPostEffect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.DeathScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;

/** One authoritative source, one camera controller; never writes sensitivity settings. */
public final class AddictionClientHandler {
    private static PoppyAddictionPacket state;
    private static ClientLevel world;
    private static LocalPlayer owner;
    private static int age;
    private static float intensity, previousIntensity, fade;
    private static long cameraTime;
    private static boolean scareSoundPlayed, warningSoundPlayed;
    public static void init() { NeoForge.EVENT_BUS.register(new AddictionClientHandler()); }
    public static void accept(PoppyAddictionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.screen instanceof DeathScreen) { clear(); return; }
        if (world != mc.level || owner != mc.player) clear();
        if (state == null || state.seed() != packet.seed() || state.route() != packet.route()) {
            scareSoundPlayed = warningSoundPlayed = false;
            net.mcreator.mcanomalyarchives.McanomalyarchivesMod.LOGGER.debug(
                    "Poppy client state: route={}, ticks={}, redCloud={}",packet.route(),packet.ticks(),packet.entityId()>=0);
        }
        world = mc.level; owner = mc.player; state = packet; age = 0;
    }
    public static void clear() {
        state = null; world = null; owner = null; age = 0; intensity = previousIntensity = fade = 0; cameraTime = 0;
        scareSoundPlayed = warningSoundPlayed = false;
        PoppyPostEffect.clear();
    }
    public static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player == owner && mc.level == world && mc.player.isAlive()
                && !(mc.screen instanceof DeathScreen) && fade > 0;
    }
    @SubscribeEvent public void screenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DeathScreen) clear();
    }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    public static int route() { return state == null ? -1 : state.route(); }
    public static long seed() { return state == null ? 0 : state.seed(); }
    public static boolean cloud() { return state != null && state.entityId() >= 0; }
    public static int level() { return state == null ? 0 : PoppyAddictionRules.level(state.ticks()); }
    public static float intensity(float partial) { return Mth.lerp(partial, previousIntensity, intensity); }
    public static float fade() { return fade; }
    public static float execution() { return state == null ? 0 : Mth.clamp((state.ticks() + Math.min(age, 5) - 2000) / 200f, 0, 1); }
    public static float scare() { return state == null || cloud() || age >= 40 ? 0 : PoppyAddictionRules.scare(state.ticks() + Math.min(age, 5)); }
    public static Vec3 base() {
        if (state == null) return Vec3.ZERO;
        Minecraft mc = Minecraft.getInstance();
        if (cloud() && mc.level != null) {
            var entity = mc.level.getEntity(state.entityId());
            if (entity != null) return entity.position().add(0, -.33, 0);
        }
        return new Vec3(state.x(), state.y(), state.z());
    }
    public static Vec3 focus() {
        Vec3 base = base();
        if (cloud()) return base;
        return base.add(0, .5, 0);
    }
    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player != owner || mc.level != world || !mc.player.isAlive()
                || mc.screen instanceof DeathScreen) { clear(); return; }
        if (mc.isPaused()) return;
        age++;
        previousIntensity = intensity;
        boolean active = state != null && state.ticks() >= 200 && age < 40;
        float target = active ? PoppyAddictionRules.intensity(state.ticks() + Math.min(age, 5)) : 0;
        intensity += Mth.clamp(target - intensity, -.4f, .15f);
        fade = Mth.clamp(fade + (active ? .1f : -.08f), 0, 1);
        if(!active)scareSoundPlayed=warningSoundPlayed=false;
        if (scare() > 0 && !scareSoundPlayed) {
            scareSoundPlayed = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_ROAR, .68f, .8f));
        }
        if (PoppyScareSequence.stage(scare()) == PoppyScareSequence.Stage.WARNINGS && !warningSoundPlayed) {
            warningSoundPlayed = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ELDER_GUARDIAN_CURSE, .8f, .65f));
        }
        if (active && mc.player.tickCount % Math.max(35, 110 - level() * 6) == 0) {
            Vec3 p = base();
            mc.level.playLocalSound(p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.AZALEA_LEAVES_STEP,
                    net.minecraft.sounds.SoundSource.AMBIENT, .18f + level() * .015f, .65f, false);
        }
    }
    @SubscribeEvent public void input(MovementInputUpdateEvent event) {
        if (!shouldRender() || Minecraft.getInstance().screen != null || event.getEntity() != owner || level() < 4 || age >= 40) return;
        event.getInput().jumping = false;
        if (level() >= 7) { event.getInput().forwardImpulse = 0; event.getInput().leftImpulse = 0; }
    }
    /** Called after mouse input, before the camera is set up for this frame. */
    public static void controlGaze() {
        long now = System.nanoTime();
        float dt = cameraTime == 0 ? .016f : Math.min(.05f, (now - cameraTime) / 1_000_000_000f);
        cameraTime = now;
        Minecraft mc = Minecraft.getInstance();
        int level = level();
        if (!shouldRender() || level == 0 || age >= 40 || mc.screen != null || mc.isPaused()) return;
        Vec3 d = focus().subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90;
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())), -90, 90);
        if (level >= 4) {
            float limit = level >= 7 ? 0 : new float[]{12, 8, 5}[level - 4];
            mc.player.setYRot(yaw + Mth.clamp(Mth.wrapDegrees(mc.player.getYRot() - yaw), -limit, limit));
            mc.player.setXRot(pitch + Mth.clamp(mc.player.getXRot() - pitch, -limit, limit));
        } else {
            float step = new float[]{160, 220, 300}[level - 1] * dt;
            mc.player.setYRot(PoppyAddictionRules.approachAngle(mc.player.getYRot(), yaw, step));
            mc.player.setXRot(PoppyAddictionRules.approachAngle(mc.player.getXRot(), pitch, step));
        }
    }
}
