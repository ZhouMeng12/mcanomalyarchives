package net.mcreator.mcanomalyarchives.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
			if (!PurpleStoryModule.startDebug(player, phase)) {
				ctx.getSource().sendFailure(Component.literal("当前无法开始紫怪会话"));
				return 0;
			}
			ctx.getSource().sendSuccess(() -> Component.literal("打开紫怪界面 - 阶段" + phase), true);
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
		return 0;
	}
}
