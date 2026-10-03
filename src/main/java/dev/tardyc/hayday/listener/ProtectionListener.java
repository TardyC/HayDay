package dev.tardyc.hayday.listener;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.manager.FarmManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;

/**
 * Beskytter marker og bygninger mod alt der kunne ødelægge dem: vand, ild, eksplosioner, stempler,
 * udtørring, nedtrampning, naturlig vækst osv.
 */
public final class ProtectionListener implements Listener {

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final HayDayPlugin plugin;

    public ProtectionListener(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private FarmManager farm() {
        return plugin.getFarm();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!farm().hasFields()) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        if ((type == Material.FARMLAND || plugin.getItems().isCropMaterial(type)) && farm().getFieldAt(block) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (!farm().hasFields()) {
            return;
        }
        Block block = event.getBlock();
        if (farm().getFieldAt(block) != null) {
            event.setCancelled(true);
            return;
        }
        // Stilke må ikke lave græskar/meloner ved siden af sig selv - de vises på selve marken
        Material grown = event.getNewState().getType();
        if (grown == Material.PUMPKIN || grown == Material.MELON) {
            for (BlockFace face : HORIZONTAL) {
                if (farm().getFieldByCrop(block.getRelative(face)) != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (farm().isFarmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        if (farm().getFieldBySoil(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (farm().isFarmBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (farm().isFarmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (farm().isFarmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event) {
        if (farm().isFarmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (farm().isFarmBlock(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (farm().isFarmBlock(block) || farm().isFarmBlock(block.getRelative(event.getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (farm().isFarmBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(farm()::isFarmBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(farm()::isFarmBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (farm().isFarmBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        if (farm().getFieldBySoil(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(state -> farm().isFarmBlock(state.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (isBuilding(event.getSource()) || isBuilding(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isBuilding(Inventory inventory) {
        if (farm().getBuildings().isEmpty()) {
            return false;
        }
        Location location = inventory.getLocation();
        return location != null && location.getWorld() != null && farm().getBuildingAt(location.getBlock()) != null;
    }
}
