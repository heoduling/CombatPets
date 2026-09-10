package su.nightexpress.combatpets.wardrobe.handler;

import org.bukkit.entity.Axolotl;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.config.Lang;

public class AxolotlVariantHandler extends EnumVariantHandler<Axolotl.Variant> {

    public AxolotlVariantHandler() {
        super(Axolotl.Variant.class);
    }

    @Override
    @Nullable
    public Axolotl.Variant read(@NotNull LivingEntity entity) {
        return entity instanceof Axolotl axolotl ? axolotl.getVariant() : null;
    }

    @Override
    @NotNull
    public String getLocalized(@NotNull Axolotl.Variant value) {
        return Lang.AXOLOTL_VARIANT.getLocalized(value);
    }

    @Override
    public boolean apply(@NotNull LivingEntity entity, @Nullable Axolotl.Variant value) {
        if (value == null || !(entity instanceof Axolotl axolotl)) return false;

        axolotl.setVariant(value);
        return true;
    }
}
