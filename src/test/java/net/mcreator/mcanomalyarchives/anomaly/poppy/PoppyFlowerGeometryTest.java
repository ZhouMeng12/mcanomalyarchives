package net.mcreator.mcanomalyarchives.anomaly.poppy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoppyFlowerGeometryTest {
    @Test void bendingStemKeepsFiniteVerticesAndABoundedFaceBudgetThroughGrowth() {
        for (float progress=1;progress<=10;progress+=.125f) {
            var mesh=PoppyFlowerGeometry.build(progress);
            assertTrue(mesh.triangles().size()<1400);
            for (var f:mesh.triangles()) for (var p:new PoppyFlowerGeometry.Point[]{f.a(),f.b(),f.c()}) {
                assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                assertTrue(p.y()>=0 && p.y()<8);
            }
        }
    }
    @Test void theToothedArtworkOnlyReplacesTheLateFlowerHead() {
        assertEquals(0,paintedFaces(3));
        assertEquals(0,paintedFaces(6));
        assertTrue(paintedFaces(9)>0);
    }
    @Test void thePaintedHeadHasSideWallsAndABackRatherThanFloatingAsAFlatCard() {
        var mesh=PoppyFlowerGeometry.build(9);
        double front=mesh.triangles().stream().filter(f->f.material()==PoppyFlowerGeometry.PAINTED_HEAD).findFirst().orElseThrow().a().z();
        assertTrue(mesh.triangles().stream().anyMatch(f->f.material()==PoppyFlowerGeometry.PETAL&&f.a().z()<front-.2));
    }
    private long paintedFaces(float progress) {
        return PoppyFlowerGeometry.build(progress).triangles().stream().filter(f->f.material()==PoppyFlowerGeometry.PAINTED_HEAD).count();
    }
}
