package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.sanity.PlayerSanity;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;

public class McanomalyarchivesModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, McanomalyarchivesMod.MODID);

    public static final Supplier<AttachmentType<PlayerSanity>> PLAYER_SANITY =
        REGISTRY.register("player_sanity", () ->
            AttachmentType.builder(PlayerSanity::new)
                .serialize(PlayerSanity.CODEC.codec())
                .build()
        );

    // 紫怪自然触发冷却时间戳（gameTime），0 表示无冷却
    public static final Supplier<AttachmentType<Long>> PURPLE_MONSTER_TRIGGER_COOLDOWN =
        REGISTRY.register("purple_monster_trigger_cooldown", () ->
            AttachmentType.builder(() -> 0L).build()
        );

    // 紫怪同化完成标记（Phase 5 结束后设置）
    public static final Supplier<AttachmentType<Boolean>> PURPLE_ASSIMILATION_COMPLETED =
        REGISTRY.register("purple_assimilation_completed", () ->
            AttachmentType.builder(() -> false)
                .serialize(Codec.BOOL)
                .build()
        );

    // 窥视者遭遇次数（持久化，用于渐进逼近距离档位）
    public static final Supplier<AttachmentType<Integer>> PURPLE_STALKER_SIGHTINGS =
        REGISTRY.register("purple_stalker_sightings", () ->
            AttachmentType.builder(() -> 0)
                .serialize(Codec.INT)
                .build()
        );

    // Boss 战触发冷却时间戳（gameTime），0 表示无冷却
    public static final Supplier<AttachmentType<Long>> PURPLE_BOSS_FIGHT_COOLDOWN =
        REGISTRY.register("purple_boss_fight_cooldown", () ->
            AttachmentType.builder(() -> 0L).build()
        );
}
