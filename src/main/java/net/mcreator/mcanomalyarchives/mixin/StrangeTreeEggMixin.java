package net.mcreator.mcanomalyarchives.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import java.util.Optional;

/**
 * 怪树刷怪蛋行为（mixin 方式，避免被 MCreator 再生成的注册文件覆盖）：
 * 右键泥土/草方块 → 消耗蛋 + 放置怪树结构（strtree.nbt），不生成实体；
 * 右键其它方块 → 不消耗不生成。
 */
@Mixin(SpawnEggItem.class)
public abstract class StrangeTreeEggMixin {

    private static final ResourceLocation STR_TREE_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "strtree");

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void mcanomalyarchives$onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        // 只处理怪树刷怪蛋
        if ((Object) this != McanomalyarchivesModItems.STRANGE_TREE_SPAWN_EGG.get()) {
            return;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        // 只在泥土/草方块上放行
        BlockPos clicked = context.getClickedPos();
        BlockPos ground;
        if (isDirtOrGrass(level.getBlockState(clicked))) {
            ground = clicked;
        } else if (isDirtOrGrass(level.getBlockState(clicked.below()))) {
            ground = clicked.below();
        } else {
            cir.setReturnValue(InteractionResult.FAIL); // 不消耗、不生成
            return;
        }
        // 放置怪树结构（不生成实体）
        buildTree(serverLevel, ground);
        Player player = context.getPlayer();
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    private static boolean isDirtOrGrass(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT);
    }

    private static void buildTree(ServerLevel level, BlockPos ground) {
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> template = manager.get(STR_TREE_TEMPLATE);
        if (template.isPresent()) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
            // 结构原点在角落：用尺寸的一半偏移，让"树心"对准点击位置
            net.minecraft.core.Vec3i size = template.get().getSize();
            BlockPos placePos = new BlockPos(ground.getX() - size.getX() / 2, ground.getY(), ground.getZ() - size.getZ() / 2);
            template.get().placeInWorld(level, placePos, placePos, settings, level.getRandom(), 2);
        }
        // 与 strtree 结构一致：树干中心（点击处）下方生成怪树实体
        spawnEntity(level, ground);
    }

    private static void spawnEntity(ServerLevel level, BlockPos base) {
        if (!level.getEntitiesOfClass(StrangeTreeEntity.class,
                new net.minecraft.world.phys.AABB(base).inflate(16.0)).isEmpty()) {
            return;
        }
        StrangeTreeEntity tree = new StrangeTreeEntity(McanomalyarchivesModEntities.STRANGE_TREE.get(), level);
        // 往上移一格：脚底对齐树干最底层（原木从 y=1 开始）
        tree.setPos(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        level.addFreshEntity(tree);
    }
}
