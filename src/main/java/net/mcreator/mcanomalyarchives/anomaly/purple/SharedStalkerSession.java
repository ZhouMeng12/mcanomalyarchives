package net.mcreator.mcanomalyarchives.anomaly.purple;

import java.util.Objects;
import java.util.UUID;

/** 全服只有一个已加载的窥视遭遇；离场清理按关联玩家匹配。 */
public final class SharedStalkerSession<T> {
    private UUID entityId;
    private UUID owner;
    private T actor;

    public boolean open(UUID id, UUID owner, T actor) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(actor);
        if (this.actor != null && !id.equals(entityId)) return false;
        this.entityId = id;
        this.owner = owner;
        this.actor = actor;
        return true;
    }

    public T current() {
        return actor;
    }

    public T closeForOwner(UUID player) {
        if (owner == null || !owner.equals(player)) return null;
        T old = actor;
        clear();
        return old;
    }

    public void leave(UUID id) {
        if (id.equals(entityId)) clear();
    }

    public void clear() {
        entityId = null;
        owner = null;
        actor = null;
    }
}
