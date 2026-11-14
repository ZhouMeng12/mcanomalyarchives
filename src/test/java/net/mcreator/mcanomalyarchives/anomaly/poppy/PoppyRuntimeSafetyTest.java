package net.mcreator.mcanomalyarchives.anomaly.poppy;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Headless contracts for the two failures reported from the real client. */
class PoppyRuntimeSafetyTest {
    @Test void theRemovedWorldFlowerAndSourceHidingAreNotPackaged() {
        assertNull(getClass().getClassLoader().getResource("net/mcreator/mcanomalyarchives/client/render/PoppyGiantRenderer.class"));
        assertNull(getClass().getClassLoader().getResource("net/mcreator/mcanomalyarchives/mixin/PoppyBlockRenderMixin.class"));
    }
    private List<String> calls(String type,String method) throws Exception {
        List<String> result=new ArrayList<>();
        try(var stream=getClass().getClassLoader().getResourceAsStream(type+".class")) {
            assertNotNull(stream,type);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
                    if(!name.equals(method))return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitInsn(int opcode) { if(opcode==Opcodes.FCONST_0)result.add("zero"); }
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf) {
                            result.add(owner+"."+name+desc);
                        }
                    };
                }
            },0);
        }
        return result;
    }
    @Test void forcedDeathZerosHealthBeforeSendingTheDeathScreen() throws Exception {
        var calls=calls("net/mcreator/mcanomalyarchives/anomaly/poppy/PoppyAddictionSystem","execute");
        int die=-1,health=-1;
        for(int n=0;n<calls.size();n++) {
            if(calls.get(n).endsWith(".setHealth(F)V"))health=n;
            if(calls.get(n).contains(".die("))die=n;
        }
        assertTrue(health>0&&die>health,"Positive-health death screens cannot satisfy vanilla's respawn check");
        assertEquals("zero",calls.get(health-1));
        // The real ServerPlayer implementation does not zero health on behalf of callers.
        assertFalse(calls("net/minecraft/server/level/ServerPlayer","die").stream().anyMatch(c->c.endsWith(".setHealth(F)V")));
    }
    @Test void postProcessingNeverClearsOrCopiesMainDepth() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("assets/mcanomalyarchives/shaders/post/poppy.json")) {
            assertNotNull(stream);
            var json=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var passes=json.getAsJsonArray("passes");
            assertFalse(passes.isEmpty());
            for(var pass:passes)assertNotEquals("minecraft:main",pass.getAsJsonObject().get("outtarget").getAsString());
            assertEquals("poppy_swap",passes.get(passes.size()-1).getAsJsonObject().get("outtarget").getAsString());
        }
        var render=calls("net/mcreator/mcanomalyarchives/client/render/PoppyPostEffect","render");
        assertFalse(render.stream().anyMatch(c->c.contains(".copyDepthFrom(")));
        assertTrue(render.stream().anyMatch(c->c.endsWith(".blitToScreen(IIZ)V")));
        // Check the actual dependency's color compositor, rather than assuming it preserves depth.
        var compositor=calls("com/mojang/blaze3d/pipeline/RenderTarget","_blitToScreen");
        assertFalse(compositor.stream().anyMatch(c->c.contains("._clear(")||c.contains("._glBlitFrameBuffer(")));
    }
}
