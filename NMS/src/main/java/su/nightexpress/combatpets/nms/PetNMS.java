package su.nightexpress.combatpets.nms;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.Template;

import java.util.Set;
import java.util.function.Function;

public interface PetNMS {

    @NotNull Set<EntityType> getSupportedEntities();

    default boolean canSpawn(@NotNull EntityType entityType) {
        return this.getSupportedEntities().contains(entityType);
    }

    default boolean isAllowedInPeaceful(@NotNull EntityType entityType) {
        return true;
    }

    @Nullable
    default LivingEntity createUnspawnedEntity(@NotNull EntityType entityType, @NotNull World world) {
        Class<? extends Entity> entityClass = entityType.getEntityClass();
        if (entityClass == null) return null;

        Entity entity = world.createEntity(world.getSpawnLocation(), entityClass);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    default double getAttributeValue(@NotNull LivingEntity entity, @NotNull Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? -1D : instance.getValue();
    }

    //@Deprecated
    //void damageItem(@NotNull EquipmentSlot[] slots, @NotNull LivingEntity entity, @NotNull DamageSource source, int damage);

    @NotNull
    ActivePet spawnPet(@NotNull Template config, @NotNull Location location, @NotNull Function<LivingEntity, ActivePet> holderFunction);

    default void clearTarget(@NotNull LivingEntity entity) {
        if (entity instanceof Mob mob) mob.setTarget(null);
    }

    void setSaddle(@NotNull LivingEntity entity);

    void sneak(@NotNull LivingEntity entity, boolean value);

    @Deprecated
    void setLeashedTo(@NotNull LivingEntity entity, @Nullable Entity holder);

    boolean hasNavigationPath(@NotNull LivingEntity entity);
}
