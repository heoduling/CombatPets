package su.nightexpress.combatpets.nms.mc_26_2.goals.follow;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Drives cube mobs through their native jump controller while still using a
 * navigation path to pick the next direction around obstacles.
 */
public final class CubePetMovement {

    private static final int PATH_RECALCULATION_DELAY = 10;

    private final AbstractCubeMob pet;
    private final CubePetMovementDriver driver;
    private int pathRecalculationDelay;

    public CubePetMovement(@NotNull AbstractCubeMob pet) {
        this.pet = pet;
        if (!(pet instanceof CubePetMovementDriver driver)) {
            throw new IllegalArgumentException("Cube pet does not expose its native movement controller: " + pet.getClass().getName());
        }
        this.driver = driver;
    }

    public void moveToward(@NotNull LivingEntity target, double speedModifier) {
        PathNavigation navigation = this.pet.getNavigation();
        BlockPos targetPosition = target.blockPosition();
        if (--this.pathRecalculationDelay <= 0) {
            this.pathRecalculationDelay = PATH_RECALCULATION_DELAY;
            if (navigation.isDone() || navigation.isStuck() || !targetPosition.equals(navigation.getTargetPos())) {
                if (!navigation.moveTo(target, speedModifier)) {
                    navigation.stop();
                }
            }
        }

        Path path = navigation.getPath();
        Vec3 destination = path == null || path.isDone() || !targetPosition.equals(navigation.getTargetPos())
            ? target.position()
            : path.getNextEntityPos(this.pet);
        double xDifference = destination.x - this.pet.getX();
        double zDifference = destination.z - this.pet.getZ();
        float direction = xDifference * xDifference + zDifference * zDifference < 1.0E-6D
            ? this.pet.getYRot()
            : (float) (Math.atan2(zDifference, xDifference) * (180D / Math.PI)) - 90F;

        this.driver.driveCube(direction, speedModifier);
    }

    public void stop() {
        this.pathRecalculationDelay = 0;
        this.pet.getNavigation().stop();
    }
}
