package net.mcreator.mcanomalyarchives.anomaly.rewind;
/** Preview progress cannot finish before both the six-second sequence and terrain restoration. */
public final class EarthRecallVisual {
    private EarthRecallVisual() { }
    public static float progress(int age,int initial,int remaining) {
        float time=Math.max(0,Math.min(1,(age-20)/100f));
        float terrain=initial<=0?1:Math.max(0,Math.min(1,1-remaining/(float)initial));
        return Math.min(time,terrain);
    }
    public static int packetAge(int age,float progress) {
        return age<20?Math.max(0,age/2):10+(int)(40*Math.max(0,Math.min(1,progress)));
    }
}
