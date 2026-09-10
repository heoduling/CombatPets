package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Guardian;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.api.pet.type.ExhaustReason;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.WaterPetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.WaterPetFollowOwnerGoal;
import su.nightexpress.combatpets.nms.mc_26_2.pets.WaterPetHabitat;

public class GuardianPet extends Guardian implements PetEntity {

    private final WaterPetHabitat habitat;

    public GuardianPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("guardian"), level);
        this.habitat = new WaterPetHabitat(this);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new WaterPetAutoTargetGoal(this));
        this.goalSelector.addGoal(2, new WaterPetFollowOwnerGoal(this));
        this.goalSelector.addGoal(4, new WaterGuardianAttackGoal(this));
    }

    @Override
    public void aiStep() {
        if (!this.isInWater()) {
            this.setTarget(null);
            if (this.hasActiveAttackTarget()) this.setActiveAttackTarget(0);
        }

        super.aiStep();
        this.habitat.tick();

        if (!this.isInWater() && this.hasActiveAttackTarget()) {
            this.setActiveAttackTarget(0);
        }
    }

    private static final class WaterGuardianAttackGoal extends GuardianAttackGoal {

        private final GuardianPet pet;

        private WaterGuardianAttackGoal(@NotNull GuardianPet pet) {
            super(pet);
            this.pet = pet;
        }

        @Override
        public boolean canUse() {
            return this.pet.isInWater() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.pet.isInWater() && super.canContinueToUse();
        }

        @Override
        public void tick() {
            int previous = this.attackTime;
            super.tick();
            if (previous < this.pet.getAttackDuration() && this.attackTime >= this.pet.getAttackDuration()) {
                this.pet.getHolder().doExhaust(ExhaustReason.COMBAT);
            }
        }
    }
}
