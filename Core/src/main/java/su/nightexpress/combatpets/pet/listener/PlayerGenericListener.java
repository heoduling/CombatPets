package su.nightexpress.combatpets.pet.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Dismounting a normal mount also sends a player-position correction packet.
        // It is not a real owner teleport and must not recall the active pet.
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.DISMOUNT) return;

        Player player = event.getPlayer();
        // PlayerTeleportEvent fires before the move is committed. Run on the player's
        // entity scheduler one tick later so moveToOwner reads the destination state.
        PetScheduler.runAtEntityLater(this.plugin, player, () -> this.handleTeleport(player), 1L);
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

        Player player = event.getPlayer();
        ActivePet activePet = this.petManager.getPetByMob(entity);
        boolean chorusFruitFood = activePet != null
            && activePet.isOwner(player)
            && player.getInventory().getItemInMainHand().getType() == Material.CHORUS_FRUIT
            && activePet.getTemplate().isFood(player.getInventory().getItemInMainHand());

        // Let sulfur cube bucket pickup reach PlayerBucketEntityEvent. Cancelling
        // that event makes the server restore both the bucket and client entity.
        if (entity.getType().name().equals("SULFUR_CUBE") &&
            player.getInventory().getItemInMainHand().getType() == Material.BUCKET &&
            this.petManager.isPetEntity(entity)) return;

        if (this.plugin.getPetManager().tryInteract(player, entity)) {
            // The vanilla client follows an entity interaction with a separate
            // chorus-fruit use packet. A short native cooldown blocks that second
            // packet without changing normal chorus-fruit use away from the pet.
            if (chorusFruitFood) {
                player.setCooldown(Material.CHORUS_FRUIT, Math.max(2, player.getCooldown(Material.CHORUS_FRUIT)));
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetBucket(PlayerBucketEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && this.petManager.isPetEntity(entity)) {
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

    private void handleTeleport(@NotNull Player player) {
        ActivePet activePet = this.petManager.getPlayerPet(player);
        if (activePet == null) return;

        // The pet already moves with its owner when it is the player's vehicle.
        // Teleporting it to the passenger's elevated position creates a feedback
        // loop that repeatedly lifts tall mounts such as camels.
        if (player.getVehicle() == activePet.getEntity()) return;

        activePet.moveToOwner();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        this.petManager.despawnPet(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.petManager.despawnPet(event.getPlayer());
    }
}

