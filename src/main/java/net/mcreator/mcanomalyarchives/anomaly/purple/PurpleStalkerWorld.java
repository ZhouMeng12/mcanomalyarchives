package net.mcreator.mcanomalyarchives.anomaly.purple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

/** Bounded, loaded-only placement and visibility queries. Tinted glass remains opaque. */
public final class PurpleStalkerWorld {
    private PurpleStalkerWorld() { }

    public static boolean glass(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.GLASS || b instanceof StainedGlassBlock
                || b instanceof StainedGlassPaneBlock || b == Blocks.GLASS_PANE;
    }

    public static boolean visible(ServerLevel level, Entity viewer, Vec3 target) {
        Vec3 start = viewer.getEyePosition();
        double length = start.distanceTo(target);
        if (length > 48) return false;
        if (!level.hasChunksAt(BlockPos.containing(Math.min(start.x, target.x), Math.min(start.y, target.y), Math.min(start.z, target.z)),
                BlockPos.containing(Math.max(start.x, target.x), Math.max(start.y, target.y), Math.max(start.z, target.z)))) return false;
        return level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
                return glass(state) ? Shapes.empty() : super.getBlockShape(state, world, pos);
            }
        }).getType() == HitResult.Type.MISS;
    }

    public static boolean watched(ServerLevel level, Vec3 eye) {
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            Vec3 delta = eye.subtract(p.getEyePosition());
            if (delta.lengthSqr() <= 1600 && p.getLookAngle().dot(delta.normalize()) > .8
                    && visible(level, p, eye)) return true;
        }
        return false;
    }

    public static boolean canStand(ServerLevel level, BlockPos pos) {
        AABB box = McanomalyarchivesModEntities.PURPLE_MONSTER.get().getDimensions()
                .makeBoundingBox(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - .1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(p) || !level.getFluidState(p).isEmpty()) return false;
        }
        return !(level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock)
                && level.noCollision(box) && !level.noCollision(box.move(0, -.1, 0));
    }

    public static Vec3 eyeAt(BlockPos pos) {
        return new Vec3(pos.getX() + .5, pos.getY() +
                McanomalyarchivesModEntities.PURPLE_MONSTER.get().getDimensions().eyeHeight(), pos.getZ() + .5);
    }

    public static boolean outdoors(ServerLevel level, BlockPos feet) {
        for (int y = 2; y <= 16; y++) {
            BlockPos pos = feet.above(y);
            if (level.canSeeSky(pos)) return true;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !(state.getBlock() instanceof LeavesBlock)) return false;
        }
        return false;
    }

    public static BlockPos window(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        if (level.canSeeSky(origin.above(2))) return null;
        // Scan near the player's eye level; only accept an exposed exterior and an actual glass crossing.
        for (BlockPos pane : BlockPos.betweenClosed(origin.offset(-12, 0, -12), origin.offset(12, 3, 12))) {
            if (!level.hasChunkAt(pane) || !glass(level.getBlockState(pane))) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Vec3 outward = Vec3.atLowerCornerOf(side.getNormal());
                if (player.position().subtract(Vec3.atCenterOf(pane)).dot(outward) >= -.5) continue;
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos candidate = pane.relative(side).offset(0, dy, 0).immutable();
                    Vec3 eye = eyeAt(candidate);
                    double distance = player.distanceToSqr(eye);
                    if (distance < 16 || distance > 144 || !canStand(level, candidate)
                            || !outdoors(level, candidate) || !visible(level, player, eye)) continue;
                    BlockHitResult first = level.clip(new ClipContext(player.getEyePosition(), eye,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (first.getType() != HitResult.Type.BLOCK || !glass(level.getBlockState(first.getBlockPos()))
                            || new AABB(pane).clip(player.getEyePosition(), eye).isEmpty()) continue;
                    if (!watched(level, eye)) return candidate;
                }
            }
        }
        return null;
    }
}
