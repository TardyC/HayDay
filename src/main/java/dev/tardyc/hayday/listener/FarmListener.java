package dev.tardyc.hayday.listener;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.util.PlaceableItems;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Spillerens interaktion med marker og bygninger: placering, klik og nedbrydning.
 */
public final class FarmListener implements Listener {

    private final HayDayPlugin plugin;

    public FarmListener(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        // Pladsen over en mark er reserveret til afgrøden
        if (plugin.getFarm().getFieldByCrop(block) != null || plugin.getFarm().getBuildingAt(block) != null) {
            event.setCancelled(true);
            return;
        }
        ItemStack item = event.getItemInHand();
        if (PlaceableItems.isField(item)) {
            if (!plugin.getService().placeField(player, block)) {
                event.setCancelled(true);
            }
            return;
        }
        String buildingId = PlaceableItems.getBuildingId(item);
        if (buildingId != null) {
            BuildingType type = plugin.getBuildings().get(buildingId);
            if (type == null) {
                plugin.getMessages().send(player, "general.unknown-building", "building", buildingId);
                event.setCancelled(true);
                return;
            }
            if (!plugin.getService().placeBuilding(player, type, block)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Field field = plugin.getFarm().getFieldAt(block);
        if (field != null) {
            event.setCancelled(true);
            if (plugin.getClickGuard().tryClick(player)) {
                plugin.getService().breakField(player, field);
            }
            return;
        }
        Building building = plugin.getFarm().getBuildingAt(block);
        if (building != null) {
            event.setCancelled(true);
            if (plugin.getClickGuard().tryClick(player)) {
                plugin.getService().breakBuilding(player, building);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // Ingen må trampe marker ned
        if (event.getAction() == Action.PHYSICAL) {
            if (plugin.getFarm().getFieldBySoil(block) != null) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        boolean holdingBlock = hand != null && hand.getType().isBlock() && !hand.getType().isAir();

        Field field = plugin.getFarm().getFieldAt(block);
        if (field != null) {
            boolean soil = plugin.getFarm().getFieldBySoil(block) != null;
            // Tillad at bygge op ad siden af en mark (fx hegn eller nye marker)
            if (soil && holdingBlock && event.getBlockFace() != BlockFace.UP) {
                return;
            }
            event.setCancelled(true);
            if (plugin.getClickGuard().tryClick(player)) {
                plugin.getService().clickField(player, field);
            }
            return;
        }
        Building building = plugin.getFarm().getBuildingAt(block);
        if (building != null) {
            // Shift + blok i hånden = byg op ad bygningen som normalt
            if (holdingBlock && player.isSneaking()) {
                return;
            }
            event.setCancelled(true);
            if (plugin.getClickGuard().tryClick(player)) {
                plugin.getService().clickBuilding(player, building);
            }
        }
    }
}
