package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.mcreator.mcanomalyarchives.init.UO007Blocks;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;

/** Vanilla placement, permissions and consumption; signed count is only the native GUI label. */
public final class NegativeBlockItem extends BlockItem implements NegativeCountItem {
    private final int material;
    public NegativeBlockItem(int material) { super(UO007Blocks.NEGATIVE_SPACE.get(), new Properties().stacksTo(64)); this.material = material; }
    @Override protected net.minecraft.world.level.block.state.BlockState getPlacementState(BlockPlaceContext context) {
        var state = super.getPlacementState(context);
        return state == null ? null : state.setValue(net.mcreator.mcanomalyarchives.block.NegativeSpaceBlock.MATERIAL, material);
    }
    @Override public String getDescriptionId() {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this);
        return "item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction() && context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
            CodexObservations.record(player, "negative_block_place");
        return result;
    }
}
