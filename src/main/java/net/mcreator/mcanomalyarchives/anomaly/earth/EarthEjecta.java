package net.mcreator.mcanomalyarchives.anomaly.earth;

/** Deterministic ballistic debris: historical age also reverses its path during rewind. */
public final class EarthEjecta {
    public static final int COUNT = 112, LIFETIME = 180;
    public record Piece(double x, double y, double z, float size, float rotation) { }
    private EarthEjecta() { }
    public static Piece at(int index, float ticks) {
        double angle = index * 2.399963229728653;
        double speed = 18 + (index * 37 % 47);
        double lift = 24 + (index * 17 % 25);
        double time = Math.max(0, ticks) / 20.0;
        double radius = 3 + index % 11 + speed * time;
        float fade = Math.max(0, Math.min(1, (LIFETIME - ticks) / 25));
        return new Piece(Math.cos(angle) * radius, 2 + lift * time - 4.9 * time * time,
            Math.sin(angle) * radius, (2.5f + (index % 9) * .65f) * fade, ticks * (.035f + index % 5 * .009f));
    }
}
