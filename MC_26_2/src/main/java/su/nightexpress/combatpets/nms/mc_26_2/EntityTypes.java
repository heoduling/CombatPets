package su.nightexpress.combatpets.nms.mc_26_2;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

public final class EntityTypes {

    private EntityTypes() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> @NotNull EntityType<T> get(@NotNull String key) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(key));
        if (type == null) throw new IllegalArgumentException("Unknown entity type: " + key);
        return (EntityType<T>) type;
    }
}
