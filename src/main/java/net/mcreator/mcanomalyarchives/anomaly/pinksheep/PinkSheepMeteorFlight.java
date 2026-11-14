package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.minecraft.world.phys.Vec3;

/** Immutable destination: the player's subsequent movement cannot steer this flight. */
public record PinkSheepMeteorFlight(Vec3 start, Vec3 target, int duration) {
	public PinkSheepMeteorFlight {
		if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
	}
	public Vec3 position(long elapsed) {
		double t = Math.max(0, Math.min(1, (double) elapsed / duration));
		return start.lerp(target, t * t);
	}
	public Vec3 velocity(Vec3 current, long elapsed) { return position(elapsed + 1).subtract(current); }
}
