package net.mcreator.mcanomalyarchives.anomaly.poppy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 全服共享存储，位置键含维度；计时随世界保存，重启不会留下永久悲伤花。 */
public final class PoppyAngerData extends SavedData {
    private final PoppyAngerState state;
    private static final Factory<PoppyAngerData> FACTORY = new Factory<>(
            () -> new PoppyAngerData(new PoppyAngerState()),
            (tag, registries) -> new PoppyAngerData(PoppyAngerState.load(tag)), null);

    private PoppyAngerData(PoppyAngerState state) {
        this.state = state;
    }

    public static PoppyAngerData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "mcanomalyarchives_poppy_anger");
    }

    public PoppyAngerState state() {
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(state.save());
        return tag;
    }
}
