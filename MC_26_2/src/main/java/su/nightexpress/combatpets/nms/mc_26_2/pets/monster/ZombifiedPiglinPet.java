package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetZombieAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;

public class ZombifiedPiglinPet extends ZombifiedPiglin implements PetEntity {

    public ZombifiedPiglinPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("zombified_piglin"), level);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PetFollowOwnerGoal(this));
        this.goalSelector.addGoal(2, new PetZombieAttackGoal(this, 1D));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData) {
        return groupData;
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, PetAI.ARMOR_SLOTS);
    }

    @Override
    protected void customServerAiStep(ServerLevel worldserver) {

    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }
}
