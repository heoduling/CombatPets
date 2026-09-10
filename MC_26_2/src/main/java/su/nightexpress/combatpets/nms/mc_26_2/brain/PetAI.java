package su.nightexpress.combatpets.nms.mc_26_2.brain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.api.pet.type.CombatMode;

import java.util.Optional;

public class PetAI {

    private static final int MAX_TICKS_TO_AUTO_ATTACK = 50;
    public static final double OWNER_RECALL_DISTANCE = 31D;
    private static final UniformInt RETREAT_DURATION = TimeUtil.rangeOfSeconds(5, 20);

    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static boolean isDamagedBySomeone(@NotNull LivingEntity entity) {
        return entity.tickCount - entity.getLastHurtByMobTimestamp() <= MAX_TICKS_TO_AUTO_ATTACK && entity.getLastHurtByMob() != entity;
    }

    public static boolean isAttackedSomeone(@NotNull LivingEntity entity) {
        return entity.tickCount - entity.getLastHurtMobTimestamp() <= MAX_TICKS_TO_AUTO_ATTACK && entity.getLastHurtMob() != entity;
    }

    public static boolean isOwnerRiding(@NotNull Mob pet) {
        ActivePet holder = PetEntityBridge.getByMobId(pet.getUUID());
        ServerPlayer owner = holder == null ? null : getLocalOwner(holder);
        return owner != null && pet.hasPassenger(owner);
    }

    /** Returns the owner only when this pet's region thread owns the player too. */
    @Nullable
    public static ServerPlayer getLocalOwner(@NotNull ActivePet activePet) {
        org.bukkit.entity.Player owner = activePet.getOwner();
        if (!Bukkit.isOwnedByCurrentRegion(owner)) return null;
        return ((CraftPlayer) owner).getHandle();
    }

    @Nullable
    public static LivingEntity findTarget(@NotNull LivingEntity pet, @NotNull ActivePet activePet) {
        ServerPlayer owner = getLocalOwner(activePet);
        if (owner == null) {
            activePet.moveToOwner();
            return null;
        }
        if (pet instanceof Mob mob && isOwnerTooFar(mob, owner)) return null;

        CombatMode combatMode = activePet.getCombatMode();

        LivingEntity angerTarget = null;

        if (isDamagedBySomeone(pet) && combatMode != CombatMode.PASSIVE) {
            angerTarget = pet.getLastHurtByMob();
        }

        if (angerTarget == null && (combatMode == CombatMode.PROTECTIVE || combatMode == CombatMode.PROTECTIVE_AND_SUPPORTIVE)) {
            if (PetAI.isDamagedBySomeone(owner)) {
                angerTarget = owner.getLastHurtByMob();
            }
        }

        if (angerTarget == null && (combatMode == CombatMode.SUPPORTIVE || combatMode == CombatMode.PROTECTIVE_AND_SUPPORTIVE)) {
            if (PetAI.isAttackedSomeone(owner)) {
                angerTarget = owner.getLastHurtMob();

                // Projectiles set lastHurtMob even if damage event was cancelled
                // so need to double check if target was actually damaged.
                if (angerTarget != null) {
                    LivingEntity lastAttacker = angerTarget.getLastHurtByMob();
                    boolean damagedByOwner = lastAttacker == owner;
                    boolean continuedByPet = lastAttacker == pet && isAttackedSomeone(pet) && pet.getLastHurtMob() == angerTarget;
                    if (!damagedByOwner && !continuedByPet) angerTarget = null;
                }
            }
            else if (PetAI.isAttackedSomeone(pet)) {
                LivingEntity petTarget = pet.getLastHurtMob();
                if (petTarget != null && petTarget.getLastHurtByMob() == pet) angerTarget = petTarget;
            }
        }

        // The owner is never a legal pet target. A teleport can leave vanilla
        // last-hurt state pointing at the owner even after Bukkit damage was
        // cancelled, so enforce this invariant at the shared target source.
        return angerTarget == owner ? null : angerTarget;
    }

    public static <T extends LivingEntity> void updateActivity(@NotNull Mob entity, @NotNull Brain<T> brain) {
        ActivePet holder = PetEntityBridge.getByMobId(entity.getUUID());
        ServerPlayer owner = holder == null ? null : getLocalOwner(holder);
        if (holder != null && owner == null) {
            clearMovementAndCombat(entity, brain);
            holder.moveToOwner();
            brain.setActiveActivityIfPossible(Activity.IDLE);
            entity.setAggressive(false);
            return;
        }
        if (owner != null && isOwnerTooFar(entity, owner)) {
            clearMovementAndCombat(entity, brain);
        }

        if (PetAI.getAttackTarget(entity).isPresent()) {
            brain.setActiveActivityIfPossible(Activity.FIGHT);
        }
        else {
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
        entity.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
    }

    private static <T extends LivingEntity> void clearMovementAndCombat(@NotNull Mob entity, @NotNull Brain<T> brain) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            brain.eraseMemory(MemoryModuleType.UNIVERSAL_ANGER);
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            entity.getNavigation().stop();
    }

    public static boolean isOwnerTooFar(@NotNull Mob pet, @NotNull LivingEntity owner) {
        return !pet.closerThan(owner, OWNER_RECALL_DISTANCE);
    }

    public static boolean shouldUseWaterNavigation(@NotNull Mob pet) {
        if (!pet.isInWater()) return false;

        Optional<LivingEntity> target = getAttackTarget(pet);
        if (target.isPresent()) return target.get().isInWater();

        ActivePet holder = PetEntityBridge.getByMobId(pet.getUUID());
        if (holder == null) return true;

        ServerPlayer owner = getLocalOwner(holder);
        if (owner == null) {
            holder.moveToOwner();
            return true;
        }
        return owner.isInWater();
    }

    public static float getMovementSpeedModifier(@NotNull LivingEntity pet) {
        if (pet instanceof Axolotl) return pet.isInWater() ? 0.6F : 0.15F;
        if (pet instanceof Camel) return 2F;
        if (pet instanceof Breeze) return 0.6F;
        return 1F;
    }

    public static boolean setAngerTarget(@NotNull Mob pet, @NotNull LivingEntity target, boolean force) {
        if (!force) {
            if (!Sensor.isEntityAttackableIgnoringLineOfSight((ServerLevel) pet.level(), pet, target)) return false;
        }

        pet.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        pet.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 60L & 20L);
        pet.getBrain().setMemoryWithExpiry(MemoryModuleType.UNIVERSAL_ANGER, true, 60L * 20L);
        pet.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        return true;
    }

    public static void setAvoidTargetAndDontHuntForAWhile(@NotNull Mob pet, @NotNull LivingEntity target) {
        pet.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        pet.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        pet.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pet.getBrain().setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, target, RETREAT_DURATION.sample(pet.level().getRandom()));
    }

    @NotNull
    public static Optional<LivingEntity> getAttackTarget(@NotNull Mob pet) {
        return pet.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
    }

    public static boolean hasAttackTarget(@NotNull Mob mob) {
        return getAttackTarget(mob).isPresent();
    }
}
