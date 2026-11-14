package net.mcreator.mcanomalyarchives.client.earth;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One camera-relative deformation field, shared by terrain and entity vertex shaders. */
public final class EarthTerrainWarp {
    private static boolean worldPass;
    private static float strength;
    public static final Vector3f CENTER = new Vector3f(), UP = new Vector3f(0,1,0);
    private EarthTerrainWarp() { }
    public static void begin(Camera camera, Matrix4f view, float partial) {
        worldPass = true; strength = 0;
        if (!EarthClient.active()) return;
        float age = EarthClient.age(partial);
        strength = net.mcreator.mcanomalyarchives.anomaly.earth.EarthRules.warpStrength(age);
        Vec3 delta = Vec3.atCenterOf(EarthClient.center()).subtract(camera.getPosition());
        view.transformPosition(CENTER.set((float)delta.x, (float)delta.y, (float)delta.z));
        view.transformDirection(UP.set(0,1,0)).normalize();
    }
    public static float strength() { return worldPass ? strength : 0; }
    public static void end() { worldPass = false; strength = 0; }
}
