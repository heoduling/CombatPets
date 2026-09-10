package su.nightexpress.combatpets.nms.mc_26_2.pets.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetAutoTargetGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.combat.PetMeleeAttackGoal;
import su.nightexpress.combatpets.nms.mc_26_2.goals.follow.PetFollowOwnerGoal;

public class StriderPet extends Strider implements PetEntity {

    public StriderPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("strider"), level);
        // The vanilla client recalculates the cold state from its local block
        // environment every tick. Keep the synced no-AI flag enabled so the
        // client does not overwrite the server-owned temperature state.
        super.setNoAi(true);
        super.setSuffocating(false);
    }

    @Override
    public void setGoals() {
        this.targetSelector.addGoal(1, new PetAutoTargetGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PetFollowOwnerGoal(this));
        this.goalSelector.addGoal(4, new PetMeleeAttackGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData) {
        // Prevent vanilla jockey generation if this hook is ever invoked.
        return groupData;
    }

    @Override
    public void setSuffocating(boolean suffocating) {
        // The flag is also the source of the vanilla movement-speed penalty.
        super.setSuffocating(false);
    }

    @Override
    public boolean isNoAi() {
        // The server still runs the pet goals; only the vanilla client sees
        // the synced no-AI bit and skips its local cold-state recalculation.
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }
}
