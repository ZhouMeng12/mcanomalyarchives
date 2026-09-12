package net.mcreator.mcanomalyarchives.item;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleArrowEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModDataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

public class PurplehandItem extends Item {

    private static final ResourceLocation REACH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "purplehand_reach");

    public PurplehandItem() {
        super(new Item.Properties().rarity(Rarity.RARE).attributes(
            ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 20, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_MODIFIER_ID, 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(stack.getItem()))
            return InteractionResultHolder.pass(stack);

        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            PurpleArrowEntity spike = new PurpleArrowEntity(McanomalyarchivesModEntities.PURPLE_ARROW.get(), player, level, null);
            spike.setPos(player.getX() + look.x * 1.5,
                    player.getEyeY() + look.y * 1.5 - 0.1,
                    player.getZ() + look.z * 1.5);
            spike.shoot(look.x, look.y, look.z, 3.0F, 0);
            spike.setDamage(20.0);
            spike.setCritArrow(false);
            spike.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(spike);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    McanomalyarchivesMod.PURPLEHAND_SHOOT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

            player.getCooldowns().addCooldown(stack.getItem(), 40);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        stack.set(McanomalyarchivesModDataComponents.ATTACK_TIME, System.currentTimeMillis());
        return true;
    }
}
