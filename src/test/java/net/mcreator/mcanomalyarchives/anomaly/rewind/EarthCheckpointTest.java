package net.mcreator.mcanomalyarchives.anomaly.rewind;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EarthCheckpointTest {
    @Test void persistentSnapshotPreservesPreNamingActorsPlayersAndTimeBeyondTenSeconds() {
        UUID actor=UUID.randomUUID(),player=UUID.randomUUID(),vehicle=UUID.randomUUID();
        var animal=new CompoundTag();animal.putString("CustomName","Original");animal.putBoolean("NoAI",false);
        var person=new CompoundTag();person.putFloat("Health",17);var carried=new CompoundTag();carried.putInt("count",3);
        var snapshot=new RewindSnapshot(12000,Map.of(),Map.of(actor,new RewindSnapshot.Actor(9,new Vec3(210,70,-20),animal,vehicle)),
            Map.of(player,new RewindSnapshot.Person("minecraft:overworld",new Vec3(220,71,-21),person,carried)),List.of(),List.of());
        var encoded=EarthCheckpoint.encode(snapshot);
        animal.putString("CustomName","Earth");
        var restored=EarthCheckpoint.decode(encoded);
        assertEquals(12000,restored.tick());
        assertEquals("Original",restored.actors().get(actor).data().getString("CustomName"));
        assertEquals(vehicle,restored.actors().get(actor).vehicle());
        assertEquals(new Vec3(210,70,-20),restored.actors().get(actor).position());
        assertEquals(17,restored.players().get(player).data().getFloat("Health"));
        assertEquals(3,restored.players().get(player).carried().getInt("count"));
    }
}
