package su.nightexpress.combatpets.config;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.nightcore.config.FileConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginConfigMigration {

    private static final String RANK_PATH = "Pets.Amount_Per_Rank";
    private static final String CHORUS_ITEMS_PATH = "Food.chorus_fruits.Items";
    private static final Map<String, Integer> DEFAULT_RANK_LIMITS = createDefaultRankLimits();

    private PluginConfigMigration() {
    }

    public static void migrate(@NotNull PetsPlugin plugin) {
        FileConfig config = plugin.getConfig();
        boolean changed = migrateRankLimits(config);
        changed |= migrateChorusFruit(config);
        if (!changed) return;

        config.saveChanges();
        // NightPlugin initialized ConfigValue fields before enable(). Refresh
        // them so a migrated server uses the new limits immediately, including
        // after a PlugManX reload, instead of only after the next restart.
        config.initializeOptions(Config.class);
    }

    static boolean migrateChorusFruit(@NotNull FileConfig config) {
        if (!config.contains("Food.chorus_fruits")) return false;

        String legacyPath = CHORUS_ITEMS_PATH + ".popped_chorus_fruit";
        String chorusPath = CHORUS_ITEMS_PATH + ".chorus_fruit";
        if (config.contains(chorusPath)) return false;

        boolean legacyDefault = config.contains(legacyPath)
            && "POPPED_CHORUS_FRUIT".equalsIgnoreCase(config.getString(legacyPath + ".Item.Material"))
            && Double.compare(config.getDouble(legacyPath + ".Saturation"), 5D) == 0;
        if (legacyDefault) config.remove(legacyPath);

        config.set(chorusPath + ".Item.Material", "CHORUS_FRUIT");
        config.set(chorusPath + ".Saturation", 5D);
        return true;
    }

    static boolean migrateRankLimits(@NotNull FileConfig config) {
        boolean legacyDefaults = config.getInt(RANK_PATH + ".Default_Value") == -1
            && config.getInt(RANK_PATH + ".Values.default") == 10
            && config.getInt(RANK_PATH + ".Values.vip") == 20
            && config.getInt(RANK_PATH + ".Values.admin") == -1
            && !config.contains(RANK_PATH + ".Values.vipp")
            && !config.contains(RANK_PATH + ".Values.mvp")
            && !config.contains(RANK_PATH + ".Values.mvpp");

        boolean changed = false;
        if (legacyDefaults) {
            // Only the admin group should be unlimited in the new defaults.
            config.set(RANK_PATH + ".Default_Value", 10);
            changed = true;
        }

        for (Map.Entry<String, Integer> entry : DEFAULT_RANK_LIMITS.entrySet()) {
            String path = RANK_PATH + ".Values." + entry.getKey();
            if (config.contains(path)) continue;

            config.set(path, entry.getValue());
            changed = true;
        }
        return changed;
    }

    @NotNull
    public static Map<String, Integer> createDefaultRankLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("default", 10);
        limits.put("admin", -1);
        limits.put("vip", 20);
        limits.put("vipp", 30);
        limits.put("mvp", 40);
        limits.put("mvpp", 60);
        return limits;
    }
}
