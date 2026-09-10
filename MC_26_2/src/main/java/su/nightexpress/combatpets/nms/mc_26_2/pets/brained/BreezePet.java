package su.nightexpress.combatpets.nms.mc_26_2.pets.brained;

import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.pet.PetEntity;
import su.nightexpress.combatpets.nms.mc_26_2.EntityTypes;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetAI;
import su.nightexpress.combatpets.nms.mc_26_2.brain.PetBrain;

public class BreezePet extends Breeze implements PetEntity {
    public BreezePet(@NotNull ServerLevel level) { super(EntityTypes.get("breeze"), level); }
    @Override public void setGoals() { }
    @Override protected Brain<Breeze> makeBrain(Brain.Packed packed) { return PetBrain.makeBrain(this, packed); }
    @Override protected void customServerAiStep(ServerLevel level) { PetBrain.tick(this, level, this.getBrain()); }

    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        if (super.canAttack(target)) return true;

        return this.holder()
            .map(holder -> this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target
                           || PetAI.findTarget(this, holder) == target)
            .orElse(false);
    }
}
