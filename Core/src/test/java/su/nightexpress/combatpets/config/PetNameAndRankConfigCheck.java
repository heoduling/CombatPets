package su.nightexpress.combatpets.config;

import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class PetNameAndRankConfigCheck {

    private PetNameAndRankConfigCheck() {
    }

    public static void main(String[] args) throws Exception {
        ConfigCodecs.init(new CodecRegistry());
        checkNameNormalization();
        if (args.length == 2) checkWordFiles(Path.of(args[0]), Path.of(args[1]));
        checkRankMigration();
        checkChorusFruitMigration();
    }

    private static void checkWordFiles(Path configPath, Path lexiconDirectory) throws Exception {
        FileConfig config = FileConfig.load(configPath);
        if (!config.getBoolean("Use_BuiltIn_Word_List")) throw new AssertionError("built-in word list is not enabled");
        if (!config.getStringSet("ForbiddenWords").isEmpty()) {
            throw new AssertionError("bundled words must not be duplicated in the editable YAML");
        }

        Set<String> words = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.list(lexiconDirectory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".txt")).toList()) {
                Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(PetNameFilter::normalize)
                    .filter(word -> !word.isEmpty())
                    .forEach(words::add);
            }
        }
        if (words.size() < 2_850) throw new AssertionError("built-in word list is unexpectedly small: " + words.size());
        if (!words.contains("fuck")) {
            throw new AssertionError("English seed word was not loaded");
        }
        if (!words.contains("k粉")) {
            throw new AssertionError("full-width/case normalization seed was not loaded");
        }
        if (words.contains("ass")) {
            throw new AssertionError("over-broad legacy word 'ass' must not be in the new defaults");
        }

        if (!PetNameFilter.isForbidden("Ｆ-u_C K测试", words, Set.of())) {
            throw new AssertionError("obfuscated forbidden name bypassed normalization");
        }
        if (PetNameFilter.isForbidden("测试名", words, Set.of("测试名"))) {
            throw new AssertionError("exact allow-list name was rejected");
        }
        if (!PetNameFilter.isForbidden("前缀测试名后缀", Set.of("测试名"), Set.of("测试名"))) {
            throw new AssertionError("allow-list entry was incorrectly usable as a substring bypass");
        }

        Path oldConfig = Files.createTempFile("combatpets-old-word-list-", ".yml");
        try {
            Files.writeString(oldConfig, "ConfigVersion: 1\nForbiddenWords: [fuck, 服主自定义词]\n");
            FileConfig legacy = FileConfig.load(oldConfig);
            Set<String> migrated = PetNameFilter.migrateStandaloneConfig(legacy, words);
            if (migrated.contains("fuck") || !migrated.contains("服主自定义词")) {
                throw new AssertionError("standalone word-list migration lost custom data or retained bundled duplicates");
            }
            assertEquals(2, legacy.getInt("ConfigVersion"), "word-list config version");
            if (!legacy.getBoolean("Use_BuiltIn_Word_List")) throw new AssertionError("built-in list flag was not added");
        }
        finally {
            Files.deleteIfExists(oldConfig);
        }
    }

    private static void checkNameNormalization() {
        assertEquals("fuck测试", PetNameFilter.normalize("<red> Ｆ-U C_K 测试 </red>"), "name normalization");
        assertEquals("文明名称", PetNameFilter.normalize("文明 名称"), "Chinese normalization");
    }

    private static void checkRankMigration() throws Exception {
        Path oldConfig = Files.createTempFile("combatpets-old-ranks-", ".yml");
        try {
            Files.writeString(oldConfig, """
                Pets:
                  Amount_Per_Rank:
                    Mode: RANK
                    Permission_Prefix: pets.amount.
                    Default_Value: -1
                    Values:
                      default: 10
                      vip: 20
                      admin: -1
                """);

            FileConfig config = FileConfig.load(oldConfig);
            if (!PluginConfigMigration.migrateRankLimits(config)) {
                throw new AssertionError("legacy rank defaults were not migrated");
            }
            config.saveChanges();

            FileConfig migrated = FileConfig.load(oldConfig);
            assertEquals(10, migrated.getInt("Pets.Amount_Per_Rank.Default_Value"), "unknown-group fallback");
            assertEquals(10, migrated.getInt("Pets.Amount_Per_Rank.Values.default"), "default rank");
            assertEquals(-1, migrated.getInt("Pets.Amount_Per_Rank.Values.admin"), "admin rank");
            assertEquals(20, migrated.getInt("Pets.Amount_Per_Rank.Values.vip"), "vip rank");
            assertEquals(30, migrated.getInt("Pets.Amount_Per_Rank.Values.vipp"), "vipp rank");
            assertEquals(40, migrated.getInt("Pets.Amount_Per_Rank.Values.mvp"), "mvp rank");
            assertEquals(60, migrated.getInt("Pets.Amount_Per_Rank.Values.mvpp"), "mvpp rank");
            if (PluginConfigMigration.migrateRankLimits(migrated)) {
                throw new AssertionError("rank migration is not idempotent");
            }
        }
        finally {
            Files.deleteIfExists(oldConfig);
        }
    }

    private static void checkChorusFruitMigration() throws Exception {
        Path oldConfig = Files.createTempFile("combatpets-old-chorus-fruit-", ".yml");
        try {
            Files.writeString(oldConfig, """
                Food:
                  chorus_fruits:
                    Name: 紫颂果
                    Items:
                      popped_chorus_fruit:
                        Item:
                          Material: POPPED_CHORUS_FRUIT
                        Saturation: 5.0
                """);

            FileConfig config = FileConfig.load(oldConfig);
            if (!PluginConfigMigration.migrateChorusFruit(config)) {
                throw new AssertionError("legacy chorus fruit was not migrated");
            }
            config.saveChanges();

            FileConfig migrated = FileConfig.load(oldConfig);
            if (migrated.contains("Food.chorus_fruits.Items.popped_chorus_fruit")) {
                throw new AssertionError("legacy popped chorus fruit was retained");
            }
            assertEquals("CHORUS_FRUIT", migrated.getString("Food.chorus_fruits.Items.chorus_fruit.Item.Material"), "chorus fruit material");
            if (Double.compare(5D, migrated.getDouble("Food.chorus_fruits.Items.chorus_fruit.Saturation")) != 0) {
                throw new AssertionError("chorus fruit saturation was changed");
            }
            if (PluginConfigMigration.migrateChorusFruit(migrated)) {
                throw new AssertionError("chorus fruit migration is not idempotent");
            }
        }
        finally {
            Files.deleteIfExists(oldConfig);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
