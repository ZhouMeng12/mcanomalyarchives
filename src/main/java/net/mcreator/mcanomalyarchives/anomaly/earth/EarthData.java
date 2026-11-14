package net.mcreator.mcanomalyarchives.anomaly.earth;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One dimension-wide catastrophe; cursors survive saves without force-loading chunks. */
public final class EarthData extends SavedData {
    public record State(UUID id, UUID anchor, BlockPos center, int age, int phase, int column, int y,
                        boolean noAi, boolean noGravity, int[] deferred) {
        public State { center = center.immutable(); deferred = deferred.clone(); }
        @Override public int[] deferred() { return deferred.clone(); }
    }
    State state;
    private static final Factory<EarthData> FACTORY = new Factory<>(EarthData::new, EarthData::load, null);
    public static EarthData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, "mcanomalyarchives_earth"); }
    static EarthData load(CompoundTag tag, HolderLookup.Provider lookup) {
        EarthData data = new EarthData();
        if (tag.hasUUID("Id") && tag.hasUUID("Anchor")) {
            data.state = new State(tag.getUUID("Id"), tag.getUUID("Anchor"), BlockPos.of(tag.getLong("Center")),
                Math.max(0, tag.getInt("Age")), Math.min(2, Math.max(0, tag.getInt("Phase"))),
                tag.getInt("ScanVersion") >= 4 ? Math.max(0, tag.getInt("Column")) : 0,
                tag.getInt("ScanVersion") >= 4 ? Math.max(-EarthRules.DEPTH, tag.getInt("Y")) : EarthRules.COLUMN_START,
                tag.getBoolean("NoAI"), tag.getBoolean("NoGravity"), tag.getInt("ScanVersion") >= 4 ? tag.getIntArray("Deferred") : new int[0]);
        }
        return data;
    }
    public void set(State value) { state = value; setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        State s = state;
        if (s != null) {
            tag.putInt("ScanVersion", 4); tag.putUUID("Id", s.id()); tag.putUUID("Anchor", s.anchor()); tag.putLong("Center", s.center().asLong());
            tag.putInt("Age", s.age()); tag.putInt("Phase", s.phase()); tag.putInt("Column", s.column()); tag.putInt("Y", s.y());
            tag.putBoolean("NoAI", s.noAi()); tag.putBoolean("NoGravity", s.noGravity()); tag.putIntArray("Deferred", s.deferred());
        }
        return tag;
    }
}
