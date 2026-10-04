package dev.tardyc.hayday.listener;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.hologram.Hologram;
import dev.tardyc.hayday.model.Building;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Hologrammer og bygningsdyr: klik, beskyttelse og oprydning.
 */
public final class EntityListener implements Listener {

    private final HayDayPlugin plugin;

    public EntityListener(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isOurs(Entity entity) {
        return plugin.getHolograms().getByEntity(entity) != null || plugin.getFarm().getBuildingByAnimal(entity) != null;
    }

    /** Andre plugins (fx WorldGuard) må ikke blokere vores hologrammer og dyr. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent event) {
        if (plugin.getHolograms().isSpawning() && event.isCancelled()) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    private void handleClick(Player player, Entity entity, EquipmentSlot hand, PlayerInteractEntityEvent event) {
        Hologram hologram = plugin.getHolograms().getByEntity(entity);
        if (hologram != null) {
            event.setCancelled(true);
            if (hand == EquipmentSlot.HAND && plugin.getClickGuard().tryClick(player)) {
                hologram.click(player);
            }
            return;
        }
        Building building = plugin.getFarm().getBuildingByAnimal(entity);
        if (building != null) {
            event.setCancelled(true);
            if (hand == EquipmentSlot.HAND && plugin.getClickGuard().tryClick(player)) {
                plugin.getService().clickBuilding(player, building);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Fyrværkeri fra level up må ikke skade nogen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework && plugin.getAnimations().isAnimationEntity(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    /** Varer der flyver hen til spilleren er kun en animation - de kan ikke samles op. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (plugin.getAnimations().isAnimationEntity(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (plugin.getAnimations().isAnimationEntity(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (plugin.getAnimations().isAnimationEntity(event.getEntity()) || plugin.getAnimations().isAnimationEntity(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        // Høns lægger ikke rigtige æg - de laves i hønsehuset
        if (plugin.getFarm().getBuildingByAnimal(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (plugin.getFarm().getBuildingByAnimal(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        if (isOurs(event.getEntered())) {
            event.setCancelled(true);
        }
    }

    /** Havnens båd kan ikke slås i stykker. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (isOurs(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (isOurs(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.getHolograms().removeStray(event.getEntities());
        plugin.getProps().removeStray(event.getEntities());
        plugin.getFarm().removeStray(event.getEntities());
        for (Entity entity : event.getEntities()) {
            // Animationer gemmes aldrig, så alt med dette mærke er rester efter et nedbrud
            if (plugin.getAnimations().isAnimationEntity(entity)) {
                entity.remove();
            }
        }
    }
}
