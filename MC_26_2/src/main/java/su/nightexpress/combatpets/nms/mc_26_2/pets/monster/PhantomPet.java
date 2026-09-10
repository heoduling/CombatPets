package su.nightexpress.combatpets.nms.mc_26_2.pets.monster;

import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityTargetEvent;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.nightcore.util.Reflex;

import java.lang.reflect.Constructor;
import java.util.EnumSet;

public class PhantomPet extends Phantom implements PetEntity {

    private static final String MOVE_TARGET_POINT = "moveTargetPoint";
    private static final String ANCHOR_POINT = "anchorPoint";
    private static final String ATTACK_PHASE = "attackPhase";
    private static final int FOLLOW_UPDATE_INTERVAL_TICKS = 10;
    private static final double COMBAT_FOLLOW_RANGE = 64D;

    private static final Constructor<?> CONSTR_I;
    private static final Constructor<?> CONSTR_C;

    static {
        Class<?> CLASS_I = Reflex.safeInnerClass(Phantom.class.getName(), "PhantomSweepAttackGoal"); // i
        Class<?> CLASS_C = Reflex.safeInnerClass(Phantom.class.getName(), "PhantomAttackStrategyGoal"); // c

        CONSTR_I = Reflex.getConstructor(CLASS_I, Phantom.class);
        CONSTR_C = Reflex.getConstructor(CLASS_C, Phantom.class);
    }

    public PhantomPet(@NotNull ServerLevel world) {
        super(EntityTypes.get("phantom"), world);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(COMBAT_FOLLOW_RANGE);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        if (CONSTR_C != null) {
            this.goalSelector.addGoal(1, (Goal) Reflex.invokeConstructor(CONSTR_C, this));
        }
        if (CONSTR_I != null) {
            this.goalSelector.addGoal(2, (Goal) Reflex.invokeConstructor(CONSTR_I, this));
        }
        this.goalSelector.addGoal(3, new PathfinderFollowOwner());
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, PetAI.ARMOR_SLOTS);
    }

    abstract class PhantomMoveTargetGoal extends Goal {

        protected Class<?> phaseClass;
        protected Object[] phaseValues;

        public PhantomMoveTargetGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));

            Object phase = this.getAttackPhase();
            if (phase == null) {
                throw new IllegalStateException("Unable to access Phantom attack phase on Minecraft 26.2.");
            }

            this.phaseClass = phase.getClass(); // Get private enum class
            this.phaseValues = phaseClass.getEnumConstants(); // Get enum values
        }

        protected boolean touchingTarget() {
            Vec3 moveTargetPoint = (Vec3) Reflex.getFieldValue(PhantomPet.this, MOVE_TARGET_POINT);
            if (moveTargetPoint == null) return false;

            return moveTargetPoint.distanceToSqr(PhantomPet.this.getX(), PhantomPet.this.getY(), PhantomPet.this.getZ()) < 4.0;
        }

        protected Object getAttackPhase() {
            return Reflex.getFieldValue(PhantomPet.this, ATTACK_PHASE);
            // 0 - CIRCLE
            // 1 - SWOOP
        }

//        protected void setAttackPhase(int i) {
//            Reflex.setFieldValue(PhantomPet.this, ATTACK_PHASE, phaseValues[i]);
//        }
    }

    final class PathfinderFollowOwner extends PhantomMoveTargetGoal {

        private float distance;
        private float height;
        private float clockWise;
        private float angle;

        private PathfinderFollowOwner() {
        }

        @Override
        public boolean canUse() {
            if (PetAI.getLocalOwner(PhantomPet.this.getHolder()) == null) {
                PhantomPet.this.getHolder().moveToOwner();
                return false;
            }
            return PhantomPet.this.getTarget() == null || getAttackPhase() == phaseValues[0];
        }

        @Override
        public void start() {
            this.distance = 5.0f + PhantomPet.this.getRandom().nextFloat() * 10.0f;
            this.height = -4.0f + PhantomPet.this.getRandom().nextFloat() * 9.0f;
            this.clockWise = (PhantomPet.this.getRandom().nextBoolean() ? 1.0f : -1.0f);
            this.selectNext();
        }

        @Override
        public void tick() {
            ServerPlayer owner = PetAI.getLocalOwner(PhantomPet.this.getHolder());
            if (owner == null) {
                PhantomPet.this.setTarget(null);
                PhantomPet.this.getHolder().moveToOwner();
                return;
            }
            if (!PhantomPet.this.level().equals(owner.level()) || PetAI.isOwnerTooFar(PhantomPet.this, owner)) {
                PhantomPet.this.setTarget(null);
                PhantomPet.this.getHolder().moveToOwner();
                return;
            }

            if (PhantomPet.this.getRandom().nextInt(350) == 0) {
                this.height = -4.0f + PhantomPet.this.getRandom().nextFloat() * 9.0f;
            }

            if (PhantomPet.this.getRandom().nextInt(250) == 0) {
                ++this.distance;
                if (this.distance > 15.0f) {
                    this.distance = 5.0f;
                    this.clockWise = -this.clockWise;
                }
            }

            if (PhantomPet.this.getTarget() == null && PhantomPet.this.tickCount % FOLLOW_UPDATE_INTERVAL_TICKS == 0) {
                this.angle = PhantomPet.this.getRandom().nextFloat() * 2.0f * 3.1415927f;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            Vec3 moveTargetPoint = (Vec3) Reflex.getFieldValue(PhantomPet.this, MOVE_TARGET_POINT);
            if (moveTargetPoint == null) return;

            if (moveTargetPoint.y < PhantomPet.this.getY() && !PhantomPet.this.level().isEmptyBlock(PhantomPet.this.blockPosition().below(1))) {
                this.height = Math.max(1.0f, this.height);
                this.selectNext();
            }
            if (moveTargetPoint.y > PhantomPet.this.getY() && !PhantomPet.this.level().isEmptyBlock(PhantomPet.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0f, this.height);
                this.selectNext();
            }
        }

        private void selectNext() {
            LivingEntity follow = getTarget();

            BlockPos anchor = (BlockPos) Reflex.getFieldValue(PhantomPet.this, ANCHOR_POINT);
            if (anchor == null || BlockPos.ZERO.equals(anchor)) {
                anchor = PhantomPet.this.blockPosition().above(5);
                Reflex.setFieldValue(PhantomPet.this, ANCHOR_POINT, anchor);
            }

            Vec3 point;
            if (follow == null || !follow.isAlive() || getAttackPhase() == phaseValues[0]) {
                follow = PetAI.getLocalOwner(PhantomPet.this.getHolder());
                if (follow == null) {
                    PhantomPet.this.getHolder().moveToOwner();
                    return;
                }
                point = new Vec3(follow.getX(), follow.getY() + 2.5, follow.getZ());
            }
            else {
                this.angle += this.clockWise * 15.0F * 0.017453292F;
                point = Vec3.atLowerCornerOf(anchor).add(this.distance * Mth.cos(this.angle), -4.0F + this.height, this.distance * Mth.sin(this.angle));
            }
            Reflex.setFieldValue(PhantomPet.this, MOVE_TARGET_POINT, point);
        }
    }
}
