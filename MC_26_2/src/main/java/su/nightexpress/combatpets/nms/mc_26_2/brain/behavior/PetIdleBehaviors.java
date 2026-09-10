package su.nightexpress.combatpets.nms.mc_26_2.brain.behavior;

import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;

public class PetIdleBehaviors {

    private static final UniformInt FOLLOW_RANGE = UniformInt.of(2, 30);

    @NotNull
    public static OneShot<LivingEntity> lookAtOwner() {
        return BehaviorBuilder.create((builder) -> {
            return builder.group(
                builder.absent(MemoryModuleType.LOOK_TARGET)
            ).apply(builder, (memLookTarget) -> {
                return (world, mob, i) -> {
                    ActivePet holder = PetEntityBridge.getByMobId(mob.getUUID());
                    if (holder == null) return false;

                    ServerPlayer owner = PetAI.getLocalOwner(holder);
                    if (owner == null) {
                        holder.moveToOwner();
                        return false;
                    }

                    memLookTarget.set(new EntityTracker(owner, true));
                    return true;
                };
            });
        });
    }

    @NotNull
    public static BehaviorControl<Mob> followOwner() {
        return BehaviorBuilder.create((builder) -> {
            return builder.group(
                builder.registered(MemoryModuleType.LOOK_TARGET),
                builder.absent(MemoryModuleType.WALK_TARGET))
                .apply(builder, (memLookTarget, memWalkTarget) -> {
                    return (world, pet, i) -> {
                        ActivePet holder = PetEntityBridge.getByMobId(pet.getUUID());
                        if (holder == null) return false;

                        ServerPlayer owner = PetAI.getLocalOwner(holder);
                        if (owner == null) {
                            holder.moveToOwner();
                            return false;
                        }

                        // A mounted owner is already moving together with this pet.
                        // Following the rider makes tall mounts path toward a point
                        // above themselves and can repeatedly launch them upward.
                        if (pet.hasPassenger(owner)) return false;

                        boolean isFarAway = PetAI.isOwnerTooFar(pet, owner);
                        if (isFarAway) {
                            pet.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
                            pet.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                            holder.moveToOwner();
                            return false;
                        }

                        if (!pet.closerThan(owner, FOLLOW_RANGE.minInclusive())) {
                            boolean forEyes = pet.getType() == EntityTypes.get("allay");

                            WalkTarget walkTarget = new WalkTarget(new EntityTracker(owner, forEyes), PetAI.getMovementSpeedModifier(pet), FOLLOW_RANGE.minInclusive() - 1);
                            memLookTarget.set(new EntityTracker(owner, true));
                            memWalkTarget.set(walkTarget);
                            return true;
                        }
                        return false;
                    };
                });
        });
    }
}
