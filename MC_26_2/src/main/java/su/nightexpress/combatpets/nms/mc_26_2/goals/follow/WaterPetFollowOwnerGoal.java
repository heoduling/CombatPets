package su.nightexpress.combatpets.nms.mc_26_2.goals.follow;

import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

public class WaterPetFollowOwnerGoal extends PetFollowOwnerGoal {

    public WaterPetFollowOwnerGoal(@NotNull Mob pet) {
        super(pet);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && this.pet.isInWater() && this.owner.isInWater();
    }
}
