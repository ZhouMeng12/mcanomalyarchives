package net.mcreator.mcanomalyarchives.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.network.OpenPurpleGuiPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class PurpleGuiCommand {

	public static void init() {
		NeoForge.EVENT_BUS.register(new PurpleGuiCommand());
	}

	@SubscribeEvent
	public void registerCommands(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

		dispatcher.register(Commands.literal("purplegui")
			.requires(source -> source.hasPermission(2))
			.then(Commands.argument("phase", IntegerArgumentType.integer(1, 5))
				.executes(this::executeOpenWithPhase))
			.executes(this::executeOpenDefault));
	}

	private int executeOpenDefault(CommandContext<CommandSourceStack> ctx) {
		return executeOpen(ctx, 2);
	}

	private int executeOpenWithPhase(CommandContext<CommandSourceStack> ctx) {
		int phase = IntegerArgumentType.getInteger(ctx, "phase");
		return executeOpen(ctx, phase);
	}

	private int executeOpen(CommandContext<CommandSourceStack> ctx, int phase) {
		if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
			double yawRad = Math.toRadians(player.getYRot());

			ServerLevel serverLevel = (ServerLevel) player.level();
			int purpleDogId = 0;

			if (phase == 4) {
				// 阶段4：紫怪在左边，purpledog在中间
				// 紫怪：前方1.5格 + 左侧3格
				double frontX = -Math.sin(yawRad) * 1.5;
				double frontZ = Math.cos(yawRad) * 1.5;
				double leftX = Math.cos(yawRad) * 1.0;
				double leftZ = Math.sin(yawRad) * 1.0;
				double spawnX = player.getX() + frontX + leftX;
				double spawnZ = player.getZ() + frontZ + leftZ;
				double spawnY = player.getY();

				PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), serverLevel);
				purpleMonster.setPos(spawnX, spawnY, spawnZ);
				// 朝向玩家
				purpleMonster.setXRot(0.0F);
				purpleMonster.setYRot(player.getYRot() + 180.0F);
				purpleMonster.yBodyRot = player.getYRot() + 180.0F;
				purpleMonster.yHeadRot = player.getYRot() + 180.0F;
				purpleMonster.setPerformMode(player.getUUID());
				purpleMonster.setTextureVariant(1); // 阶段4使用 purple 纹理
				serverLevel.addFreshEntity(purpleMonster);

				// purpledog：玩家前方1.0格
				double dogX = player.getX() - Math.sin(yawRad) * 1.5;
				double dogZ = player.getZ() + Math.cos(yawRad) * 1.5;
				double dogY = player.getY();

				PurpleDogEntity purpleDog = new PurpleDogEntity(McanomalyarchivesModEntities.PURPLE_DOG.get(), serverLevel);
				purpleDog.setPos(dogX, dogY, dogZ);
				// 朝向玩家
				purpleDog.setXRot(0.0F);
				purpleDog.setYRot(player.getYRot() + 180.0F);
				purpleDog.yBodyRot = player.getYRot() + 180.0F;
				purpleDog.yHeadRot = player.getYRot() + 180.0F;
				purpleDog.setPerformMode(player.getUUID());
				serverLevel.addFreshEntity(purpleDog);
				purpleDogId = purpleDog.getId();

				PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(purpleMonster.getId(), phase, purpleDogId));
				ctx.getSource().sendSuccess(() -> Component.literal("打开紫怪界面 - 阶段" + phase), true);
			} else if (phase == 5) {
				// 阶段5：只打开GUI，不生成实体（在右侧显示）
				PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(0, phase, 0));
				ctx.getSource().sendSuccess(() -> Component.literal("打开紫怪界面 - 阶段" + phase), true);
			} else if (phase == 1) {
				// 阶段1：紫怪在玩家正前方
				double frontX = -Math.sin(yawRad) * 2.0;
				double frontZ = Math.cos(yawRad) * 2.0;
				double spawnX = player.getX() + frontX;
				double spawnZ = player.getZ() + frontZ;
				double spawnY = player.getY();

				PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), serverLevel);
				purpleMonster.setPos(spawnX, spawnY, spawnZ);
				purpleMonster.setXRot(0.0F);
				purpleMonster.setYRot(player.getYRot() + 180.0F);
				purpleMonster.yBodyRot = player.getYRot() + 180.0F;
				purpleMonster.yHeadRot = player.getYRot() + 180.0F;
				purpleMonster.setPerformMode(player.getUUID());
				// 纹理默认已经是 purpleboy (variant=0)
				serverLevel.addFreshEntity(purpleMonster);

				PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(purpleMonster.getId(), phase, 0));
				ctx.getSource().sendSuccess(() -> Component.literal("打开紫怪界面 - 阶段" + phase), true);
			} else {
				// 阶段2：紫怪在玩家正前方中间
				double frontX = -Math.sin(yawRad) * 2.0;
				double frontZ = Math.cos(yawRad) * 2.0;
				double spawnX = player.getX() + frontX;
				double spawnZ = player.getZ() + frontZ;
				double spawnY = player.getY();

				PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), serverLevel);
				purpleMonster.setPos(spawnX, spawnY, spawnZ);
				purpleMonster.setXRot(0.0F);
				purpleMonster.setYRot(player.getYRot() + 180.0F);
				purpleMonster.yBodyRot = player.getYRot() + 180.0F;
				purpleMonster.yHeadRot = player.getYRot() + 180.0F;
				purpleMonster.setPerformMode(player.getUUID());
				serverLevel.addFreshEntity(purpleMonster);

				PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(purpleMonster.getId(), phase, 0));
				ctx.getSource().sendSuccess(() -> Component.literal("打开紫怪界面 - 阶段" + phase), true);
			}
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
		return 0;
	}
}
