package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetMeleeAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;
import su.nightexpress.nightcore.util.Reflex;

import java.lang.reflect.Method;

public class CreeperPet extends Creeper implements PetEntity {

    private static final Method SPAWN_LINGERING_CLOUD = Reflex.safeMethod(Creeper.class, "spawnLingeringCloud", "gK");

    private int explodeCooldown = 0;

    public CreeperPet(@NotNull ServerLevel world) {
        super(EntityTypes.get("creeper"), world);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PetFollowOwnerGoal(this));
        // A vanilla SwellGoal has higher priority than PetMeleeAttackGoal, so
        // an armed creeper would never perform its configured melee attack.
        this.goalSelector.addGoal(2, new PetSwellGoal(this));
        this.goalSelector.addGoal(4, new PetMeleeAttackGoal(this));
    }

    private boolean isArmed() {
        ItemStack stack = this.getMainHandItem();
        Item item = stack.getItem();
        return stack.is(ItemTags.SWORDS)
            || stack.is(ItemTags.AXES)
            || item instanceof ProjectileWeaponItem
            || item instanceof MaceItem
            || item instanceof TridentItem;
    }

    /**
     * Vanilla Creeper.doHurtTarget intentionally does not deal melee damage
     * (it only returns true).  Armed pets still use the shared melee goal, so
     * provide the normal weapon damage path when a player equips this pet.
     */
    @Override
    public boolean doHurtTarget(@NotNull ServerLevel level, @NotNull net.minecraft.world.entity.Entity target) {
        if (!this.isArmed()) {
            return super.doHurtTarget(level, target);
        }

        ItemStack weapon = this.getWeaponItem();
        DamageSource source = weapon.getDamageSource(this);
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        damage += weapon.getItem().getAttackDamageBonus(target, damage, source);

        if (!target.hurtServer(level, source, damage)) {
            return false;
        }

        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            weapon.hurtEnemy(living, this);
        }
        EnchantmentHelper.doPostAttackEffects(level, target, source);
        this.setLastHurtMob(target);
        this.playAttackSound();
        return true;
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
    public void tick() {
        if (this.isAlive()) {
            net.minecraft.world.entity.LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                this.setSwellDir(-1);
                this.swell = 0;
                this.setTarget(null);
            }

            if (this.isArmed() || this.explodeCooldown > 0) {
                this.setSwellDir(-1);
                this.swell = 0;
            }

            if (this.explodeCooldown > 0) this.explodeCooldown--;
        }
        super.tick();
    }

    @Override
    public void explodeCreeper() {
        if (this.level().isClientSide()) return;

        Level.ExplosionInteraction interaction = Level.ExplosionInteraction.NONE;

        float f = this.isPowered() ? 2.0f : 1.0f;

        try {
            ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), (float) this.explosionRadius * f, false);
            this.level().getCraftServer().getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                // Keep the reusable pet entity alive while creating the explosion.
                boolean wasInvulnerable = this.isInvulnerable();
                this.dead = true;
                this.setInvulnerable(true);
                try {
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), interaction);
                }
                finally {
                    this.setInvulnerable(wasInvulnerable);
                    this.dead = false;
                }
                Reflex.invokeMethod(SPAWN_LINGERING_CLOUD, this);

                LivingEntity li = (LivingEntity) this.getBukkitEntity();
                li.setVelocity(li.getEyeLocation().add(1, 1, 1).getDirection().multiply(-1.5));
            }
        }
        finally {
            // Creeper.tick() owns fuse progression. Always reset here so a
            // cancelled or failed explosion cannot retrigger on the next tick.
            // The client does not receive the server-side swell value, only
            // this direction. One full negative step clears its local visual.
            this.setSwellDir(-this.maxSwell);
            this.swell = 0;
            this.explodeCooldown = 60;
        }
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        PetBrain.thunderHit(this, level, bolt);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel var0) {
        return false;
    }

    private static final class PetSwellGoal extends SwellGoal {

        private final CreeperPet creeper;

        private PetSwellGoal(@NotNull CreeperPet creeper) {
            super(creeper);
            this.creeper = creeper;
        }

        @Override
        public boolean canUse() {
            return !this.creeper.isArmed() && this.creeper.explodeCooldown <= 0 && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.creeper.isArmed() && this.creeper.explodeCooldown <= 0 && super.canContinueToUse();
        }
    }
}
