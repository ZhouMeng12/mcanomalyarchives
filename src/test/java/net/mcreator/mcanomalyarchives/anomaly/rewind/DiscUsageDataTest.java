package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscUsageDataTest {
    @Test void tenChargesAreIndependentAndSavedOutsideSnapshots() {
        var data = new DiscUsageData();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(0, data.used(first));
        for (int i = 1; i <= 10; i++) {
            data.consume(first);
            assertEquals(i, data.used(first));
        }
        data.consume(first);
        assertEquals(10, data.used(first));
        assertEquals(0, data.used(second));
        assertEquals(10, data.save(new CompoundTag(), null).getInt(first.toString()));
        assertTrue(data.isDirty());
    }
}
