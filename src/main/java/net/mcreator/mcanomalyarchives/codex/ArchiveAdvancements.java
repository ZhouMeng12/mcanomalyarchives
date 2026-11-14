package net.mcreator.mcanomalyarchives.codex;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;

/** Custom triggers live outside MCreator's generated sources. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "mcanomalyarchives")
public final class ArchiveAdvancements {
    private ArchiveAdvancements() { }
    public static void award(ServerPlayer player, String id) {
        if (player == null || !CodexOriginStory.hasReceived(player)) return;
        var advancement = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", id));
        if (advancement == null) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        if (!progress.isDone()) for (String criterion : progress.getRemainingCriteria())
            player.getAdvancements().award(advancement, criterion);
    }
    public static void witness(Entity source, String id) {
        if (!(source.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players())
            if (player.distanceToSqr(source) <= 48 * 48 && player.hasLineOfSight(source)
                && player.getLookAngle().dot(source.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize()) > .5)
                award(player, id);
    }
    public static void observation(ServerPlayer p, String note) {
        if (note.startsWith("name_") && !note.equals("name_absorb")) award(p, "name_first");
        String id = switch (note) {
            case "garbled_spread" -> "garbled_infect";
            case "purple_arrival", "purple_change", "purple_stalker", "tree_chase", "tree_replant", "name_garbled" -> note;
            case "tree_exposure" -> "tree_burrow";
            case "sheep_blessing" -> "sheep_lucky";
            case "negative_apple" -> "negative_apple";
            case "negative_bow_release" -> "negative_bow";
            case "negative_block_place" -> "negative_space";
            case "negative_block_return" -> "negative_return";
            case "negative_disc_rewind" -> "disc_rewind";
            case "name_rewrite" -> "name_first";
            case "name_compression" -> "name_compress";
            case "name_earth" -> "earth_name";
            case "earth_rewind" -> "earth_cancel";
            default -> "";
        };
        if (!id.isEmpty()) award(p, id);
    }
    public static void tick(ServerPlayer p) {
        if (!CodexOriginStory.hasReceived(p)) return;
        award(p, "welcome");
        award(p, "codex_received");
        Set<String> notes = CodexObservations.decode(CodexObservations.encoded(p));
        if (!notes.isEmpty()) award(p, "first_note");
        // Archive discovery is not enough: these require an observed property in each implemented category.
        Set<Integer> entries = new java.util.HashSet<>();
        for (String note : notes) {
            entries.add(CodexObservations.note(note).entry());
            observation(p, note);
        }
        if (entries.size() >= 4) award(p, "four_anomalies");
        if (entries.containsAll(Set.of(0,1,2,3,4,6,7,10,11))) award(p, "all_anomalies");
        if (notes.contains("sheep_blessing") && notes.contains("sheep_calamity")) award(p,"sheep_both");
        for (var stack : p.getInventory().items) inventory(p, stack);
        for (var stack : p.getInventory().offhand) inventory(p, stack);
    }
    private static void inventory(ServerPlayer p, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("mcanomalyarchives")) return;
        if (id.getPath().equals("uo002_garbled")) award(p,"garbled_get");
        if (id.getPath().startsWith("negative_")) award(p,"negative_get");
    }
    public static void blockInfected(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center) > 48*48 || p.getLookAngle().dot(center.subtract(p.getEyePosition()).normalize()) <= .5) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), center,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if (hit.getBlockPos().equals(pos)) award(p,"garbled_block");
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        event.getEntity().getPersistentData().remove("ArchiveEarthSurvivor");
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getEntity().getPersistentData().remove("ArchiveEarthSurvivor");
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void anvilTaken(net.neoforged.neoforge.event.entity.player.AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getOutput().isEmpty()
            || !event.getRight().is(net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems.BAIBIAN_NAME_TAG.get())
            || event.getLeft().is(net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems.BAIBIAN_NAME_TAG.get())) return;
        var output = event.getOutput();
        boolean garbled = output.getItem() instanceof net.mcreator.mcanomalyarchives.item.UO002GarbledItem;
        if (garbled || net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState.isNamed(output)
            || net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState.isUnstable(output)) {
            CodexObservations.record(p,"name_rewrite");
            if (garbled) { CodexObservations.record(p,"name_garbled"); award(p,"garbled_get"); }
        }
    }
    public static void checkSurvivor(ServerPlayer p) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator()) p.getPersistentData().remove("ArchiveEarthSurvivor");
    }
    public static void earthBlast(ServerLevel level, java.util.UUID event, net.minecraft.core.BlockPos center) {
        for (ServerPlayer p : level.players()) if (p.isAlive() && !p.isCreative() && !p.isSpectator()
            && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center)) < 256*256)
            p.getPersistentData().putUUID("ArchiveEarthSurvivor",event);
    }
    public static void earthEnd(ServerLevel level, java.util.UUID event) {
        for (ServerPlayer p : level.players()) {
            var tag=p.getPersistentData();
            if (tag.hasUUID("ArchiveEarthSurvivor") && tag.getUUID("ArchiveEarthSurvivor").equals(event)) {
                tag.remove("ArchiveEarthSurvivor");
                if (p.isAlive() && !p.isCreative() && !p.isSpectator()) award(p,"earth_survive");
            }
        }
    }
}
