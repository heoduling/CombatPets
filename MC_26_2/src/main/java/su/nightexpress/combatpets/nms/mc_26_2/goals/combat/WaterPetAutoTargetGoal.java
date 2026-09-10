package su.nightexpress.combatpets.nms.mc_26_2.goals.combat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class WaterPetAutoTargetGoal extends PetAutoTargetGoal {

    private static final int TARGET_WATER_EXIT_GRACE_TICKS = 10;

    private final Mob pet;
    private int targetOutOfWaterTicks;

    public WaterPetAutoTargetGoal(@NotNull Mob pet) {
        super(pet);
        this.pet = pet;
    }

    @Override
    public boolean canUse() {
        if (!this.pet.isInWater() || !super.canUse()) return false;

        if (this.targetMob != null && this.targetMob.isInWater()) {
            this.targetOutOfWaterTicks = 0;
            return true;
        }

        this.targetMob = null;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.pet.getTarget();
        if (target == null) target = this.targetMob;

        if (!this.pet.isInWater() || target == null) return false;

        if (target.isInWater()) {
            this.targetOutOfWaterTicks = 0;
        }
        else if (++this.targetOutOfWaterTicks > TARGET_WATER_EXIT_GRACE_TICKS) {
            return false;
        }

        return super.canContinueToUse();
    }
}
