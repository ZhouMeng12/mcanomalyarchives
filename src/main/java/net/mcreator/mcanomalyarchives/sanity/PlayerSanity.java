package net.mcreator.mcanomalyarchives.sanity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.network.PacketDistributor;
import net.mcreator.mcanomalyarchives.network.SyncPlayerSanityPacket;

public class PlayerSanity {
    public static final int MAX_SANITY = 100;
    public static final int MIN_SANITY = 0;

    public static final MapCodec<PlayerSanity> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("sanity").forGetter(ps -> ps.sanity)
        ).apply(instance, PlayerSanity::fromSanity)
    );

    private int sanity;

    public PlayerSanity() {
        this.sanity = MAX_SANITY;
    }

    public static PlayerSanity fromSanity(int sanity) {
        PlayerSanity ps = new PlayerSanity();
        ps.sanity = Math.clamp(sanity, MIN_SANITY, MAX_SANITY);
        return ps;
    }

    public int getSanity() {
        return sanity;
    }

    public void setSanity(int value) {
        this.sanity = Math.clamp(value, MIN_SANITY, MAX_SANITY);
    }

    public void reduceSanity(int amount) {
        setSanity(this.sanity - amount);
    }

    public void increaseSanity(int amount) {
        setSanity(this.sanity + amount);
    }

    public void resetToMax() {
        this.sanity = MAX_SANITY;
    }

    public static void syncToClient(ServerPlayer player) {
        int sanity = player.getData(
            net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments.PLAYER_SANITY
        ).getSanity();
        PacketDistributor.sendToPlayer(player, new SyncPlayerSanityPacket(sanity));
    }
}
