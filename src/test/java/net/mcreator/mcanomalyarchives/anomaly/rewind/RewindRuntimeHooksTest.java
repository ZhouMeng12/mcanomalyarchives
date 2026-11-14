package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class RewindRuntimeHooksTest {
    private ClassReader read(String name) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name); return new ClassReader(stream);
        }
    }
    @Test void allRewindInjectionSelectorsAndShadowsExistInMinecraft() throws Exception {
        Map<String, String> targets = Map.of(
            "RewindChunkMixin", "world/level/chunk/LevelChunk",
            "RewindJukeboxMixin", "world/level/block/entity/JukeboxBlockEntity",
            "RewindBlockEntityTickMixin", "world/level/chunk/LevelChunk$BoundTickingBlockEntity",
            "RewindScheduledTickMixin", "server/level/ServerLevel",
            "RewindRandomTickMixin", "world/level/block/state/BlockBehaviour$BlockStateBase",
            "RewindPlayerPacketMixin", "server/network/ServerGamePacketListenerImpl",
            "RewindNeighborMixin", "world/level/redstone/CollectingNeighborUpdater",
            "RewindParticleMixin", "client/particle/ParticleEngine",
            "RewindSoundMixin", "client/sounds/SoundEngine"
        );
        for (var entry : targets.entrySet()) {
            Set<String> selectors = new HashSet<>(), shadows = new HashSet<>(), methods = new HashSet<>(), fields = new HashSet<>();
            read("net/mcreator/mcanomalyarchives/mixin/" + entry.getKey()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) {
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (annotation.endsWith("/Shadow;")) shadows.add(n + d);
                            return null;
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!annotation.endsWith("/Inject;")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public AnnotationVisitor visitArray(String name) {
                                    if (!name.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String n, Object value) { selectors.add((String)value); }
                                    };
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
            read("net/minecraft/" + entry.getValue()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) { methods.add(n); methods.add(n + d); return null; }
                @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) { fields.add(n + d); return null; }
            }, ClassReader.SKIP_CODE);
            assertFalse(selectors.isEmpty(), entry.getKey());
            assertTrue(methods.containsAll(selectors), entry.getKey() + " missing " + selectors);
            assertTrue(fields.containsAll(shadows), entry.getKey() + " missing shadows " + shadows);
        }
    }
}
