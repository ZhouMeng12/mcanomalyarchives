package net.mcreator.mcanomalyarchives.anomaly.rewind;
import net.minecraft.nbt.CompoundTag;
/** Only an actually living historical snapshot may be revived. Never mutate recorded NBT. */
public final class RewindLifeState {
    private RewindLifeState() { }
    public static boolean wasAlive(CompoundTag tag) { return tag.contains("Health",99) && tag.getFloat("Health")>0; }
    public static CompoundTag forRestore(CompoundTag historical) {
        CompoundTag copy=historical.copy();
        if(wasAlive(copy)) { copy.putShort("DeathTime",(short)0); copy.putShort("HurtTime",(short)0); }
        return copy;
    }
}
