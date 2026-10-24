package net.mcreator.mcanomalyarchives.anomaly.poppy;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.GlobalPos;

/** 花朵计时。维度属于键的一部分，换成悲伤花时保留同一记录。 */
public final class PoppyAngerState {
    private final Map<GlobalPos, Long> watched = new HashMap<>();
    private final Map<GlobalPos, Long> angryUntil = new HashMap<>();

    public void watch(GlobalPos key, long tick) {
        watched.put(key, tick);
        angryUntil.remove(key);
    }

    public long lastWatched(GlobalPos key) {
        return watched.getOrDefault(key, 0L);
    }

    public boolean wasWatched(GlobalPos key) {
        return watched.containsKey(key);
    }

    public void becomeAngry(GlobalPos key, long until) {
        angryUntil.put(key, until);
    }

    public Long angryUntil(GlobalPos key) {
        return angryUntil.get(key);
    }

    public void calm(GlobalPos key) {
        angryUntil.remove(key);
    }

    public void onReplacement(GlobalPos key, boolean becomesSadPoppy) {
        if (!becomesSadPoppy) remove(key);
    }

    public void remove(GlobalPos key) {
        watched.remove(key);
        angryUntil.remove(key);
    }

    public Map<GlobalPos, Long> watchTimes() {
        return Map.copyOf(watched);
    }

    public net.minecraft.nbt.CompoundTag save() {
        var root = new net.minecraft.nbt.CompoundTag();
        var entries = new net.minecraft.nbt.ListTag();
        for (var entry : watched.entrySet()) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("Dimension", entry.getKey().dimension().location().toString());
            tag.putLong("Pos", entry.getKey().pos().asLong());
            tag.putLong("Watched", entry.getValue());
            Long until = angryUntil.get(entry.getKey());
            if (until != null) tag.putLong("AngryUntil", until);
            entries.add(tag);
        }
        root.put("Flowers", entries);
        return root;
    }

    public static PoppyAngerState load(net.minecraft.nbt.CompoundTag root) {
        var state = new PoppyAngerState();
        var entries = root.getList("Flowers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var tag = entries.getCompound(i);
            var dimension = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null || !tag.contains("Pos", net.minecraft.nbt.Tag.TAG_LONG)
                    || !tag.contains("Watched", net.minecraft.nbt.Tag.TAG_LONG)) continue;
            var key = GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension),
                    net.minecraft.core.BlockPos.of(tag.getLong("Pos")));
            state.watch(key, tag.getLong("Watched"));
            if (tag.contains("AngryUntil", net.minecraft.nbt.Tag.TAG_LONG)) state.becomeAngry(key, tag.getLong("AngryUntil"));
        }
        return state;
    }

    public void clear() {
        watched.clear();
        angryUntil.clear();
    }
}
