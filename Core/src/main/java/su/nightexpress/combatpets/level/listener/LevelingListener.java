package su.nightexpress.combatpets.level.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.level.LevelingConfig;
import su.nightexpress.combatpets.level.LevelingManager;
import su.nightexpress.nightcore.manager.AbstractListener;
import su.nightexpress.nightcore.util.EntityUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LevelingListener extends AbstractListener<PetsPlugin> {

    private final LevelingManager manager;
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Double>> damageMap;

    public LevelingListener(@NotNull PetsPlugin plugin, @NotNull LevelingManager manager) {
        super(plugin);
        this.manager = manager;
        this.damageMap = new ConcurrentHashMap<>();
    }

    @NotNull
    private ConcurrentMap<UUID, Double> getDealtDamageMap(@NotNull LivingEntity victim) {
        return this.damageMap.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>());
    }

    private void addDealtDamage(@NotNull LivingEntity victim, @NotNull LivingEntity damager, double damage) {
        this.getDealtDamageMap(victim).merge(damager.getUniqueId(), damage, Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXPAbuseSpawnReason(CreatureSpawnEvent event) {
        if (LevelingConfig.DISABLE_XP_BY_SPAWN_REASON.get().contains(event.getSpawnReason())) {
            this.manager.setDropXP(event.getEntity(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpGainHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof LivingEntity damager)) return;
        if (!this.manager.shouldDropXP(victim)) return;
        if (this.manager.isDisabledWorld(victim.getWorld())) return;

        double damage = event.getFinalDamage();
        if (damage <= 0D) return;

        ActivePet activePet = PetEntityBridge.getByMobId(damager.getUniqueId());
        if (activePet == null) return;

        if (damage > victim.getHealth()) damage = victim.getHealth();

        this.addDealtDamage(victim, damager, damage);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExpGainKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Map<UUID, Double> damagers = this.damageMap.remove(victim.getUniqueId());
        if (!this.manager.shouldDropXP(victim)) return;
        if (this.manager.isDisabledWorld(victim.getWorld())) return;

        if (damagers == null || damagers.isEmpty()) return;

        double totalHealth = EntityUtil.getAttribute(victim, Attribute.MAX_HEALTH);
        if (totalHealth <= 0D) return;

        damagers.forEach((damagerId, damageDealt) -> {
            ActivePet pet = PetEntityBridge.getByMobId(damagerId);
            if (pet == null) return;

            double damagePercent = Math.min(1D, damageDealt / totalHealth);
            if (damagePercent <= 0) return;

            int xpReward = this.manager.rewardXP(pet, victim, damagePercent, event.getDroppedExp());

            if (!this.manager.useCustomXPTable()) {
                event.setDroppedExp(Math.max(0, event.getDroppedExp() - xpReward));
            }
        });
    }

    @EventHandler
    public void onEntityRemove(@NotNull EntityRemoveFromWorldEvent event) {
        this.damageMap.remove(event.getEntity().getUniqueId());
    }
}
