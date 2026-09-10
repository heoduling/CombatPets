package su.nightexpress.combatpets.level;

import su.nightexpress.combatpets.level.data.XPSource;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class LevelingSourceMigrationCheck {

    private static final Set<String> REQUIRED = Set.of(
        "allay", "bat", "copper_golem", "dolphin", "iron_golem", "snow_golem", "sulfur_cube",
        "villager", "wandering_trader", "cod", "glow_squid", "pufferfish", "salmon", "squid",
        "tadpole", "tropical_fish"
    );

    private LevelingSourceMigrationCheck() {
    }

    public static void main(String[] args) throws Exception {
        ConfigCodecs.init(new CodecRegistry());
        checkSupplementalDefaults();
        checkExistingConfigMigration();
        if (args.length == 1) checkRealConfigMigration(Path.of(args[0]));
    }

    private static void checkSupplementalDefaults() {
        Map<String, Integer> occurrences = countMobs(XPSource.getSupplementalDefaults());
        for (String mob : REQUIRED) {
            if (occurrences.getOrDefault(mob, 0) != 1) {
                throw new AssertionError(mob + " must occur exactly once in supplemental defaults");
            }
        }
        if (occurrences.containsKey("mannequin")) throw new AssertionError("mannequin must not receive an XP source");
    }

    private static void checkExistingConfigMigration() throws Exception {
        Path path = Files.createTempFile("combatpets-leveling-migration-", ".yml");
        try {
            Files.writeString(path, """
                XPSources:
                  custom:
                    Amount:
                      Min: 91
                      Max: 92
                    Chance: 93
                    Mobs: [allay, custom_mob]
                """);
            FileConfig config = FileConfig.load(path);
            Map<String, XPSource> defaults = XPSource.getSupplementalDefaults();
            if (!LevelingManager.mergeMissingDefaultXPSources(config, defaults, Set.of("mannequin"))) {
                throw new AssertionError("missing XP sources were not added");
            }
            if (LevelingManager.mergeMissingDefaultXPSources(config, defaults, Set.of("mannequin"))) {
                throw new AssertionError("XP source migration is not idempotent");
            }

            Map<String, Integer> occurrences = new HashMap<>();
            for (String id : config.getSection("XPSources")) {
                for (String mob : config.getStringSet("XPSources." + id + ".Mobs")) {
                    occurrences.merge(mob, 1, Integer::sum);
                }
            }
            for (String mob : REQUIRED) {
                if (occurrences.getOrDefault(mob, 0) != 1) {
                    throw new AssertionError(mob + " was not merged exactly once");
                }
            }
            assertEquals(91, config.getInt("XPSources.custom.Amount.Min"), "custom minimum");
            assertEquals(92, config.getInt("XPSources.custom.Amount.Max"), "custom maximum");
            assertEquals(93, config.getInt("XPSources.custom.Chance"), "custom chance");
            if (occurrences.containsKey("mannequin")) throw new AssertionError("mannequin was added");
        }
        finally {
            Files.deleteIfExists(path);
        }
    }

    private static void checkRealConfigMigration(Path sourcePath) throws Exception {
        Path copy = Files.createTempFile("combatpets-real-leveling-migration-", ".yml");
        try {
            Files.copy(sourcePath, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            FileConfig config = FileConfig.load(copy);
            LevelingManager.mergeMissingDefaultXPSources(config, XPSource.getDefaults(), Set.of("mannequin"));

            Map<String, Integer> occurrences = new HashMap<>();
            for (String id : config.getSection("XPSources")) {
                for (String mob : config.getStringSet("XPSources." + id + ".Mobs")) {
                    occurrences.merge(mob, 1, Integer::sum);
                }
            }
            for (String mob : REQUIRED) {
                if (occurrences.getOrDefault(mob, 0) != 1) {
                    throw new AssertionError("real config did not contain exactly one " + mob + " after migration");
                }
            }
            if (occurrences.containsKey("mannequin")) throw new AssertionError("real config migration added mannequin");
        }
        finally {
            Files.deleteIfExists(copy);
        }
    }

    private static Map<String, Integer> countMobs(Map<String, XPSource> sources) {
        Map<String, Integer> occurrences = new HashMap<>();
        sources.values().forEach(source -> source.getMobs().forEach(mob -> occurrences.merge(mob, 1, Integer::sum)));
        return occurrences;
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
