package net.mcreator.mcanomalyarchives.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.sanity.PlayerSanity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class SanityCommand {

	public static void init() {
		NeoForge.EVENT_BUS.register(new SanityCommand());
	}

	@SubscribeEvent
	public void registerCommands(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

		dispatcher.register(Commands.literal("sanity")
			.requires(source -> source.hasPermission(2))
			.then(Commands.literal("get")
				.executes(ctx -> executeGet(ctx, ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(ctx -> executeGet(ctx, EntityArgument.getPlayer(ctx, "player")))))
			.then(Commands.literal("set")
				.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
					.executes(ctx -> executeSet(ctx, ctx.getSource().getPlayerOrException(),
						IntegerArgumentType.getInteger(ctx, "value")))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> executeSet(ctx, EntityArgument.getPlayer(ctx, "player"),
							IntegerArgumentType.getInteger(ctx, "value"))))))
			.then(Commands.literal("add")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
					.executes(ctx -> executeAdd(ctx, ctx.getSource().getPlayerOrException(),
						IntegerArgumentType.getInteger(ctx, "amount")))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> executeAdd(ctx, EntityArgument.getPlayer(ctx, "player"),
							IntegerArgumentType.getInteger(ctx, "amount"))))))
			.then(Commands.literal("reduce")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
					.executes(ctx -> executeReduce(ctx, ctx.getSource().getPlayerOrException(),
						IntegerArgumentType.getInteger(ctx, "amount")))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> executeReduce(ctx, EntityArgument.getPlayer(ctx, "player"),
							IntegerArgumentType.getInteger(ctx, "amount"))))))
			.then(Commands.literal("reset")
				.executes(ctx -> executeReset(ctx, ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(ctx -> executeReset(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int executeGet(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		int sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).getSanity();
		ctx.getSource().sendSuccess(() -> Component.literal(
			player.getName().getString() + " 的理智值: " + sanity + "/" + PlayerSanity.MAX_SANITY), false);
		return sanity;
	}

	private static int executeSet(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int value) {
		player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).setSanity(value);
		PlayerSanity.syncToClient(player);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"已将 " + player.getName().getString() + " 的理智值设置为 " + value), true);
		return value;
	}

	private static int executeAdd(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int amount) {
		PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
		sanity.increaseSanity(amount);
		PlayerSanity.syncToClient(player);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"已为 " + player.getName().getString() + " 增加 " + amount + " 理智值 (当前: "
				+ sanity.getSanity() + "/" + PlayerSanity.MAX_SANITY + ")"), true);
		return sanity.getSanity();
	}

	private static int executeReduce(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int amount) {
		PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
		sanity.reduceSanity(amount);
		PlayerSanity.syncToClient(player);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"已为 " + player.getName().getString() + " 减少 " + amount + " 理智值 (当前: "
				+ sanity.getSanity() + "/" + PlayerSanity.MAX_SANITY + ")"), true);
		return sanity.getSanity();
	}

	private static int executeReset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).resetToMax();
		PlayerSanity.syncToClient(player);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"已将 " + player.getName().getString() + " 的理智值重置为 " + PlayerSanity.MAX_SANITY), true);
		return PlayerSanity.MAX_SANITY;
	}
}
