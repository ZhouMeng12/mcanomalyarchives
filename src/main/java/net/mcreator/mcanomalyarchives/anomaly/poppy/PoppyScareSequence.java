package net.mcreator.mcanomalyarchives.anomaly.poppy;

import java.util.List;

/** Fixed compositions on a 1280 x 720 reference canvas; cuts never interpolate scale. */
public final class PoppyScareSequence {
    public static final float WARNING_CUT = .55f;
    public enum Stage { NONE, MOUTH, WARNINGS }
    public record Card(int x, int y, int size) {}
    public static final List<Card> CARDS = List.of(new Card(74,-84,412),new Card(28,244,618),new Card(705,83,538));
    private PoppyScareSequence() {}
    public static Stage stage(float progress) {
        return !(progress > 0) ? Stage.NONE : progress < WARNING_CUT ? Stage.MOUTH : Stage.WARNINGS;
    }
    public static int glitchColor(int x,int y) {
        int hash=(x*73856093)^(y*19349663)^150003;
        hash^=hash>>>13;
        return switch(Math.floorMod(hash,16)) {
            case 0,1,2 -> 0xff0a0806;
            case 3,4,5 -> 0xffef1d09;
            case 6 -> 0xffc6b57c;
            case 7 -> 0xfff5e64c;
            case 8 -> 0xff6d7564;
            default -> 0xfffffff2;
        };
    }
}
