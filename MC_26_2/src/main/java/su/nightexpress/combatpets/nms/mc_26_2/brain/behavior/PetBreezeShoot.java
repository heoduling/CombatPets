package su.nightexpress.combatpets.nms.mc_26_2.brain.behavior;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.Shoot;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.ItemStack;

/** Keeps vanilla Breeze combat timing while removing projectile spread for pet attacks. */
public final class PetBreezeShoot extends Shoot {

    private static final int RECOVER_TICKS = 4;

    @Override
    protected void tick(ServerLevel level, Breeze breeze, long gameTime) {
        LivingEntity target = breeze.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) return;

        breeze.lookAt(EntityAnchorArgument.Anchor.EYES, target.position());
        if (breeze.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_SHOOT_CHARGING)
            || breeze.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_SHOOT_RECOVERING)) {
            return;
        }

        breeze.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_RECOVERING, Unit.INSTANCE, RECOVER_TICKS);

        double x = target.getX() - breeze.getX();
        double y = target.getBoundingBox().getCenter().y - breeze.getFiringYPosition();
        double z = target.getZ() - breeze.getZ();
        Projectile.spawnProjectileUsingShoot(new BreezeWindCharge(breeze, level), level, ItemStack.EMPTY, x, y, z, 0.7F, 0F);
        breeze.playSound(SoundEvents.BREEZE_SHOOT, 1.5F, 1F);
    }
}
