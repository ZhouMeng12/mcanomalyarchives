package net.mcreator.mcanomalyarchives.anomaly.purple;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Validate mixin targets against this build's real Minecraft bytecode without starting a client. */
class PurpleRenderContractTest {
    @Test void firstPersonInjectionTargetsTheActualPrivateHandRenderer() throws Exception {
        boolean[] found = {false};
        new ClassReader("net.minecraft.client.renderer.entity.player.PlayerRenderer").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (name.equals("renderHand") && desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V")) found[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE);
        assertTrue(found[0], "First-person mixin signature no longer matches Minecraft");
    }
    @Test void bodyOverlayRunsAfterTheRealModelDrawBeforeItsTransformIsPopped() throws Exception {
        boolean[] found = {false};
        new ClassReader("net.minecraft.client.renderer.entity.LivingEntityRenderer").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (!name.equals("render") || !desc.equals("(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (owner.equals("net/minecraft/client/model/EntityModel") && method.equals("renderToBuffer")
                                && descriptor.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")) found[0] = true;
                    }
                };
            }
        }, 0);
        assertTrue(found[0], "Third-person mixin draw target no longer matches Minecraft");
    }
}
