package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class AllayPet extends Allay implements PetEntity {
    public AllayPet(@NotNull ServerLevel level) { super(EntityTypes.get("allay"), level); }
    @Override public void setGoals() { }
    @Override protected Brain<Allay> makeBrain(Brain.Packed packed) { return PetBrain.makeBrain(this, packed); }
    @Override protected void customServerAiStep(ServerLevel level) { PetBrain.tick(this, level, this.getBrain()); }
}
