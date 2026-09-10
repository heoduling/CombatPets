package su.nightexpress.combatpets.nms.mc_26_2;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.nms.PetNMS;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetLookAtOwnerGoal;
import su.nightexpress.nightcore.util.Reflex;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Function;

public class MC_26_2 implements PetNMS {

    private final Field despawnTimeField;

    public MC_26_2() {
        this.despawnTimeField = resolveDespawnTimeField();
        EntityInjector.register();
    }

    @NotNull
    private static Field resolveDespawnTimeField() {
        try {
            Field field = net.minecraft.world.entity.Entity.class.getDeclaredField("despawnTime");
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("Entity despawnTime field is not accessible on this server build.");
            }
            return field;
        }
        catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Entity despawnTime field is missing on this server build.", exception);
        }
    }

    private void disableTimedDespawn(@NotNull net.minecraft.world.entity.Entity entity) {
        try {
            if (this.despawnTimeField.getInt(entity) < 0) return;

            this.despawnTimeField.setInt(entity, -1);
            if (this.despawnTimeField.getInt(entity) >= 0) {
                throw new IllegalStateException("Timed despawn remained enabled for pet entity " + entity.getType());
            }
        }
        catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to disable timed despawn for pet entity " + entity.getType(), exception);
        }
    }

    @Override
    @NotNull
    public Set<EntityType> getSupportedEntities() {
        return EntityInjector.getTypes();
    }

    @Override
    public boolean isAllowedInPeaceful(@NotNull EntityType entityType) {
        return EntityTypes.get(entityType.getKey().getKey()).isAllowedInPeaceful();
    }

    @Override
    @NotNull
    public LivingEntity createUnspawnedEntity(@NotNull EntityType entityType, @NotNull World world) {
        Mob mob = EntityInjector.spawn(entityType, ((CraftWorld) world).getHandle());
        return (LivingEntity) mob.getBukkitEntity();
    }

    @Override
    public double getAttributeValue(@NotNull LivingEntity entity, @NotNull org.bukkit.attribute.Attribute attribute) {
        // Config generation uses an entity that is deliberately not registered
        // in a world, so it has no Folia owning region. getHandleRaw avoids the
        // CraftEntity ownership check and is safe here because this object is
        // private to the current enable call and cannot be ticking elsewhere.
        net.minecraft.world.entity.LivingEntity handle = this.getRawHandle((CraftEntity) entity);
        String attributeKey = attribute.getKey().toString();
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.keySet().stream()
            .filter(key -> key.toString().equals(attributeKey))
            .findFirst()
            .flatMap(BuiltInRegistries.ATTRIBUTE::get)
            .orElse(null);
        if (holder == null) return -1D;

        AttributeMap attributes = handle.getAttributes();
        return attributes.hasAttribute(holder) ? attributes.getValue(holder) : -1D;
    }

    @NotNull
    private net.minecraft.world.entity.LivingEntity getRawHandle(@NotNull CraftEntity entity) {
        try {
            Method method = CraftEntity.class.getMethod("getHandleRaw");
            return (net.minecraft.world.entity.LivingEntity) method.invoke(entity);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access an unregistered pet entity during config generation.", exception);
        }
    }

    @Override
    @NotNull
    public ActivePet spawnPet(@NotNull Template template, @NotNull Location location, @NotNull Function<LivingEntity, ActivePet> holderFunction) {
        World bukkitWorld = location.getWorld();
        if (bukkitWorld == null) {
            throw new IllegalStateException("World can not be null!");
        }

        EntityType type = template.getEntityType();
        ServerLevel level = ((CraftWorld) bukkitWorld).getHandle();
        Mob mob = EntityInjector.spawn(type, level);
        this.disableTimedDespawn(mob);
        PetEntity petEntity = (PetEntity) mob;

        mob.getGoalSelector().removeAllGoals(goal -> true);
        this.getTargetSelector(mob).removeAllGoals(goal -> true);

        // Register all attributes. They will be updated with actual values on spawn.
        BuiltInRegistries.ATTRIBUTE.keySet().forEach(resourceLocation -> {
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(resourceLocation).orElse(null);
            if (holder == null) return;

            this.registerAttribute(mob, holder);
        });

        LivingEntity bukkitEntity = (LivingEntity) mob.getBukkitEntity();
        ActivePet holder = holderFunction.apply(bukkitEntity);
        PetEntityBridge.addHolder(petEntity, holder);

        try {
            petEntity.setGoals();
            mob.getGoalSelector().addGoal(8, new PetLookAtOwnerGoal(mob));
            mob.getGoalSelector().addGoal(9, new RandomLookAroundGoal(mob));

            mob.snapTo(location.getX(), location.getY(), location.getZ());
            // EntityLookup assigns a Folia region from the entity's current
            // block position.  Registering first leaves a newly-created entity
            // indexed at (0, 0, 0) and then moves it across regions during the
            // same call.  Set the final position before registration so the
            // lookup and the entity start in the same owning region.
            if (!level.addFreshEntity(mob, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
                throw new IllegalStateException("Pet entity was rejected by the world: " + type);
            }

            return holder;
        }
        catch (RuntimeException | Error exception) {
            // addHolder runs before goal construction because pet goals may
            // query their owner. Do not leave a failed, unspawned pet indexed
            // as the player's active pet when goal setup or world insertion fails.
            PetEntityBridge.removeHolder(holder);
            mob.discard();
            throw exception;
        }
    }

    @Override
    public void clearTarget(@NotNull LivingEntity entity) {
        if (!(entity instanceof CraftMob craftMob)) {
            PetNMS.super.clearTarget(entity);
            return;
        }

        Mob mob = craftMob.getHandle();
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        mob.getBrain().eraseMemory(MemoryModuleType.UNIVERSAL_ANGER);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        mob.getBrain().eraseMemory(MemoryModuleType.PATH);
        mob.getNavigation().stop();
    }

    @NotNull
    private GoalSelector getTargetSelector(@NotNull Mob mob) {
        GoalSelector selector = (GoalSelector) Reflex.getFieldValue(mob, "targetSelector");
        if (selector == null) {
            throw new IllegalStateException("Unable to access Mob target selector on this server version.");
        }
        return selector;
    }

    @Override
    public void setSaddle(@NotNull LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        if (entity.getType() == EntityType.HAPPY_GHAST) {
            equipment.setItem(CraftEquipmentSlot.getSlot(EquipmentSlot.BODY), new org.bukkit.inventory.ItemStack(Material.WHITE_HARNESS));
            return;
        }

        equipment.setItem(CraftEquipmentSlot.getSlot(EquipmentSlot.SADDLE), new org.bukkit.inventory.ItemStack(Material.SADDLE));
    }

    private void registerAttribute(@NotNull net.minecraft.world.entity.LivingEntity handle, @NotNull Holder<Attribute> holder) {
        AttributeMap attributes = handle.getAttributes();
        if (handle.getAttribute(holder) != null) return;

        try {
            // Shiroha 26.2 exposes this method, while the compile-time NMS
            // artifact does not.  Resolve it at runtime instead of touching
            // version-specific AttributeSupplier fields.
            Method method = AttributeMap.class.getMethod("registerAttribute", Holder.class);
            method.invoke(attributes, holder);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register attribute " + holder, exception);
        }

        if (handle.getAttribute(holder) == null) {
            throw new IllegalStateException("Attribute registration did not create an instance for " + holder);
        }
    }

    @Override
    public void sneak(@NotNull LivingEntity entity, boolean value) {
        CraftLivingEntity craftEntity = ((CraftLivingEntity) entity);
        craftEntity.getHandle().setShiftKeyDown(value);
    }

    @Override
    public boolean hasNavigationPath(@NotNull LivingEntity entity) {
        CraftLivingEntity craftEntity = ((CraftLivingEntity) entity);

        net.minecraft.world.entity.LivingEntity nmsEntity = craftEntity.getHandle();
        if (!(nmsEntity instanceof Mob mob)) return false;

        return mob.getNavigation().isInProgress();
    }

    @Override
    public void setLeashedTo(@NotNull LivingEntity entity, @Nullable Entity holder) {
        CraftLivingEntity craftEntity = (CraftLivingEntity) entity;
        if (!(craftEntity.getHandle() instanceof Mob mob)) return;

        mob.setLeashedTo(holder == null ? null : ((CraftEntity)holder).getHandle(), true);
    }

//    @Override
//    public void damageItem(@NotNull org.bukkit.inventory.EquipmentSlot[] bukkitSlots,
//                           @NotNull LivingEntity bukkitEntity,
//                           @NotNull org.bukkit.damage.DamageSource bukkitSource,
//                           int damage) {
//
//        DamageSource source = ((CraftDamageSource)bukkitSource).getHandle();
//        net.minecraft.world.entity.LivingEntity entity = ((CraftLivingEntity)bukkitEntity).getHandle();
//
//        for (org.bukkit.inventory.EquipmentSlot bukkitSlot : bukkitSlots) {
//            EquipmentSlot slot = CraftEquipmentSlot.getNMS(bukkitSlot);
//            ItemStack stack = entity.getItemBySlot(slot);
//            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
//
//            if (equippable != null && equippable.damageOnHurt() && stack.isDamageableItem() && stack.canBeHurtBy(source)) {
//                stack.hurtAndBreak(damage, entity, slot);
//            }
//        }
//    }
}
