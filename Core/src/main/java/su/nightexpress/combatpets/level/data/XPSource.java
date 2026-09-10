package su.nightexpress.combatpets.level.data;

import org.bukkit.entity.*;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.BukkitThing;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.random.Rnd;
import su.nightexpress.nightcore.util.wrapper.UniInt;

import java.util.*;

public class XPSource {

    private final UniInt      amount;
    private final double      chance;
    private final Set<String> mobs;

    public XPSource(@NotNull UniInt amount, double chance, @NotNull Set<String> mobs) {
        this.amount = amount;
        this.chance = chance;
        this.mobs = mobs;
    }

    @NotNull
    public static XPSource read(@NotNull FileConfig config, @NotNull String path) {
        UniInt amount = UniInt.read(config, path + ".Amount");
        double chance = ConfigValue.create(path + ".Chance", 50).read(config);
        Set<String> mobs = ConfigValue.create(path + ".Mobs", Lists.newSet()).onRead(set -> Lists.modify(set, String::toLowerCase)).read(config);

        return new XPSource(amount, chance, mobs);
    }

    public void write(@NotNull FileConfig config, @NotNull String path) {
        this.amount.write(config, path + ".Amount");
        config.set(path + ".Chance", this.chance);
        config.set(path + ".Mobs", this.mobs);
    }

    public boolean checkChance() {
        return Rnd.chance(this.chance);
    }

    public boolean isMob(@NotNull String name) {
        return this.mobs.contains(name.toLowerCase()) || this.mobs.contains(Placeholders.WILDCARD);
    }

    @NotNull
    public UniInt getAmount() {
        return amount;
    }

    public double getChance() {
        return chance;
    }

    @NotNull
    public Set<String> getMobs() {
        return mobs;
    }

    @NotNull
    public static Map<String, XPSource> getDefaults() {
        Map<String, XPSource> map = new LinkedHashMap<>();

        Set<String> animalNames = new HashSet<>();
        Set<String> illagerNames = new HashSet<>();
        Set<String> monsterNames = new HashSet<>();
        Set<String> otherNames = new HashSet<>();

        for (EntityType entityType : EntityType.values()) {
            Class<? extends Entity> clazz = entityType.getEntityClass();
            if (clazz == null || !LivingEntity.class.isAssignableFrom(clazz)) continue;

            String name = BukkitThing.toString(entityType);

            if (Animals.class.isAssignableFrom(clazz)) {
                animalNames.add(name);
            }
            else if (Illager.class.isAssignableFrom(clazz)) {
                illagerNames.add(name);
            }
            else if (Monster.class.isAssignableFrom(clazz)) {
                monsterNames.add(name);
            }
            else if (Enemy.class.isAssignableFrom(clazz)) {
                otherNames.add(name);
            }
        }

        map.put("animals", new XPSource(UniInt.of(5, 25), 35, animalNames));
        map.put("monsters", new XPSource(UniInt.of(20, 60), 50, monsterNames));
        map.put("illagers", new XPSource(UniInt.of(30, 70), 70, illagerNames));
        map.put("others", new XPSource(UniInt.of(25, 50), 65, otherNames));
        map.putAll(getSupplementalDefaults());
        return map;
    }

    @NotNull
    public static Map<String, XPSource> getSupplementalDefaults() {
        Map<String, XPSource> map = new LinkedHashMap<>();
        map.put("passive_specials", new XPSource(UniInt.of(3, 12), 30, mobSet(
            "allay", "bat", "copper_golem", "snow_golem", "villager", "wandering_trader"
        )));
        map.put("aquatic_wildlife", new XPSource(UniInt.of(4, 16), 35, mobSet(
            "cod", "glow_squid", "salmon", "squid", "tadpole", "tropical_fish"
        )));
        map.put("aquatic_defensive", new XPSource(UniInt.of(7, 22), 40, mobSet(
            "dolphin", "pufferfish"
        )));
        map.put("iron_golem", new XPSource(UniInt.of(25, 60), 60, mobSet("iron_golem")));
        map.put("sulfur_cube", new XPSource(UniInt.of(4, 14), 35, mobSet("sulfur_cube")));
        return map;
    }

    @NotNull
    private static Set<String> mobSet(@NotNull String... names) {
        return new LinkedHashSet<>(List.of(names));
    }
}
