package su.nightexpress.combatpets.nms.mc_26_2;

import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Illager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;
import su.nightexpress.nightcore.util.BukkitThing;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Read-only coverage report against the exact 26.2 API used by this module. */
public final class XPSourceCoverageCheck {

    private XPSourceCoverageCheck() {
    }

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Expected path to leveling.yml");
        ConfigCodecs.init(new CodecRegistry());

        FileConfig config = FileConfig.load(Path.of(args[0]));
        Set<String> configured = new TreeSet<>();
        config.getSection("XPSources").forEach(id -> configured.addAll(config.getStringSet("XPSources." + id + ".Mobs")));

        Set<String> defaultEligible = new TreeSet<>();
        Set<String> livingMobs = new TreeSet<>();
        for (EntityType type : EntityType.values()) {
            Class<? extends Entity> entityClass = type.getEntityClass();
            if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) continue;
            if (Player.class.isAssignableFrom(entityClass) || ArmorStand.class.isAssignableFrom(entityClass)) continue;

            String id = BukkitThing.toString(type);
            livingMobs.add(id);
            if (Animals.class.isAssignableFrom(entityClass)
                || Illager.class.isAssignableFrom(entityClass)
                || Monster.class.isAssignableFrom(entityClass)
                || Enemy.class.isAssignableFrom(entityClass)) {
                defaultEligible.add(id);
            }
        }

        Set<String> missingFromExisting = new TreeSet<>(defaultEligible);
        missingFromExisting.removeAll(configured);

        Set<String> omittedByDefaultGenerator = new TreeSet<>(livingMobs);
        omittedByDefaultGenerator.removeAll(defaultEligible);
        omittedByDefaultGenerator.removeAll(configured);

        System.out.println("CONFIGURED=" + configured.size());
        System.out.println("DEFAULT_ELIGIBLE=" + defaultEligible.size());
        System.out.println("MISSING_FROM_EXISTING=" + missingFromExisting);
        System.out.println("OMITTED_BY_DEFAULT_GENERATOR=" + omittedByDefaultGenerator);
    }
}
