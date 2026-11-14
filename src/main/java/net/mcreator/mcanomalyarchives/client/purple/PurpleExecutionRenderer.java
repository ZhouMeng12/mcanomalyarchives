package net.mcreator.mcanomalyarchives.client.purple;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleExecutionSequence;

/** Three tapered, curved limbs attached to the actor and the actual victim. */
public final class PurpleExecutionRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "mcanomalyarchives", "textures/entities/purple.png");
    private PurpleExecutionRenderer() { }
    public static void render(PurpleMonsterEntity actor, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        var target = actor.level().getEntity(actor.executionTarget());
        if (actor.executionTarget() == 0 || target == null) return;
        double age = actor.executionTicks() + partial;
        double extension = PurpleExecutionSequence.extension(age);
        if (extension <= 0) return;
        Vec3 end = target.getPosition(partial).add(0, target.getBbHeight() * .65, 0).subtract(actor.getPosition(partial));
        if (end.lengthSqr() > 256) return;
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int limb = 0; limb < 3; limb++) {
            Vec3 start = new Vec3((limb - 1) * .3, 1.2 + limb * .15, 0);
            Vec3 previous = start;
            for (int i = 1; i <= 12; i++) {
                double t = i / 12.0 * extension;
                double curve = Math.sin(t * Math.PI) * (.22 + limb * .08);
                Vec3 next = start.lerp(end, t).add(Math.cos(limb * 2.1) * curve,
                        Math.sin(limb * 2.1 + age * .06) * curve, Math.sin(limb * 2.1) * curve);
                segment(poses.last(), consumer, previous, next, .085 * (1 - i / 15.0), light);
                previous = next;
            }
        }
    }
    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, double radius, int light) {
        Vec3 direction = b.subtract(a).normalize();
        Vec3 side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize().scale(radius);
        Vec3 up = direction.cross(side).normalize().scale(radius);
        Vec3[] points = {a.add(side).add(up), a.subtract(side).add(up), a.subtract(side).subtract(up), a.add(side).subtract(up),
                b.add(side).add(up), b.subtract(side).add(up), b.subtract(side).subtract(up), b.add(side).subtract(up)};
        int[][] faces = {{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{4,5,6,7},{3,2,1,0}};
        for (int[] face : faces) {
            Vec3 normal = points[face[1]].subtract(points[face[0]]).cross(points[face[2]].subtract(points[face[0]])).normalize();
            for (int i = 0; i < 4; i++) {
                Vec3 p = points[face[i]];
                buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(190, 115, 230, 255)
                        .setUv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
            }
        }
    }
}
