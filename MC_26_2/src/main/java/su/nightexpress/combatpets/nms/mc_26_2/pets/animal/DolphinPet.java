package su.nightexpress.combatpets.nms.mc_26_2.pets.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetMeleeAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.WaterPetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.WaterPetFollowOwnerGoal;
import su.nightexpress.combatpets.nms.mc_26_2.pets.WaterPetHabitat;

public class DolphinPet extends Dolphin implements PetEntity {

    private final WaterPetHabitat      habitat;
    private final BoundedWaterIdleGoal idleGoal;

    public DolphinPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("dolphin"), level);
        this.habitat = new WaterPetHabitat(this);
        this.idleGoal = new BoundedWaterIdleGoal(this);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new WaterPetAutoTargetGoal(this));
        this.goalSelector.addGoal(2, new DolphinFollowOwnerGoal(this, this.idleGoal));
        this.goalSelector.addGoal(4, new WaterMeleeGoal(this));
        this.goalSelector.addGoal(7, this.idleGoal);
    }

    @Override
    protected void handleAirSupply(int air) {
        this.setAirSupply(this.getMaxAirSupply());
    }

    @Override
    public void tick() {
        this.setMoisntessLevel(2400);
        this.setAirSupply(this.getMaxAirSupply());
        super.tick();
        this.setMoisntessLevel(2400);
        this.setAirSupply(this.getMaxAirSupply());
        this.habitat.tick();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }

    private static final class WaterMeleeGoal extends PetMeleeAttackGoal {

        private WaterMeleeGoal(@NotNull Dolphin pet) {
            super(pet);
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.pet.getTarget();
            return this.pet.isInWater() && target != null && target.isInWater() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.pet.getTarget();
            return this.pet.isInWater() && target != null && target.isInWater() && super.canContinueToUse();
        }
    }

    private static final class DolphinFollowOwnerGoal extends WaterPetFollowOwnerGoal {

        private final BoundedWaterIdleGoal idleGoal;

        private DolphinFollowOwnerGoal(@NotNull Dolphin pet, @NotNull BoundedWaterIdleGoal idleGoal) {
            super(pet);
            this.idleGoal = idleGoal;
        }

        @Override
        public void tick() {
            this.idleGoal.refreshAnchor();
            super.tick();
        }
    }

    private static final class BoundedWaterIdleGoal extends RandomStrollGoal {

        private static final double MAX_IDLE_DISTANCE_SQUARED = 64D;
        private static final double TELEPORT_RESET_DISTANCE_SQUARED = 1024D;

        private BlockPos anchor;

        private BoundedWaterIdleGoal(@NotNull Dolphin pet) {
            super(pet, 1D, 40, false);
        }

        @Override
        public boolean canUse() {
            if (!this.mob.isInWater() || this.mob.getTarget() != null) return false;

            if (this.anchor == null || Vec3.atCenterOf(this.anchor).distanceToSqr(this.mob.position()) > TELEPORT_RESET_DISTANCE_SQUARED) {
                this.refreshAnchor();
            }
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.mob.isInWater() && this.mob.getTarget() == null && super.canContinueToUse();
        }

        @Override
        protected @Nullable Vec3 getPosition() {
            Vec3 anchorCenter = Vec3.atCenterOf(this.anchor);

            for (int attempt = 0; attempt < 6; attempt++) {
                Vec3 position = BehaviorUtils.getRandomSwimmablePos(this.mob, 6, 3);
                if (position == null || position.distanceToSqr(anchorCenter) > MAX_IDLE_DISTANCE_SQUARED) continue;
                if (!this.mob.level().getFluidState(BlockPos.containing(position)).is(FluidTags.WATER)) continue;

                return position;
            }
            return null;
        }

        private void refreshAnchor() {
            this.anchor = this.mob.blockPosition();
        }
    }
}
