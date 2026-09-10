package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.CubePetFollowTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.CubePetMovementDriver;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;

public class MagmaCubePet extends MagmaCube implements PetEntity, CubePetMovementDriver {

    public MagmaCubePet(@NotNull ServerLevel world) {
        super(EntityTypes.get("magma_cube"), world);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PetFollowOwnerGoal(this));
        this.goalSelector.addGoal(2, new CubePetFollowTargetGoal(this));
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
    protected int getJumpDelay() {
        return 15;
    }

    @Override
    protected int getSplitCount() {
        return 0;
    }

    @Override
    public void push(Entity entity) {
        LivingEntity target = this.getTarget();
        if (target == null || entity != target || entity.getBukkitEntity() == this.getHolder().getOwner()) return;

        if (!(entity instanceof IronGolem)) {
            this.dealDamage(target);
        }

        super.push(entity);
    }

    @Override
    public void playerTouch(Player entity) {
        if (this.holder().isEmpty()) return;
        if (entity.getBukkitEntity() == this.getHolder().getOwner()) return;

        super.playerTouch(entity);
    }

    @Override
    protected boolean isDealsDamage() {
        return true;
    }

    @Override
    public void driveCube(float direction, double speedModifier) {
        CubeMobMoveControl<?> moveControl = (CubeMobMoveControl<?>) this.getMoveControl();
        moveControl.setDirection(direction, true);
        moveControl.setWantedMovement(speedModifier);
    }
}
