package net.mcreator.mcanomalyarchives.anomaly.nametag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.item.UO002GarbledItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Slowly infects one non-empty stack at a time while a GUI containing a UO-002 fragment stays open. */
@EventBusSubscriber
public final class GarbledInventoryInfection {
	private static final long OPEN_LEASE_TICKS = 40L;
	private static final long INITIAL_INFECTION_DELAY_TICKS = 100L;
	private static final long INFECTION_INTERVAL_TICKS = 100L;
	private static final Map<UUID, Long> OPEN_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> NEXT_INFECTION = new HashMap<>();

	private GarbledInventoryInfection() {
	}

	public static void setGuiOpen(ServerPlayer player, boolean open) {
		UUID id = player.getUUID();
		long now = player.serverLevel().getGameTime();
		if (open) {
			OPEN_UNTIL.put(id, now + OPEN_LEASE_TICKS);
			NEXT_INFECTION.putIfAbsent(id, now + INITIAL_INFECTION_DELAY_TICKS);
		} else {
			OPEN_UNTIL.remove(id);
			NEXT_INFECTION.remove(id);
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
		UUID id = player.getUUID();
		long now = player.serverLevel().getGameTime();
		Long openUntil = OPEN_UNTIL.get(id);
		if (openUntil == null) return;
		if (now > openUntil) {
			OPEN_UNTIL.remove(id);
			NEXT_INFECTION.remove(id);
			return;
		}

		Long next = NEXT_INFECTION.get(id);
		if (next != null && now < next) return;

		AbstractContainerMenu menu = player.containerMenu;
		boolean containsSource = false;
		for (Slot slot : menu.slots) {
			if (slot.hasItem() && slot.getItem().is(McanomalyarchivesModItems.UO002_GARBLED.get())) {
				containsSource = true;
				break;
			}
		}
		if (!containsSource) return;

		for (Slot slot : menu.slots) {
			if (!slot.hasItem() || slot.getItem().is(McanomalyarchivesModItems.UO002_GARBLED.get())) continue;
			ItemStack current = slot.getItem();
			slot.set(UO002GarbledItem.createRandomStack(current.getCount()));
			slot.setChanged();
			menu.broadcastChanges();
			net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "garbled_spread");
			NEXT_INFECTION.put(id, now + INFECTION_INTERVAL_TICKS);
			return;
		}
		NEXT_INFECTION.put(id, now + INFECTION_INTERVAL_TICKS);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		OPEN_UNTIL.remove(id);
		NEXT_INFECTION.remove(id);
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		OPEN_UNTIL.clear();
		NEXT_INFECTION.clear();
	}
}
