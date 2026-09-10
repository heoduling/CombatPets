package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetBowAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetMeleeAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;

public class ParchedPet extends Parched implements PetEntity {

    private PetBowAttackGoal<Parched> bowGoal;
    private PetMeleeAttackGoal         meleeGoal;

    public ParchedPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("parched"), level);
    }

    @Override
    public void setGoals() {
        this.bowGoal = new PetBowAttackGoal<>(this, 1D, 15F);
        this.meleeGoal = new PetMeleeAttackGoal(this);

        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PetFollowOwnerGoal(this));
        this.reassessWeaponGoal();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData) {
        return groupData;
    }

    @Override
    public void reassessWeaponGoal() {
        if (this.meleeGoal == null || this.bowGoal == null) return;

        Level level = this.level();
        if (level == null || level.isClientSide()) return;

        this.goalSelector.removeGoal(this.meleeGoal);
        this.goalSelector.removeGoal(this.bowGoal);
        ItemStack itemStack = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        this.goalSelector.addGoal(4, itemStack.is(Items.BOW) ? this.bowGoal : this.meleeGoal);
    }

    @Override
    public boolean is(TagKey<EntityType<?>> tag) {
        // In 26.2 daylight burning is selected through the entity-type tag in
        // Mob#aiStep; the older isSunBurnTick hook is private and cannot be
        // overridden. Exclude only this tag and preserve every other tag.
        return tag != EntityTypeTags.BURN_IN_DAYLIGHT && this.typeHolder().is(tag);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, PetAI.ARMOR_SLOTS);
    }
}
