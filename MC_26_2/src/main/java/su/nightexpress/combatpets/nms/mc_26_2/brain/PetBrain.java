package su.nightexpress.combatpets.nms.mc_26_2.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.LongJump;
import net.minecraft.world.entity.monster.breeze.ShootWhenStuck;
import net.minecraft.world.entity.monster.breeze.Slide;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.Bukkit;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.brain.behavior.PetCoreBehaviors;
import su.nightexpress.combatpets.nms.mc_26_2.brain.behavior.PetBreezeShoot;
import su.nightexpress.combatpets.nms.mc_26_2.brain.behavior.PetFightBehaviors;
import su.nightexpress.combatpets.nms.mc_26_2.brain.behavior.PetIdleBehaviors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PetBrain {

    protected static final ImmutableList<SensorType<? extends Sensor<? extends LivingEntity>>> SENSOR_TYPES;
    protected static final ImmutableList<MemoryModuleType<?>>                                  MEMORY_TYPES;
    private static final ImmutableList<MemoryModuleType<?>>                                    BREEZE_MEMORY_TYPES;

    static {
        SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_PLAYERS,
            SensorType.NEAREST_ITEMS,
            SensorType.HURT_BY
            //SensorType.PIGLIN_SPECIFIC_SENSOR
        );
        MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.IS_IN_WATER,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.LIKED_PLAYER,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
            MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ATTACK_COOLING_DOWN,
            MemoryModuleType.INTERACTION_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.ANGRY_AT,
            MemoryModuleType.UNIVERSAL_ANGER,
            MemoryModuleType.AVOID_TARGET,
            MemoryModuleType.RIDE_TARGET,
            MemoryModuleType.ATE_RECENTLY,
            MemoryModuleType.RAM_COOLDOWN_TICKS,
            MemoryModuleType.RAM_TARGET,
            MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
            MemoryModuleType.NEAREST_REPELLENT);
        BREEZE_MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.BREEZE_JUMP_COOLDOWN,
            MemoryModuleType.BREEZE_JUMP_INHALING,
            MemoryModuleType.BREEZE_JUMP_TARGET,
            MemoryModuleType.BREEZE_LEAVING_WATER,
            MemoryModuleType.BREEZE_SHOOT,
            MemoryModuleType.BREEZE_SHOOT_CHARGING,
            MemoryModuleType.BREEZE_SHOOT_COOLDOWN,
            MemoryModuleType.BREEZE_SHOOT_RECOVERING);
    }

    public static <E extends Mob> ImmutableList<SensorType<? extends Sensor<? super E>>> getSensorTypes(@NotNull E entity) {
        return ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_PLAYERS,
            SensorType.NEAREST_ITEMS,
            SensorType.HURT_BY,
            SensorType.PIGLIN_SPECIFIC_SENSOR);
    }

    public static <E extends Mob> Brain.Provider<E> brainProvider(@NotNull E entity) {
        return Brain.provider(MEMORY_TYPES, getSensorTypes(entity), ignored -> ImmutableList.of());
    }

    public static <E extends Mob> Brain.Provider<E> brainProvider(@NotNull E entity, List<MemoryModuleType<?>> extraMemory) {
        List<MemoryModuleType<?>> memoryTypes = new ArrayList<>(MEMORY_TYPES);
        memoryTypes.addAll(extraMemory);

        return Brain.provider(memoryTypes, getSensorTypes(entity), ignored -> ImmutableList.of());
    }

    /**
     * Creates the brain used by a 26.2 pet.  The 26.2 API no longer exposes
     * the old entity-level brainProvider() hook; the provider must therefore
     * be invoked explicitly with both the entity and the packed brain.
     */
    @NotNull
    public static <E extends Mob> Brain<E> makeBrain(@NotNull E pet, @NotNull Brain.Packed packed) {
        Brain.Provider<E> provider = pet instanceof Breeze
            ? brainProvider(pet, BREEZE_MEMORY_TYPES)
            : brainProvider(pet);

        return refreshBrain(pet, provider.makeBrain(pet, packed));
    }

    /** Ticks the replacement brain and selects the plugin's active activity. */
    public static <E extends Mob> void tick(@NotNull E pet, @NotNull ServerLevel level, @NotNull Brain<E> brain) {
        boolean ownerRiding = PetAI.isOwnerRiding(pet);
        if (ownerRiding) {
            stopAiMovement(pet, brain);
        }

        ProfilerFiller profiler = net.minecraft.util.profiling.Profiler.get();
        profiler.push("combatPetsBrain");
        brain.tick(level, pet);
        profiler.pop();

        // Brain behaviors may create a new path during this tick. Rider input must
        // remain the only movement source while the owner is mounted on the pet.
        if (ownerRiding) {
            stopAiMovement(pet, brain);
        }

        profiler.push("combatPetsActivityUpdate");
        PetAI.updateActivity(pet, brain);
        profiler.pop();
    }

    private static void stopAiMovement(@NotNull Mob pet, @NotNull Brain<?> brain) {
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        pet.getNavigation().stop();
    }

    @NotNull
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E extends Mob> Brain<E> refreshBrain(@NotNull E pet, @NotNull Brain<E> brain) {
        // Keep the entity's vanilla memory and sensor registrations, but replace its behaviors.
        brain.removeAllBehaviors();
        ImmutableList.Builder<BehaviorControl<? super E>> coreBehaviors = ImmutableList.builder();
        coreBehaviors.add(
            PetCoreBehaviors.lookAtTarget(),
            PetCoreBehaviors.moveToTarget()
        );
        // Vanilla aquatic brains use water pathing, not the generic Swim behavior.
        // Swim repeatedly triggers JumpControl in deep water and makes these pets bounce upward.
        if (!(pet instanceof AbstractNautilus) && !(pet instanceof Axolotl)) {
            coreBehaviors.add(PetCoreBehaviors.swim());
        }
        coreBehaviors.add(PetFightBehaviors.stopAngryIfTargetDead());
        brain.addActivity(Activity.CORE, prioritized(coreBehaviors.build()), ImmutableSet.of(), ImmutableSet.of());

        brain.addActivity(Activity.IDLE, prioritized(ImmutableList.of(
            new RunOne<>(ImmutableList.of(Pair.of(PetIdleBehaviors.lookAtOwner(), 1))),
            PetIdleBehaviors.followOwner(),
            PetFightBehaviors.autoTargetAndAttack())
        ), ImmutableSet.of(), ImmutableSet.of());


        ImmutableList.Builder<BehaviorControl<? super E>> fightBehaviors = ImmutableList.builder();
        fightBehaviors.add(PetFightBehaviors.stopAttackIfTargetInvalid(pet));
        if (pet instanceof Breeze) {
            // Breeze combat is a jump/slide/shoot state machine. A generic walk
            // target blocks all four vanilla behaviors because they require it absent.
            fightBehaviors.add(
                (BehaviorControl) new PetBreezeShoot(),
                (BehaviorControl) new LongJump(),
                (BehaviorControl) new ShootWhenStuck(),
                (BehaviorControl) new Slide());
        }
        else {
            fightBehaviors.add(PetFightBehaviors.reachTargetWhenOutOfRange());
            if (pet instanceof CrossbowAttackMob) {
                fightBehaviors.add(PetFightBehaviors.crossbowAttack());
            }
            fightBehaviors.add(PetFightBehaviors.meleeAttack());
        }
        brain.addActivity(Activity.FIGHT, prioritized(fightBehaviors.build()), ImmutableSet.of(), ImmutableSet.of());

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();

        return brain;
    }

    private static <E extends LivingEntity> ImmutableList<Pair<Integer, ? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super E>>> prioritized(
        ImmutableList<? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<? super E>> behaviors) {
        return behaviors.stream().map(behavior -> Pair.of(0, behavior)).collect(ImmutableList.toImmutableList());
    }

    // Simulate natural mob damage, but without the actual damager to prevent certain AI goals from triggering.
    public static boolean hurt(@NotNull LivingEntity pet, @NotNull DamageSource original, @NotNull Function<DamageSource, Boolean> hurtServer) {
        DamageSource fixed = new DamageSource(original.typeHolder(), original.getDirectEntity(), null, original.sourcePositionRaw());

        if (original.getEntity() instanceof ServerPlayer player && pet instanceof PetEntity petEntity && petEntity.getOwnerId().equals(player.getUUID())) {
            return false;
        }

        boolean flag = hurtServer.apply(fixed);

        if (original.getEntity() instanceof LivingEntity mob) {
            pet.setLastHurtByMob(mob);
        }
        if (original.getEntity() instanceof ServerPlayer player) {
            pet.setLastHurtByPlayer(player, LivingEntity.PLAYER_HURT_EXPERIENCE_TIME);
        }

        return flag;
    }

    // Reimplemention of the Entity#tunderHit method to cancel lightning effects and apply damage for certain mobs like Mooshroms, Pigs.
    public static void thunderHit(@NotNull LivingEntity entity, @NotNull ServerLevel level, @NotNull LightningBolt bolt) {
        entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
        org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
        org.bukkit.entity.Entity bukkitStorm = bolt.getBukkitEntity();
        if (entity.getRemainingFireTicks() == 0) {
            EntityCombustByEntityEvent entityCombustEvent = new EntityCombustByEntityEvent(bukkitStorm, bukkitEntity, 8.0F);
            Bukkit.getPluginManager().callEvent(entityCombustEvent);
            if (!entityCombustEvent.isCancelled()) {
                entity.igniteForSeconds(entityCombustEvent.getDuration(), false);
            }
        }

        if (!entity.fireImmune()) {
            entity.hurtServer(level, entity.damageSources().lightningBolt().customEntityDamager(bolt), 5.0F);
        }
    }
}
