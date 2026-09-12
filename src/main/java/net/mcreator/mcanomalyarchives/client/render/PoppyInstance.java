package net.mcreator.mcanomalyarchives.client.render;

import net.minecraft.world.phys.Vec3;

public class PoppyInstance {
    private final Vec3 position;
    private float growthProgress;
    private final float maxGrowthTime;
    private final float totalLifeTime;
    private float age;
    private final float swayOffset;
    private final float randomYaw;

    public PoppyInstance(Vec3 position, float maxGrowthTime, float totalLifeTime) {
        this.position = position;
        this.maxGrowthTime = maxGrowthTime;
        this.totalLifeTime = totalLifeTime;
        this.age = 0;
        this.growthProgress = 0;
        this.swayOffset = (float) (Math.random() * Math.PI * 2);
        this.randomYaw = (float) (Math.random() * 360);
    }

    public void tick() {
        age++;
        if (age < maxGrowthTime) {
            growthProgress = age / maxGrowthTime;
        } else if (age > totalLifeTime) {
            growthProgress = Math.max(0, 1 - (age - totalLifeTime) / 20f);
        }
    }

    public boolean isDead() {
        return age > totalLifeTime + 20;
    }

    public Vec3 getPosition() { return position; }
    public float getGrowthProgress() { return growthProgress; }
    public float getSwayOffset() { return swayOffset; }
    public float getRandomYaw() { return randomYaw; }
}
