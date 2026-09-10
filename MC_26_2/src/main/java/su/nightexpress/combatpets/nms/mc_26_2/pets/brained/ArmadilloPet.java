package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class ArmadilloPet extends Armadillo implements PetEntity {
    public ArmadilloPet(@NotNull ServerLevel level) { super(EntityTypes.get("armadillo"), level); }
    @Override public void setGoals() { }
    @Override protected Brain<Armadillo> makeBrain(Brain.Packed packed) { return PetBrain.makeBrain(this, packed); }
    @Override protected void customServerAiStep(ServerLevel level) { PetBrain.tick(this, level, this.getBrain()); }
}
