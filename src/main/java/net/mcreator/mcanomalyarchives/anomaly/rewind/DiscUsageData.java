package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Disc wear is outside the rewound state, including after a server restart. */
public final class DiscUsageData extends SavedData {
    public static final int MAX_USES = 10;
    private final Map<UUID, Integer> used = new HashMap<>();
    private static final Factory<DiscUsageData> FACTORY = new Factory<>(DiscUsageData::new, (tag, lookup) -> {
        DiscUsageData data = new DiscUsageData();
        for (String key : tag.getAllKeys()) {
            try { data.used.put(UUID.fromString(key), Math.max(0, Math.min(MAX_USES, tag.getInt(key)))); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }, null);

    public static DiscUsageData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "mcanomalyarchives_negative_disc_usage");
    }
    public int used(UUID id) { return used.getOrDefault(id, 0); }
    public void consume(UUID id) { used.put(id, Math.min(MAX_USES, used(id) + 1)); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        used.forEach((id, count) -> tag.putInt(id.toString(), count));
        return tag;
    }
}
