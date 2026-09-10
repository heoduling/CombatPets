package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Bucketable;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.CubePetFollowTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.CubePetMovementDriver;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;

public class SulfurCubePet extends SulfurCube implements PetEntity, CubePetMovementDriver {

    public SulfurCubePet(@NotNull ServerLevel world) {
        super(EntityTypes.get("sulfur_cube"), world);
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

    @Override
    public boolean canExplode() {
        return false;
    }

    @Override
    public boolean primeTime(boolean sound) {
        return false;
    }

    @Override
    public boolean canBePickedUpWithBucket(ItemStack bucket) {
        return super.canBePickedUpWithBucket(bucket);
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return false;
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity item) {
        // Pet equipment is managed by CombatPets, not by sulfur cube swallowing.
    }

    @Override
    protected int getSplitCount() {
        return 0;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return Bucketable.bucketMobPickup(player, hand, this).orElse(
            this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER
        );
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return false;
    }
}
