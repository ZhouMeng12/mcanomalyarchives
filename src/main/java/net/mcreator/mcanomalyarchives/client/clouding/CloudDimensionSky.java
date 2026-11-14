package net.mcreator.mcanomalyarchives.client.clouding;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Stable world-space cloud field, purely visual: no persistent mobs or terrain damage. */
@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class CloudDimensionSky {
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !CloudDimensionVisuals.active()) return;
        var mc = Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        double time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double drift = time * .04;
        int cx = (int)Math.floor((camera.x - drift) / 64), cz = (int)Math.floor(camera.z / 64);
        double distance = Math.min(224, mc.options.getEffectiveRenderDistance() * 16.0);
        var buffers = mc.renderBuffers().bufferSource();
        var type = RenderType.debugQuads();
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            // Keep vertex coordinates camera-relative to retain precision far from spawn.
            Matrix4f matrix = pose.last().pose();
            VertexConsumer out = buffers.getBuffer(type);
            for (int layer = 0; layer < 3; layer++) {
                for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 4; z <= cz + 4; z++) {
                    long hash = mix(x * 341873128712L + z * 132897987541L + layer * 6571L);
                    if ((hash & 3) == 0) continue;
                    double px = x * 64.0 + ((hash >>> 4) & 15) + drift;
                    double pz = z * 64.0 + ((hash >>> 8) & 15);
                    double y = 128 + layer * 48 + ((hash >>> 12) & 15);
                    double length = 24 + ((hash >>> 16) & 31), depth = 14 + ((hash >>> 21) & 15);
                    var box = new AABB(px, y, pz, px + length, y + 4, pz + depth);
                    double dx = box.getCenter().x - camera.x, dz = box.getCenter().z - camera.z;
                    double radius = Math.sqrt(dx * dx + dz * dz);
                    if (radius > distance || !event.getFrustum().isVisible(box)) continue;
                    float alpha = (float)Math.min(1, (distance - radius) / 32);
                    box(out, matrix, box.move(-camera.x, -camera.y, -camera.z), alpha);
                }
            }
            buffers.endBatch(type);
        } finally { pose.popPose(); }
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static void box(VertexConsumer out, Matrix4f m, AABB b, float alpha) {
        float x = (float)b.minX, y = (float)b.minY, z = (float)b.minZ;
        float X = (float)b.maxX, Y = (float)b.maxY, Z = (float)b.maxZ;
        face(out,m,alpha,x,y,z,X,y,z,X,y,Z,x,y,Z);
        face(out,m,alpha,x,Y,z,x,Y,Z,X,Y,Z,X,Y,z);
        face(out,m,alpha,x,y,z,x,Y,z,X,Y,z,X,y,z);
        face(out,m,alpha,x,y,Z,X,y,Z,X,Y,Z,x,Y,Z);
        face(out,m,alpha,x,y,z,x,y,Z,x,Y,Z,x,Y,z);
        face(out,m,alpha,X,y,z,X,Y,z,X,Y,Z,X,y,Z);
    }

    private static void face(VertexConsumer out, Matrix4f matrix, float alpha, float... coords) {
        for (int i=0;i<12;i+=3) out.addVertex(matrix,coords[i],coords[i+1],coords[i+2]).setColor(.004f,.004f,.004f,alpha);
    }
}
