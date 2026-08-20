package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class AxolotlPet extends Axolotl implements PetEntity {
    public AxolotlPet(@NotNull ServerLevel level) { super(EntityTypes.get("axolotl"), level); }
    @Override public void setGoals() { }
    @Override protected Brain<Axolotl> makeBrain(Brain.Packed packed) { return PetBrain.refreshBrain(this, super.makeBrain(packed)); }
}
