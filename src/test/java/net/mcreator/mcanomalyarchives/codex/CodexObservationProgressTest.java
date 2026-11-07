package net.mcreator.mcanomalyarchives.codex;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CodexObservationProgressTest {
    @Test
    void discoveriesRemainInEncounterOrderAcrossSaveAndSync() {
        String saved = CodexObservations.append("", "purple_stalker");
        saved = CodexObservations.append(saved, "purple_change");
        saved = CodexObservations.append(saved, "garbled_spread");
        assertEquals(List.of("purple_stalker", "purple_change", "garbled_spread"),
                List.copyOf(CodexObservations.decode(saved)));
        assertEquals(saved, String.join(",", CodexObservations.decode(saved)));
    }

    @Test
    void repeatingAnObservationDoesNotAppendOrMoveIt() {
        String saved = "name_absorb,purple_stalker,name_mining";
        assertEquals(saved, CodexObservations.append(saved, "purple_stalker"));
    }

    @Test
    void unknownIdsDoNotExposeOtherProperties() {
        assertEquals(List.of("fishing_far"),
                List.copyOf(CodexObservations.decode("future_property,fishing_far,fishing_far")));
        assertEquals("fishing_far", CodexObservations.append("fishing_far", "future_property"));
        assertEquals(3, CodexObservations.note("fishing_far").entry());
        assertNull(CodexObservations.note("future_property"));
    }
}
