package net.mcreator.mcanomalyarchives.sanity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

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

    /** 攒着，等 DirtyPlayerSanityTracker 每 5 tick 统一发一次（同一 tick 内反复触发只发一个包）。 */
    public static void syncToClient(ServerPlayer player) {
        DirtyPlayerSanityTracker.markDirty(player);
    }

    /**
     * 立刻发。给"客户端状态刚被重置、不能等"的两个时机用：
     * 进服（PlayerLoggedInEvent）与死亡重生完成（PlayerRespawnEvent）。
     * 这两处要是也走批处理，客户端会先按默认的满值画 250ms 的 HUD。
     */
    public static void syncToClientNow(ServerPlayer player) {
        DirtyPlayerSanityTracker.syncNow(player);
    }
}
