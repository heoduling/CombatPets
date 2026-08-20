package su.nightexpress.combatpets.pet.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.manager.AbstractListener;

public class PlayerGenericListener extends AbstractListener<PetsPlugin> {

    private final PetManager petManager;

    public PlayerGenericListener(@NotNull PetsPlugin plugin, @NotNull PetManager petManager) {
        super(plugin);
        this.petManager = petManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.plugin.getPetNMS().listenForTeleports(player, target ->
            PetScheduler.runAtEntity(this.plugin, target, () -> this.handleTeleportPacket(target)));
    }

//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onPetClaimEgg(PlayerInteractEvent event) {
//        ItemStack egg = event.getItem();
//        if (egg == null || egg.getType().isAir()) return;
//        if (event.useItemInHand() == Event.Result.DENY) return;
//
//        Template template = this.petManager.getTemplate(egg);
//        if (template == null) return;
//
//        Tier tier = this.petManager.getTierByItem(egg).orElse(null);
//        if (tier == null) return;
//
//        event.setUseInteractedBlock(Event.Result.DENY);
//        event.setUseItemInHand(Event.Result.DENY);
//
//        Player player = event.getPlayer();
//        if (this.petManager.tryClaimPet(player, tier, template)) {
//            egg.setAmount(egg.getAmount() - 1);
//        }
//    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract2(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;

        if (this.plugin.getPetManager().tryInteract(event.getPlayer(), entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract3(PlayerArmorStandManipulateEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (this.plugin.getPetManager().tryInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ActivePet activePet = this.petManager.getPlayerPet(player);
        if (activePet == null) return;

        PetScheduler.runAtEntity(this.plugin, activePet.getEntity(), () ->
            this.plugin.getPetNMS().sneak(activePet.getEntity(), event.isSneaking()));
    }

    private void handleTeleportPacket(@NotNull Player player) {
        ActivePet activePet = this.petManager.getPlayerPet(player);
        if (activePet == null) return;

        java.util.UUID playerWorldId = player.getWorld().getUID();
        PetScheduler.runAtEntity(this.plugin, activePet.getEntity(), () -> {
            if (!activePet.getEntity().getWorld().getUID().equals(playerWorldId)) {
                this.petManager.removePet(activePet);
                PetScheduler.runAtEntity(this.plugin, player, () ->
                    this.petManager.spawnPet(player, activePet.getTier(), activePet.getTemplate()));
                return;
            }

            PetScheduler.runAtEntityLater(this.plugin, activePet.getEntity(), activePet::moveToOwner, 5L);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        this.petManager.despawnPet(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getPetNMS().stopListeningForTeleports(event.getPlayer());
        this.petManager.despawnPet(event.getPlayer());
    }
}

