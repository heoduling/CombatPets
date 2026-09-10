package su.nightexpress.combatpets.data;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.data.impl.PetUser;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.db.AbstractUserManager;
import su.nightexpress.nightcore.util.Players;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserManager extends AbstractUserManager<PetsPlugin, PetUser> {

    private final AtomicBoolean active = new AtomicBoolean(true);

    public UserManager(@NotNull PetsPlugin plugin, @NotNull DataHandler dataHandler) {
        super(plugin, dataHandler);
    }

    @Override
    @NotNull
    public PetUser create(@NotNull UUID uuid, @NotNull String name) {
        return PetUser.create(uuid, name);
    }

    /**
     * Nightcore only loads online users that already have a database row. On a
     * plugin hot-load, a player who has never used CombatPets therefore gets a
     * transient user from getOrFetch(Player); changes to that object are never
     * cached or inserted. Initialize those players explicitly and keep all
     * database work away from the Folia entity thread.
     */
    @Override
    public void loadOnline() {
        Players.getOnline().forEach(this::initializeOnlinePlayer);
    }

    private void initializeOnlinePlayer(@NotNull Player player) {
        PetScheduler.runAtEntity(this.plugin, player, () -> {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            if (this.getLoaded(uuid) != null || !this.active.get()) return;

            this.plugin.runTaskAsync(() -> this.loadOrCreate(uuid, name));
        });
    }

    private void loadOrCreate(@NotNull UUID uuid, @NotNull String name) {
        if (!this.active.get() || this.getLoaded(uuid) != null) return;

        PetUser user = this.getFromDatabase(uuid);
        if (user == null) {
            this.addInDatabase(this.create(uuid, name));
            user = this.getFromDatabase(uuid);
        }

        if (!this.active.get()) return;
        if (user == null) {
            this.plugin.error("Unable to initialize persistent pet data for online player '" + name + "' (" + uuid + ").");
            return;
        }

        user.setName(name);
        user.onLoad();
        this.cachePermanent(user);
    }

    @Override
    protected void onShutdown() {
        this.active.set(false);
        super.onShutdown();
    }
}
