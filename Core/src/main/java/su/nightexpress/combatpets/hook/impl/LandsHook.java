package su.nightexpress.combatpets.hook.impl;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.Flags;
import me.angeschossen.lands.api.flags.type.RoleFlag;
import me.angeschossen.lands.api.land.LandWorld;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;

import java.util.UUID;

public final class LandsHook {

    private final PetsPlugin       plugin;
    private final Plugin           landsPlugin;
    private final LandsIntegration integration;

    private LandsHook(@NotNull PetsPlugin plugin, @NotNull Plugin landsPlugin) {
        this.plugin = plugin;
        this.landsPlugin = landsPlugin;
        this.integration = LandsIntegration.of(plugin);
    }

    @Nullable
    public static LandsHook create(@NotNull PetsPlugin plugin) {
        Plugin landsPlugin = plugin.getPluginManager().getPlugin("Lands");
        if (landsPlugin == null || !landsPlugin.isEnabled()) return null;

        try {
            return new LandsHook(plugin, landsPlugin);
        }
        catch (LinkageError | RuntimeException exception) {
            plugin.warn("Lands 兼容功能加载失败，本次启动将不启用领地权限联动。");
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to initialize Lands integration.", exception);
            return null;
        }
    }

    public boolean canSummon(@NotNull Player owner, @NotNull Location location) {
        return this.hasRoleFlag(owner.getUniqueId(), location, Flags.INTERACT_GENERAL);
    }

    public boolean canAttack(@NotNull UUID ownerId, @NotNull LivingEntity victim) {
        RoleFlag flag = switch (getAttackTargetType(victim)) {
            case PLAYER -> Flags.ATTACK_PLAYER;
            case MONSTER -> Flags.ATTACK_MONSTER;
            case ANIMAL -> Flags.ATTACK_ANIMAL;
            case OTHER -> Flags.BLOCK_BREAK;
        };

        return this.hasRoleFlag(ownerId, victim.getLocation(), flag);
    }

    @NotNull
    static AttackTargetType getAttackTargetType(@NotNull LivingEntity victim) {
        if (victim instanceof Player) return AttackTargetType.PLAYER;
        EntityType type = victim.getType();
        if (victim instanceof ArmorStand || type.name().equals("MANNEQUIN")) return AttackTargetType.OTHER;
        if (type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM) return AttackTargetType.MONSTER;
        return victim instanceof Enemy ? AttackTargetType.MONSTER : AttackTargetType.ANIMAL;
    }

    private boolean hasRoleFlag(@NotNull UUID ownerId, @NotNull Location location, @NotNull RoleFlag flag) {
        if (!this.isAvailable()) return true;

        LandWorld landWorld = this.integration.getWorld(location.getWorld());
        if (landWorld == null) return true;

        LandPlayer landPlayer = this.integration.getLandPlayer(ownerId);
        return landPlayer == null
            ? landWorld.hasRoleFlag(ownerId, location, flag)
            : landWorld.hasRoleFlag(landPlayer, location, flag, null, false);
    }

    private boolean isAvailable() {
        Plugin current = this.plugin.getPluginManager().getPlugin("Lands");
        return current == this.landsPlugin && current.isEnabled();
    }

    enum AttackTargetType {
        PLAYER,
        MONSTER,
        ANIMAL,
        OTHER
    }
}
