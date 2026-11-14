package net.mcreator.mcanomalyarchives.client.purple;

import java.util.*;
import net.mcreator.mcanomalyarchives.network.PurpleAssimilationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class PurpleAssimilationClient {
    private record State(int stage, long start) { }
    private static final Map<UUID, State> states = new HashMap<>();
    private PurpleAssimilationClient() { }
    public static void accept(PurpleAssimilationPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.stage() <= 0) { states.remove(packet.player()); return; }
        State old = states.get(packet.player());
        if (old != null && old.stage == packet.stage()) return;
        states.put(packet.player(), new State(Math.min(3, packet.stage()), mc.level == null ? 0 : mc.level.getGameTime()));
        if (mc.player != null && mc.player.getUUID().equals(packet.player()) && packet.stage() < 3)
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, .3f, .5f);
    }
    public static int color(Player player, boolean body) {
        State state = states.get(player.getUUID());
        if (state == null || state.stage < 2 || player.isInvisible()) return 0;
        float growth = state.stage == 3 ? 1 : Math.min(1, Math.max(0, player.level().getGameTime() - state.start) / 80f);
        float alpha = body ? Math.max(0, (growth - .4f) / .6f) : Math.min(1, growth * 2);
        return ((int)(alpha * 205) << 24) | 0x7835b5;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { states.clear(); }
}
