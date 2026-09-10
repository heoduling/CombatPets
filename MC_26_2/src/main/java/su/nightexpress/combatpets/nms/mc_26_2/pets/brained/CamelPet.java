package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class CamelPet extends Camel implements PetEntity {

    public CamelPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("camel"), level);
    }

    @Override
    public void setGoals() {
    }

    @Override
    protected Brain<Camel> makeBrain(Brain.Packed packed) {
        return PetBrain.makeBrain(this, packed);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        PetBrain.tick(this, level, this.getBrain());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData) {
        return groupData;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected boolean handleEating(Player player, ItemStack itemStack) {
        return false;
    }
}
