package su.nightexpress.combatpets.nms.mc_26_2.pets.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.AbstractPetFollowOwnerGoal;

public class HappyGhastPet extends HappyGhast implements PetEntity {

    public HappyGhastPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("happy_ghast"), level);
        this.setAge(0);
    }

    @Override
    public void setGoals() {
        // Happy Ghast is a transport pet. Its 4x4 body is unsuitable for the
        // generic melee goal, so it follows the owner but does not auto-target.
        this.goalSelector.addGoal(2, new HappyGhastFollowOwnerGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor,
                                        DifficultyInstance difficulty,
                                        EntitySpawnReason reason,
                                        SpawnGroupData groupData) {
        return groupData;
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, PetAI.ARMOR_SLOTS);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        if (!this.isWearingBodyArmor() || this.isOnStillTimeout()) return null;

        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player && player.getBukkitEntity() == this.getHolder().getOwner()) {
                return player;
            }
        }
        return null;
    }

    private static class HappyGhastFollowOwnerGoal extends AbstractPetFollowOwnerGoal {

        private final HappyGhast happyGhast;

        private HappyGhastFollowOwnerGoal(@NotNull HappyGhast happyGhast) {
            super(happyGhast);
            this.happyGhast = happyGhast;
        }

        @Override
        public boolean canUse() {
            return !this.happyGhast.isVehicle() && !this.happyGhast.isOnStillTimeout() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void tick() {
            if (!this.refreshOwner()) return;

            this.pet.getMoveControl().setWantedPosition(
                this.owner.getX(),
                this.owner.getY() + 1.5D,
                this.owner.getZ(),
                1D
            );
            this.pet.lookAt(this.owner, 20F, 20F);
        }
    }
}
