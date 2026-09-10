package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class HoglinPet extends Hoglin implements PetEntity {
    public HoglinPet(@NotNull ServerLevel level) {
        super(EntityTypes.get("hoglin"), level);
        // A pet must keep its entity type and UUID.  Vanilla would otherwise
        // replace it with a new Zoglin after 300 ticks outside the Nether.
        this.setImmuneToZombification(true);
    }
    @Override public void setGoals() { }
    @Override protected Brain<Hoglin> makeBrain(Brain.Packed packed) { return PetBrain.makeBrain(this, packed); }

    /**
     * Hoglin's vanilla implementation selects activities through HoglinAi.
     * PetBrain supplies a different activity set, so the pet must select
     * FIGHT/IDLE from the pet attack memory after its brain has ticked.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        PetBrain.tick(this, level, this.getBrain());
    }

    @Override public boolean isImmuneToZombification() { return true; }
    @Override public boolean isConverting() { return false; }
    @Override public boolean canBeHunted() { return false; }
}
