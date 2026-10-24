package net.mcreator.mcanomalyarchives.anomaly.purple;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SharedStalkerSessionTest {
    private final SharedStalkerSession<String> session = new SharedStalkerSession<>();
    private final UUID actor = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @Test
    void unrelatedPlayerLeavingKeepsSharedEncounter() {
        session.open(actor, owner, "stalker");
        assertNull(session.closeForOwner(UUID.randomUUID()));
        assertEquals("stalker", session.current());
    }

    @Test
    void ownerLeavingReturnsActorForCleanupAndReleasesSlot() {
        session.open(actor, owner, "stalker");
        assertEquals("stalker", session.closeForOwner(owner));
        assertNull(session.current());
        assertTrue(session.open(UUID.randomUUID(), UUID.randomUUID(), "next"));
    }

    @Test
    void twoPlayersCannotOpenTwoSharedEncounters() {
        assertTrue(session.open(actor, owner, "first"));
        assertFalse(session.open(UUID.randomUUID(), UUID.randomUUID(), "second"));
        assertEquals("first", session.current());
    }

    @Test
    void unrelatedEntityUnloadingDoesNotReleaseSlot() {
        session.open(actor, owner, "stalker");
        session.leave(UUID.randomUUID());
        assertEquals("stalker", session.current());
        session.leave(actor);
        assertNull(session.current());
    }

    @Test
    void rejoiningSameActorUpdatesReferenceWithoutOpeningSecondEncounter() {
        session.open(actor, owner, "old");
        assertTrue(session.open(actor, owner, "loaded"));
        assertEquals("loaded", session.closeForOwner(owner));
    }

    @Test
    void stopDropsPreviousServerOwnership() {
        session.open(actor, owner, "stalker");
        session.clear();
        assertNull(session.closeForOwner(owner));
        assertTrue(session.open(UUID.randomUUID(), UUID.randomUUID(), "new server"));
    }
}
