package su.nightexpress.combatpets.nms.mc_26_2.pets;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.api.pet.PetEntity;

/** Land fallback shared by the strictly aquatic 26.2 pets. */
public final class WaterPetHabitat {

    private static final int SEARCH_RADIUS = 10;
    private static final int RETURN_WITHOUT_WATER_TICKS = 60;
    private static final int RETURN_STUCK_TICKS = 200;
    private static final int TELEPORT_STUCK_TICKS = 40;
    private static final double PROGRESS_EPSILON = 0.04D;

    private final Mob pet;

    private int landTicks;
    private int stuckTicks;
    private boolean returnRequested;
    private boolean teleportRequested;
    private double bestTargetDistance = Double.MAX_VALUE;
    private BlockPos waterTarget;

    public WaterPetHabitat(@NotNull Mob pet) {
        this.pet = pet;
    }

    public void tick() {
        if (this.pet.isInWater()) {
            this.landTicks = 0;
            this.returnRequested = false;
            this.setWaterTarget(null);
            return;
        }

        this.pet.setTarget(null);
        this.pet.getNavigation().stop();
        this.landTicks++;

        if (this.waterTarget != null && !this.isSafeWater(this.waterTarget)) {
            this.setWaterTarget(null);
        }
        if (this.waterTarget == null && (this.landTicks == 1 || this.landTicks % 20 == 0)) {
            this.setWaterTarget(this.findNearbyWater());
        }

        if (this.waterTarget == null) {
            Vec3 movement = this.pet.getDeltaMovement();
            this.pet.setDeltaMovement(movement.x * 0.2D, Math.min(0D, movement.y), movement.z * 0.2D);
            if (this.landTicks >= RETURN_WITHOUT_WATER_TICKS) this.returnPet();
            return;
        }

        if (this.teleportRequested) {
            this.pet.setDeltaMovement(Vec3.ZERO);
            if (this.landTicks >= RETURN_STUCK_TICKS) this.returnPet();
            return;
        }

        this.steerTowardWater(this.waterTarget);
        this.checkProgress(this.waterTarget);
        if (this.landTicks >= RETURN_STUCK_TICKS) this.returnPet();
    }

    private void checkProgress(@NotNull BlockPos target) {
        double distance = this.horizontalDistance(target);
        if (distance + PROGRESS_EPSILON < this.bestTargetDistance) {
            this.bestTargetDistance = distance;
            this.stuckTicks = 0;
            return;
        }
        if (++this.stuckTicks < TELEPORT_STUCK_TICKS) return;

        this.teleportRequested = true;
        this.pet.setDeltaMovement(Vec3.ZERO);
        Location destination = new Location(
            this.pet.getBukkitEntity().getWorld(),
            target.getX() + 0.5D,
            target.getY() + 0.1D,
            target.getZ() + 0.5D,
            this.pet.getYRot(),
            this.pet.getXRot()
        );
        this.pet.getBukkitEntity().teleportAsync(destination);
    }

    private void steerTowardWater(@NotNull BlockPos target) {
        double x = target.getX() + 0.5D - this.pet.getX();
        double z = target.getZ() + 0.5D - this.pet.getZ();
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal < 0.001D) return;

        double speed = 0.22D;
        Vec3 movement = this.pet.getDeltaMovement();
        double y = this.pet.onGround() ? (target.getY() > this.pet.getY() ? 0.42D : 0.28D) : movement.y;
        this.pet.setDeltaMovement(x / horizontal * speed, y, z / horizontal * speed);
        this.pet.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
    }

    @Nullable
    private BlockPos findNearbyWater() {
        BlockPos origin = this.pet.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = -3; y <= 3; y++) {
            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!this.isSafeWater(candidate)) continue;

                    double distance = candidate.distSqr(origin);
                    if (distance >= bestDistance) continue;
                    best = candidate.immutable();
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean isWater(@NotNull BlockPos pos) {
        return this.pet.level().getFluidState(pos).is(FluidTags.WATER);
    }

    private boolean isSafeWater(@NotNull BlockPos pos) {
        if (!this.isWater(pos)) return false;

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.1D;
        double z = pos.getZ() + 0.5D;
        AABB targetBox = this.pet.getBoundingBox().move(x - this.pet.getX(), y - this.pet.getY(), z - this.pet.getZ());
        return this.pet.level().noCollision(this.pet, targetBox);
    }

    private double horizontalDistance(@NotNull BlockPos target) {
        double x = target.getX() + 0.5D - this.pet.getX();
        double z = target.getZ() + 0.5D - this.pet.getZ();
        return x * x + z * z;
    }

    private void setWaterTarget(@Nullable BlockPos target) {
        this.waterTarget = target;
        this.bestTargetDistance = target == null ? Double.MAX_VALUE : this.horizontalDistance(target);
        this.stuckTicks = 0;
        this.teleportRequested = false;
    }

    private void returnPet() {
        if (this.returnRequested) return;
        this.returnRequested = true;
        ((PetEntity) this.pet).getHolder().returnToCollection();
    }
}
