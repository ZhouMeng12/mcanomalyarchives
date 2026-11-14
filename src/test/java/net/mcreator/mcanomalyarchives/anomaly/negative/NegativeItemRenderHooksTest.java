package net.mcreator.mcanomalyarchives.anomaly.negative;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class NegativeItemRenderHooksTest {
    private ClassReader read(String name)throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream(name+".class")) {
            assertNotNull(stream,name);return new ClassReader(stream);
        }
    }
    @Test void signedCountsTargetTheRealNativeDecorationMethod()throws Exception {
        Set<String> selectors=new HashSet<>(),methods=new HashSet<>();
        read("net/mcreator/mcanomalyarchives/mixin/NegativeItemCountMixin").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) {
                        if(!desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public AnnotationVisitor visitArray(String name) {
                                if(!name.equals("method"))return null;
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override public void visit(String name,Object value){selectors.add((String)value);}
                                };
                            }
                        };
                    }
                };
            }
        },ClassReader.SKIP_CODE);
        read("net/minecraft/client/gui/GuiGraphics").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
                methods.add(name+desc);return null;
            }
        },ClassReader.SKIP_CODE);
        assertFalse(selectors.isEmpty());assertTrue(methods.containsAll(selectors));
    }
    @Test void negativeToolsUseTheNativeDurabilityBarAndTooltip()throws Exception {
        Set<String> overrides=new HashSet<>();
        read("net/mcreator/mcanomalyarchives/item/NegativeDiamondPickaxeItem").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
                overrides.add(name);return null;
            }
        },ClassReader.SKIP_CODE);
        assertTrue(overrides.contains("getDamage"));
        assertFalse(overrides.contains("getBarWidth"));assertFalse(overrides.contains("getBarColor"));
        assertFalse(overrides.contains("appendHoverText"));
        assertNull(getClass().getClassLoader().getResource("net/mcreator/mcanomalyarchives/client/NegativeAppleCountDecorator.class"));
    }
}
