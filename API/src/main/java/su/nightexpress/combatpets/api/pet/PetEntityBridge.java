package su.nightexpress.combatpets.api.pet;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PetEntityBridge {

    /** Active pets can be queried from multiple Folia region threads. */
    public static final ConcurrentMap<PetEntity, ActivePet> BY_PET = new ConcurrentHashMap<>();
    public static final ConcurrentMap<UUID, ActivePet>      BY_ID  = new ConcurrentHashMap<>();

    public static ActivePet getByPet(@NotNull PetEntity entity) {
        return BY_PET.get(entity);
    }

    public static boolean isPet(@NotNull LivingEntity entity) {
        return getByMob(entity) != null;
    }

    @Nullable
    public static ActivePet getByMob(@NotNull LivingEntity entity) {
        if (entity.getType() == EntityType.PLAYER) {
            return null;
        }
        return getByMobId(entity.getUniqueId());
    }

    @Nullable
    public static ActivePet getByPlayer(@NotNull Player player) {
        return BY_ID.get(player.getUniqueId());
    }

    @Nullable
    public static ActivePet getByMobId(@NotNull UUID uuid) {
        return BY_ID.get(uuid);
    }

    @NotNull
    public static Collection<ActivePet> getAll() {
        return java.util.Set.copyOf(BY_PET.values());
    }

    public static void addHolder(@NotNull PetEntity entity, @NotNull ActivePet holder) {
        BY_PET.put(entity, holder);
        BY_ID.put(holder.getOwner().getUniqueId(), holder);
        BY_ID.put(holder.getEntity().getUniqueId(), holder);
    }

    /*public static void removeHolder(@NotNull PetEntity entity) {
        IPetHolder holder = BY_ENTITY.remove(entity);
        if (holder == null) return;

        BY_ID.remove(holder.getEntity().getUniqueId());
        BY_ID.remove(holder.getOwner().getUniqueId());
    }*/

    public static void removeHolder(@NotNull ActivePet holder) {
        BY_PET.entrySet().removeIf(entry -> entry.getValue() == holder);
        BY_ID.remove(holder.getEntity().getUniqueId(), holder);
        BY_ID.remove(holder.getOwner().getUniqueId(), holder);
    }

    /** Removes a retired pet without reading either retired entity. */
    public static void removeRetiredHolder(@NotNull ActivePet holder) {
        BY_PET.entrySet().removeIf(entry -> entry.getValue() == holder);
        BY_ID.entrySet().removeIf(entry -> entry.getValue() == holder);
    }
}
