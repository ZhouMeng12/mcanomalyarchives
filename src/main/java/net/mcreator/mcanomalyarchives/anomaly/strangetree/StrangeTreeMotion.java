package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.function.ToDoubleFunction;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;

/** Five-tick movement budget, sampled across the swept trunk rather than the surface overhead. */
final class StrangeTreeMotion {
	private StrangeTreeMotion() { }

	static double step(double x, double y, double z, double ux, double uz, double maximum,
			ToDoubleFunction<Point> speed) {
		double limit = maximum;
		int samples = Math.max(1, (int) Math.ceil(maximum / 0.05));
		for (int sample = 0; sample <= samples; sample++) {
			double travel = maximum * sample / samples;
			if (travel > limit) break;
			for (int dy = 0; dy < 5; dy++) {
				for (double ox : new double[] {-0.49, 0.49}) {
					for (double oz : new double[] {-0.49, 0.49}) {
						Point point = new Point((int) Math.floor(x + ux * travel + ox),
								(int) Math.floor(y + dy), (int) Math.floor(z + uz * travel + oz));
						double material = speed.applyAsDouble(point);
						if (material <= 0) return Math.min(limit, Math.max(0, travel - maximum / samples));
						limit = Math.min(limit, material / 4);
					}
				}
			}
		}
		return limit;
	}
}
