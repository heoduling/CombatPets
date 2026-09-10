package su.nightexpress.combatpets.nms.mc_26_2.goals.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.CubePetMovement;

import java.util.EnumSet;

public final class CubePetFollowTargetGoal extends Goal {

    private final AbstractCubeMob pet;
    private final CubePetMovement movement;

    public CubePetFollowTargetGoal(@NotNull AbstractCubeMob pet) {
        this.pet = pet;
        this.movement = new CubePetMovement(pet);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.pet.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.movement.stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.pet.getTarget();
        if (target == null) return;

        this.movement.moveToward(target, 1D);
    }
}
