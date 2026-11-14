package net.mcreator.mcanomalyarchives.anomaly.earth;

import net.mcreator.mcanomalyarchives.client.earth.EarthShaderSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarthShaderSourceTest {
    private static final String TERRAIN = "#version 150\n#moj_import <fog.glsl>\nvoid main() {\nvec3 pos = Position + ChunkOffset;\n gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);\nvertexDistance = fog_distance(pos, FogShape);\n}";
    @Test void patchesTerrainAfterModelViewAndBeforeProjection() {
        String patched = EarthShaderSource.patch(true, "minecraft:rendertype_solid", TERRAIN);
        assertTrue(patched.startsWith("#version 150\n#moj_import <fog.glsl>"));
        assertTrue(patched.contains("ProjMat * mcaEarthWarp(ModelViewMat * vec4(pos, 1.0))"));
        assertTrue(patched.indexOf("vec4 mcaEarthWarp") < patched.indexOf("void main()"));
        assertTrue(patched.contains("vertexDistance = fog_distance(pos, FogShape)"));
    }
    @Test void entityAndTerrainUseTheSameViewSpaceField() {
        String entity = TERRAIN.replace("vec4(pos, 1.0)", "vec4(Position, 1.0)");
        String patched = EarthShaderSource.patch(true, "rendertype_entity_cutout", entity);
        assertTrue(patched.contains("mcaEarthWarp(ModelViewMat * vec4(Position, 1.0))"));
        assertTrue(patched.contains("McaEarthUp"));
    }
    @Test void doesNotTouchFragmentsUnknownShadersOrGuiShaders() {
        assertEquals(TERRAIN, EarthShaderSource.patch(false, "rendertype_solid", TERRAIN));
        assertEquals(TERRAIN, EarthShaderSource.patch(true, "rendertype_gui", TERRAIN));
        assertEquals(TERRAIN, EarthShaderSource.patch(true, "anothermod:rendertype_solid", TERRAIN));
        assertEquals("void main() {}", EarthShaderSource.patch(true, "rendertype_solid", "void main() {}"));
    }
    @Test void patchIsIdempotentAcrossResourceReloads() {
        String once = EarthShaderSource.patch(true, "rendertype_translucent", TERRAIN);
        assertEquals(once, EarthShaderSource.patch(true, "rendertype_translucent", once));
    }
}
