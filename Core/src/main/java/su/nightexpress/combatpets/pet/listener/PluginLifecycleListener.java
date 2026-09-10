package su.nightexpress.combatpets.pet.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.nightcore.manager.AbstractListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class PluginLifecycleListener extends AbstractListener<PetsPlugin> {

    private static final Set<String> PLUGMAN_ACTIONS = Set.of("unload", "disable", "reload", "restart");

    private final PetManager petManager;
    private volatile String  pendingCommand;
    private volatile String  replayCommand;
    private volatile boolean shutdownReady;
    private volatile boolean shutdownStarted;

    private Class<?> plugManApiClass;
    private Object   plugManGuard;
    private boolean  plugManGuardRegistered;

    public PluginLifecycleListener(@NotNull PetsPlugin plugin, @NotNull PetManager petManager) {
        super(plugin);
        this.petManager = petManager;
        this.plugManGuardRegistered = this.registerPlugManGuard();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().trim();
        if (command.equals(this.replayCommand)) {
            this.replayCommand = null;
            return;
        }
        if (!isLifecycleCommand(command)) return;

        if (this.plugManGuardRegistered) {
            this.pendingCommand = command;
            return;
        }

        event.setCancelled(true);
        this.prepareAndReplay(command);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().substring(1).trim();
        String permission = getPlugManPermission(command);
        if (permission == null || !event.getPlayer().hasPermission("plugman.help") ||
            !event.getPlayer().hasPermission(permission)) return;

        if (this.plugManGuardRegistered) {
            this.pendingCommand = command;
            return;
        }

        event.setCancelled(true);
        this.prepareAndReplay(command);
    }

    public void unregisterPlugManGuard() {
        if (!this.plugManGuardRegistered || this.plugManApiClass == null) return;

        try {
            Field field = this.plugManApiClass.getDeclaredField("gentleUnloads");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                synchronized (map) {
                    map.remove(this.plugin);
                }
            }
        }
        catch (ReflectiveOperationException exception) {
            this.plugin.getLogger().log(Level.WARNING, "无法解除 PlugManX 安全卸载回调。", exception);
        }
        finally {
            this.plugManGuardRegistered = false;
            this.plugManGuard = null;
            this.plugManApiClass = null;
        }
    }

    private boolean registerPlugManGuard() {
        Plugin plugMan = this.plugin.getServer().getPluginManager().getPlugin("PlugManX");
        if (plugMan == null) plugMan = this.plugin.getServer().getPluginManager().getPlugin("PlugMan");
        if (plugMan == null || !plugMan.isEnabled()) return false;

        try {
            ClassLoader loader = plugMan.getClass().getClassLoader();
            Class<?> guardClass = Class.forName("bukkit.com.rylinaux.plugman.api.GentleUnload", true, loader);
            this.plugManApiClass = Class.forName("bukkit.com.rylinaux.plugman.api.PlugManAPI", true, loader);
            this.plugManGuard = Proxy.newProxyInstance(loader, new Class<?>[]{guardClass}, (proxy, method, args) -> {
                if (method.getName().equals("askingForGentleUnload")) return this.askForGentleUnload();
                if (method.getName().equals("toString")) return "CombatPetsSafeUnload";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == (args == null ? null : args[0]);
                return null;
            });

            Method register = this.plugManApiClass.getMethod("pleaseAddMeToGentleUnload", Plugin.class, guardClass);
            return Boolean.TRUE.equals(register.invoke(null, this.plugin, this.plugManGuard));
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            this.plugin.getLogger().log(Level.WARNING, "PlugManX 安全卸载接口不可用，将使用命令事件兼容路径。", exception);
            this.plugManApiClass = null;
            this.plugManGuard = null;
            return false;
        }
    }

    private synchronized boolean askForGentleUnload() {
        if (this.shutdownReady) {
            this.unregisterPlugManGuard();
            return true;
        }
        if (this.shutdownStarted) return false;

        this.shutdownStarted = true;
        boolean started = this.petManager.prepareLifecycleShutdown(() -> {
            this.shutdownReady = true;

            String command = this.pendingCommand;
            if (command == null || command.isBlank()) return;

            this.replayCommand = command;
            this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), command);
        });
        if (!started) {
            this.shutdownStarted = false;
            this.plugin.info("插件已在执行其他停止流程，PlugManX 本次卸载已取消。");
        }
        return false;
    }

    private void prepareAndReplay(@NotNull String command) {
        boolean started = this.petManager.prepareLifecycleShutdown(() -> {
            this.replayCommand = command;
            this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), command);
        });
        if (!started) this.plugin.info("插件已在执行安全停止，已忽略重复命令。");
    }

    static boolean isLifecycleCommand(@NotNull String command) {
        String[] args = command.trim().split("\\s+");
        if (args.length < 3) return false;

        String root = withoutNamespace(args[0]);
        if (!root.equals("plugman") && !root.equals("plm")) return false;

        String target = args[2].replace("\"", "").replace("'", "");
        return PLUGMAN_ACTIONS.contains(args[1].toLowerCase(Locale.ROOT)) && target.equalsIgnoreCase("CombatPets");
    }

    private static String getPlugManPermission(@NotNull String command) {
        String[] args = command.trim().split("\\s+");
        if (args.length < 3) return null;

        String root = withoutNamespace(args[0]);
        String action = args[1].toLowerCase(Locale.ROOT);
        String target = args[2].replace("\"", "").replace("'", "");
        if ((!root.equals("plugman") && !root.equals("plm")) ||
            !PLUGMAN_ACTIONS.contains(action) || !target.equalsIgnoreCase("CombatPets")) {
            return null;
        }
        return "plugman." + action;
    }

    @NotNull
    private static String withoutNamespace(@NotNull String value) {
        String normalized = value.startsWith("/") ? value.substring(1) : value;
        int separator = normalized.lastIndexOf(':');
        return normalized.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
