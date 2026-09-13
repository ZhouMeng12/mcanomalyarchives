/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.entity.*;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

@EventBusSubscriber
public class McanomalyarchivesModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, McanomalyarchivesMod.MODID);
	public static final DeferredHolder<EntityType<?>, EntityType<StangeCloudEntity>> STANGE_CLOUD = register("stange_cloud",
			EntityType.Builder.<StangeCloudEntity>of(StangeCloudEntity::new, MobCategory.AMBIENT).setShouldReceiveVelocityUpdates(true).setTrackingRange(128).setUpdateInterval(3).fireImmune()

					.sized(30f, 5f));
	public static final DeferredHolder<EntityType<?>, EntityType<AnbulaEntity>> ANBULA = register("anbula",
			EntityType.Builder.<AnbulaEntity>of(AnbulaEntity::new, MobCategory.CREATURE).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<PrisonerEntity>> PRISONER = register("prisoner",
			EntityType.Builder.<PrisonerEntity>of(PrisonerEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<SittingEnityEntity>> SITTING_ENITY = register("sitting_enity",
			EntityType.Builder.<SittingEnityEntity>of(SittingEnityEntity::new, MobCategory.AMBIENT).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune()

					.sized(1f, 1f));
	public static final DeferredHolder<EntityType<?>, EntityType<SvanEntity>> SVAN = register("svan",
			EntityType.Builder.<SvanEntity>of(SvanEntity::new, MobCategory.CREATURE).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<QuEntity>> QU = register("qu", EntityType.Builder.<QuEntity>of(QuEntity::new, MobCategory.AMBIENT).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

			.sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<PurpleMonsterEntity>> PURPLE_MONSTER = register("purple_monster",
			EntityType.Builder.<PurpleMonsterEntity>of(PurpleMonsterEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<PurpleDogEntity>> PURPLE_DOG = register("purple_dog",
			EntityType.Builder.<PurpleDogEntity>of(PurpleDogEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<PurpleArrowEntity>> PURPLE_ARROW = register("purple_arrow",
			EntityType.Builder.<PurpleArrowEntity>of(PurpleArrowEntity::new, MobCategory.MISC).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(1).sized(0.5f, 0.5f));
	public static final DeferredHolder<EntityType<?>, EntityType<YifulinEntity>> YIFULIN = register("yifulin",
			EntityType.Builder.<YifulinEntity>of(YifulinEntity::new, MobCategory.CREATURE).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<DavidEntity>> DAVID = register("david",
			EntityType.Builder.<DavidEntity>of(DavidEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<PotterEntity>> POTTER = register("potter",
			EntityType.Builder.<PotterEntity>of(PotterEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<StrangeTreeEntity>> STRANGE_TREE = register("strange_tree",
			EntityType.Builder.<StrangeTreeEntity>of(StrangeTreeEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune()

					.sized(0.1f, 0.1f));
	public static final DeferredHolder<EntityType<?>, EntityType<PinkSheepEntity>> PINK_SHEEP = register("pink_sheep",
			EntityType.Builder.<PinkSheepEntity>of(PinkSheepEntity::new, MobCategory.CREATURE).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.ridingOffset(-0.6f).sized(0.9f, 1.3f));

	// Start of user code block custom entities
	// End of user code block custom entities
	private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return REGISTRY.register(registryname, () -> (EntityType<T>) entityTypeBuilder.build(registryname));
	}

	@SubscribeEvent
	public static void init(RegisterSpawnPlacementsEvent event) {
		StangeCloudEntity.init(event);
		AnbulaEntity.init(event);
		PrisonerEntity.init(event);
		SittingEnityEntity.init(event);
		SvanEntity.init(event);
		QuEntity.init(event);
		PurpleMonsterEntity.init(event);
		PurpleDogEntity.init(event);
		YifulinEntity.init(event);
		DavidEntity.init(event);
		PotterEntity.init(event);
		StrangeTreeEntity.init(event);
		PinkSheepEntity.init(event);
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(STANGE_CLOUD.get(), StangeCloudEntity.createAttributes().build());
		event.put(ANBULA.get(), AnbulaEntity.createAttributes().build());
		event.put(PRISONER.get(), PrisonerEntity.createAttributes().build());
		event.put(SITTING_ENITY.get(), SittingEnityEntity.createAttributes().build());
		event.put(SVAN.get(), SvanEntity.createAttributes().build());
		event.put(QU.get(), QuEntity.createAttributes().build());
		event.put(PURPLE_MONSTER.get(), PurpleMonsterEntity.createAttributes().build());
		event.put(PURPLE_DOG.get(), PurpleDogEntity.createAttributes().build());
		event.put(YIFULIN.get(), YifulinEntity.createAttributes().build());
		event.put(DAVID.get(), DavidEntity.createAttributes().build());
		event.put(POTTER.get(), PotterEntity.createAttributes().build());
		event.put(STRANGE_TREE.get(), StrangeTreeEntity.createAttributes().build());
		event.put(PINK_SHEEP.get(), PinkSheepEntity.createAttributes().build());
	}
}