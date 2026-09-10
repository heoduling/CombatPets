package su.nightexpress.combatpets.util;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import su.nightexpress.combatpets.pet.AttributeRegistry;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public final class PetFoodDefaultsCheck {

    private PetFoodDefaultsCheck() {
    }

    public static void main(String[] args) throws Exception {
        ConfigCodecs.init(new CodecRegistry());

        Map<EntityType, String> expectedCategories = Map.of(
            EntityType.AXOLOTL, "tropical_fish",
            EntityType.ARMADILLO, "spider_eyes",
            EntityType.SNIFFER, "torchflower_seeds",
            EntityType.IRON_GOLEM, "iron_ingots",
            EntityType.ALLAY, "cakes",
            EntityType.BLAZE, "blaze_powder",
            EntityType.BREEZE, "feathers",
            EntityType.SNOW_GOLEM, "carrots"
        );
        expectedCategories.forEach((type, category) -> assertEquals(
            Set.of(category),
            PetCreator.getFoodCategories(type),
            type + " food category"
        ));

        Map<String, Material> expectedItems = Map.of(
            "tropical_fish", Material.TROPICAL_FISH,
            "torchflower_seeds", Material.TORCHFLOWER_SEEDS,
            "iron_ingots", Material.IRON_INGOT,
            "cakes", Material.CAKE,
            "blaze_powder", Material.BLAZE_POWDER,
            "feathers", Material.FEATHER,
            "chorus_fruits", Material.CHORUS_FRUIT
        );
        expectedItems.forEach((categoryId, material) -> assertEquals(
            material,
            PetCreator.getDefaultFoodMaterial(categoryId),
            categoryId + " material"
        ));

        Path oldConfig = Files.createTempFile("combatpets-old-axolotl-", ".yml");
        try {
            Files.writeString(oldConfig, """
                Saturation:
                  FoodCategories:
                  - custom_food
                Attributes:
                  Default:
                    max_health: 14.0
                    attack_damage: 9.5
                  Per_Aspect:
                    max_saturation: 0.0
                """);

            FileConfig config = FileConfig.load(oldConfig);
            if (!PetCreator.migrateSaturationDefaults(config, Set.of("tropical_fish"))) {
                throw new AssertionError("old config was not migrated");
            }
            config.saveChanges();

            FileConfig migrated = FileConfig.load(oldConfig);
            assertEquals(Set.of("custom_food", "tropical_fish"), migrated.getStringSet("Saturation.FoodCategories"), "merged food categories");
            assertDouble(10.5D, migrated.getDouble("Attributes.Default." + AttributeRegistry.MAX_SATURATION), "max saturation");
            assertDouble(1.4D, migrated.getDouble("Attributes.Per_Aspect." + AttributeRegistry.MAX_SATURATION), "per-aspect saturation");
            assertDouble(9.5D, migrated.getDouble("Attributes.Default." + AttributeRegistry.ATTACK_DAMAGE), "unrelated custom attribute");
            if (PetCreator.migrateSaturationDefaults(migrated, Set.of("tropical_fish"))) {
                throw new AssertionError("migration is not idempotent");
            }
        }
        finally {
            Files.deleteIfExists(oldConfig);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static void assertDouble(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0E-12D) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
