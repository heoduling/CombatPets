package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class CopperGolemPet extends CopperGolem implements PetEntity {

    private static final long IGNORE_WEATHERING_TICK = -2L;

    public CopperGolemPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("copper_golem"), level);
        this.nextWeatheringTick = IGNORE_WEATHERING_TICK;
    }

    @Override
    public void setGoals() {
    }

    @Override
    protected Brain<CopperGolem> makeBrain(Brain.Packed packed) {
        return PetBrain.makeBrain(this, packed);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        PetBrain.tick(this, level, this.getBrain());
    }

    @Override
    public void tick() {
        // Vanilla uses -2 as its own "ignore weathering" sentinel. Keeping it
        // set before the vanilla tick prevents both oxidation and the later
        // oxidized-golem-to-statue replacement that discards the pet entity.
        this.nextWeatheringTick = IGNORE_WEATHERING_TICK;
        super.tick();
    }
}
