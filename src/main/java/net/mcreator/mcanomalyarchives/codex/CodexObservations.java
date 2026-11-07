package net.mcreator.mcanomalyarchives.codex;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/** Stable observation IDs: discovering an anomaly never reveals its unobserved properties. */
public final class CodexObservations {
    private static final String TAG = "McanomalyCodexObservations";
    public record Note(String id, int entry) {
        public String key() { return "codex.mcanomalyarchives.note." + id; }
    }
    public static final List<Note> NOTES = List.of(
        new Note("cloud_small", 0), new Note("cloud_black", 0),
        new Note("cloud_contact", 0), new Note("cloud_transmute", 0),
        new Note("cloud_red", 0), new Note("cloud_erase", 0),
        new Note("garbled_spread", 1), new Note("poppy_addiction", 2), new Note("poppy_withdrawal", 2),
        new Note("negative_apple", 6), new Note("negative_pickaxe", 6),
        new Note("fishing_catch", 3), new Note("fishing_far", 3), new Note("fishing_living", 3),
        new Note("purple_arrival", 4), new Note("purple_change", 4),
        new Note("purple_assimilation", 4), new Note("purple_stalker", 4),
        new Note("purple_reward", 4), new Note("purple_pet", 4), new Note("name_rewrite", 7),
        new Note("name_garbled", 7), new Note("name_mining", 7),
        new Note("name_erase", 7), new Note("name_mimic", 7),
        new Note("name_absorb", 7), new Note("name_compression", 7),
        new Note("tree_chase", 10), new Note("tree_replant", 10), new Note("tree_exposure", 10),
        new Note("sheep_blessing", 11),
        new Note("sheep_calamity", 11), new Note("sheep_blink", 11));

    private CodexObservations() { }

    public static Set<String> decode(String encoded) {
        Set<String> known = new LinkedHashSet<>();
        for (String id : encoded.split(",")) if (note(id) != null) known.add(id);
        return known;
    }

    public static Note note(String id) {
        return NOTES.stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    public static String append(String encoded, String id) {
        if (note(id) == null) return encoded;
        Set<String> known = decode(encoded);
        known.add(id);
        return String.join(",", known);
    }

    public static String encoded(ServerPlayer player) {
        return player.getPersistentData().getString(TAG);
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        to.getPersistentData().putString(TAG, encoded(from));
    }

    public static void witness(net.minecraft.world.entity.Entity source, String id) {
        if (!(source.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(source) <= 48 * 48 && player.hasLineOfSight(source)
                    && player.getLookAngle().dot(source.getBoundingBox().getCenter()
                            .subtract(player.getEyePosition()).normalize()) > .5) record(player, id);
        }
    }

    /** Called at the effect's actual execution point; repeated experiences add no duplicate records. */
    public static void record(ServerPlayer player, String id) {
        Note note = note(id);
        if (player == null || note == null) return;
        String before = encoded(player);
        if (decode(before).contains(id)) return;
        player.getPersistentData().putString(TAG, append(before, id));
        if (!AnomalyCodex.unlock(player, note.entry(), player.serverLevel())) {
            CodexNotifier.syncTo(player, false);
            player.playNotifySound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                    net.minecraft.sounds.SoundSource.PLAYERS, .45f, .9f);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "codex.mcanomalyarchives.note_added",
                    net.minecraft.network.chat.Component.translatable(AnomalyCodex.get(note.entry()).nameKey())), true);
        }
    }
}
