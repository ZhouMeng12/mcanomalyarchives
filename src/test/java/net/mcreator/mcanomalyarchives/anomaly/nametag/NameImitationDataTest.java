package net.mcreator.mcanomalyarchives.anomaly.nametag;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NameImitationDataTest {
    private static final String KEY = "mcanomalyarchives_name_mimic_uuid";
    @Test void legacyStringIsMigratedAndCanBeReadAgain() {
        var data = new CompoundTag(); UUID id = UUID.randomUUID();
        data.putString(KEY, id.toString()); data.putInt("unrelated", 42);
        assertEquals(id, NameImitation.readTarget(data));
        assertTrue(data.hasUUID(KEY));
        assertEquals(id, NameImitation.readTarget(data));
        assertEquals(42, data.getInt("unrelated"));
    }
    @Test void absentMalformedOrWrongTypeDoesNotCrashEntityTicks() {
        var data = new CompoundTag(); assertNull(NameImitation.readTarget(data));
        data.putString(KEY, "not-a-uuid"); assertNull(NameImitation.readTarget(data));
        data.putInt(KEY, 9); assertNull(NameImitation.readTarget(data));
    }
}
