package net.mcreator.mcanomalyarchives.anomaly.rewind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RewindLifeStateTest {
    @Test void liveSnapshotClearsDeathAnimationWithoutChangingItsHealthOrOriginalNbt() {
        var old=new CompoundTag();old.putFloat("Health",7);old.putShort("DeathTime",(short)18);old.putShort("HurtTime",(short)4);
        var restored=RewindLifeState.forRestore(old);
        assertTrue(RewindLifeState.wasAlive(restored));assertEquals(7,restored.getFloat("Health"));
        assertEquals(0,restored.getShort("DeathTime"));assertEquals(0,restored.getShort("HurtTime"));assertEquals(18,old.getShort("DeathTime"));
    }
    @Test void DeadHistoricalSnapshotDoesNotGetResurrected() {
        var old=new CompoundTag();old.putFloat("Health",0);old.putShort("DeathTime",(short)9);
        assertFalse(RewindLifeState.wasAlive(old));assertEquals(9,RewindLifeState.forRestore(old).getShort("DeathTime"));
        assertFalse(RewindLifeState.wasAlive(new CompoundTag()));
    }
}
