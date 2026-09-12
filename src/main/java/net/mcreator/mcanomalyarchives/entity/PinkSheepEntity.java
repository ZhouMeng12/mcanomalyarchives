package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * UO-012 幸运粉羊（异常粉色羊）—— MCreator 生成区里的【薄壳】。
 *
 * 【工程层约定 · 重要】
 * 本文件属于 MCreator 生成区（元素 elements/PinkSheep.mod.json），重新生成代码会覆盖它。
 * 因此这里只保留"不能搬走"的东西：
 *   1) 继承原版 Sheep（模型/材质/羊毛染色/叫声/剪毛全部沿用原版，方法 C）；
 *   2) 强制粉色；
 *   3) 两个防 NPE 的空覆写（见下）；
 *   4) MCreator 约定会被注册表调用的两个静态钩子 init / createAttributes。
 *
 * 全部玩法机制（观察者效应状态机、消散转移、栖息地选点、幸运、灾厄）都在
 * {@code net.mcreator.mcanomalyarchives.anomaly.pinksheep} 与 {@code events} 包里，
 * MCreator 不会触碰那些文件。
 *
 * 若本文件被覆盖，运行 tools/mcreator-guard/guard.ps1 -Action apply 一键还原。
 *
 * —— 两个空覆写的必要性：
 * - registerGoals 为空 → 静止不动（粉羊不游荡、不跟随，只是"站在那里"）。
 * - customServerAiStep 必须同时为空：Sheep#customServerAiStep 会读取 private 字段
 *   eatBlockGoal（只由父类 registerGoals 初始化），清空 goals 后会 NPE 崩服。
 */
public class PinkSheepEntity extends Sheep {

	public PinkSheepEntity(EntityType<? extends Sheep> type, Level world) {
		super(type, world);
		this.setColor(DyeColor.PINK);
	}

	@Override
	protected void registerGoals() {
		// 空：静止。粉羊不参与任何 AI 目标（走位由观察者效应的转移逻辑决定）。
	}

	@Override
	protected void customServerAiStep() {
		// 空：跳过父类对 eatBlockGoal 的读取，防 NPE（详见类注释）。
		// "无人注视立即消失"由 PinkSheepLookHandler 在 ServerTickEvent.Post 统一结算，
		// 这里不做，避免与 handler 的判定时序冲突。
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType reason, SpawnGroupData spawnData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
		this.setColor(DyeColor.PINK);
		return data;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// 存档载入后重新强制粉色（防止被当作普通羊保存成白色）
		this.setColor(DyeColor.PINK);
	}

	// ===== MCreator 约定钩子（由 init/McanomalyarchivesModEntities 调用，勿改名） =====

	public static void init(RegisterSpawnPlacementsEvent event) {
		event.register(net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities.PINK_SHEEP.get(),
				SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(entityType, world, reason, pos, random) -> Mob.checkMobSpawnRules(entityType, world, reason, pos, random),
				RegisterSpawnPlacementsEvent.Operation.REPLACE);
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MAX_HEALTH, 20.0); // 粉羊血量 20
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.23);
		builder = builder.add(Attributes.FOLLOW_RANGE, 16.0);
		return builder;
	}
}
