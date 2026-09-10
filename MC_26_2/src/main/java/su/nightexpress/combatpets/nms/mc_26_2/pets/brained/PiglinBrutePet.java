package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class PiglinBrutePet extends PiglinBrute implements PetEntity {
    public PiglinBrutePet(@NotNull ServerLevel level) {
        super(EntityTypes.get("piglin_brute"), level);
        // Keep a summoned pet from being replaced by a vanilla Zombified Piglin.
        this.setImmuneToZombification(true);
    }
    @Override public void setGoals() { }
    @Override protected Brain<PiglinBrute> makeBrain(Brain.Packed packed) { return PetBrain.makeBrain(this, packed); }
    @Override protected void customServerAiStep(ServerLevel level) { PetBrain.tick(this, level, this.getBrain()); }
    @Override public boolean canHunt() { return false; }
    @Override public boolean isImmuneToZombification() { return true; }
    @Override public boolean isConverting() { return false; }
}
