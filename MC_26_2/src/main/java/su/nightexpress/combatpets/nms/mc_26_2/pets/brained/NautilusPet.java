package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class NautilusPet extends Nautilus implements PetEntity {

    private final PathNavigation waterNavigation;
    private final PathNavigation landNavigation;

    public NautilusPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("nautilus"), level);
        this.waterNavigation = this.navigation;
        this.landNavigation = new AmphibiousPathNavigation(this, level);
        this.moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.011F, 0.05F, false);
    }

    @Override
    public void setGoals() {
    }

    @Override
    protected Brain<Nautilus> makeBrain(Brain.Packed packed) {
        return PetBrain.makeBrain(this, packed);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.updateNavigation();
        PetBrain.tick(this, level, this.getBrain());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    private void updateNavigation() {
        PathNavigation next = PetAI.shouldUseWaterNavigation(this) ? this.waterNavigation : this.landNavigation;
        if (this.navigation == next) return;

        this.navigation.stop();
        this.navigation = next;
        this.getBrain().eraseMemory(MemoryModuleType.PATH);
        this.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected void handleAirSupply(ServerLevel level, int air) {
        this.setAirSupply(this.getMaxAirSupply());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.level().isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER;
    }
}
