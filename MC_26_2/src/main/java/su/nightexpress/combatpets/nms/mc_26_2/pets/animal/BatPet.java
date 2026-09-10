package su.nightexpress.combatpets.nms.mc_26_2.pets.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetMeleeAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;

public class BatPet extends Bat implements PetEntity {

    public BatPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("bat"), level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.navigation = new FlyingPathNavigation(this, level);
        this.setResting(false);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        this.goalSelector.addGoal(0, new PetMeleeAttackGoal(this, 1.4D));
        this.goalSelector.addGoal(2, new PetFollowOwnerGoal(this));
    }

    @Override
    protected void customServerAiStep(@NotNull ServerLevel level) {
        // Mob.serverAiStep already ticks goals, navigation and controls around
        // this hook. Bat's implementation adds random flight and hanging,
        // which would overwrite the pet goals, so intentionally do nothing.
        this.setResting(false);
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
}
