package su.nightexpress.combatpets.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
        runAtEntity(plugin, entity, task, null);
    }

    /** Schedules an entity task and reports retirement without touching entity state. */
    public static boolean runAtEntity(@NotNull PetsPlugin plugin,
                                      @NotNull Entity entity,
                                      @NotNull Runnable task,
                                      @Nullable Runnable retired) {
        if (folia) {
            return entity.getScheduler().run(plugin, scheduledTask -> task.run(), retired) != null;
        }
        Bukkit.getScheduler().runTask(plugin, task);
        return true;
    }

    public static void runAtEntityLater(@NotNull PetsPlugin plugin, @NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        runAtEntityLater(plugin, entity, task, null, delayTicks);
    }

    /** Schedules a delayed entity task and reports retirement without touching entity state. */
    public static boolean runAtEntityLater(@NotNull PetsPlugin plugin,
                                           @NotNull Entity entity,
                                           @NotNull Runnable task,
                                           @Nullable Runnable retired,
                                           long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (folia) {
            return entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), retired, delay) != null;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        return true;
    }
}
