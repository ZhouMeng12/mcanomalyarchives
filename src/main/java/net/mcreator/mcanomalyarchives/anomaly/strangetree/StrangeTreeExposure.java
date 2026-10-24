package net.mcreator.mcanomalyarchives.anomaly.strangetree;

/** 根部两层 3×3 共 18 个采样点，至少三分之二暴露才钻地。 */
public final class StrangeTreeExposure {
	public static final int SAMPLES = 18;
	private StrangeTreeExposure() { }
	public static boolean shouldBurrow(int exposed, int sampled) {
		return sampled == SAMPLES && exposed >= 12 && exposed <= sampled;
	}
}
