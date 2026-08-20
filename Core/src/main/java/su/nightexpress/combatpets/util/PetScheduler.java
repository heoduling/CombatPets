package su.nightexpress.combatpets.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;

/** Schedules live entity access in the entity's owning context. */
public final class PetScheduler {

    private static boolean folia;
    private static boolean initialized;

    private PetScheduler() {
    }

    public static void initialize() {
        if (initialized) return;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        }
        catch (ClassNotFoundException ignored) {
            folia = false;
        }
        initialized = true;
    }

    public static void runAtEntity(@NotNull PetsPlugin plugin, @NotNull Entity entity, @NotNull Runnable task) {
        if (folia) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runAtEntityLater(@NotNull PetsPlugin plugin, @NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (folia) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delay);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
}
