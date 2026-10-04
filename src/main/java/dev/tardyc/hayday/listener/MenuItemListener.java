package dev.tardyc.hayday.listener;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.gui.MainMenu;
import dev.tardyc.hayday.manager.MenuItemManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * HayDay-itemet i hotbaren: højreklik åbner menuen, og itemet kan ikke smides, flyttes eller mistes.
 */
public final class MenuItemListener implements Listener {

    private final HayDayPlugin plugin;

    public MenuItemListener(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private MenuItemManager items() {
        return plugin.getMenuItem();
    }

    private void later(Player player, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                items().update(player);
            }
        }, ticks);
    }

    private void use(Player player) {
        if (!plugin.getClickGuard().tryClick(player)) {
            return;
        }
        if (player.isSneaking() && plugin.getIslands().isEnabled()) {
            plugin.getService().ensureStarted(player);
            plugin.getIslands().teleportHome(player);
            return;
        }
        plugin.getService().ensureStarted(player);
        new MainMenu(plugin, player).open();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!items().isMenuItem(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Klik på en mark/bygning med itemet i hånden virker som normalt
        Block block = event.getClickedBlock();
        if (block != null && plugin.getFarm().isFarmBlock(block)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            use(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && items().isMenuItem(event.getPlayer().getInventory().getItemInMainHand())) {
            // Fx ikke i en item frame
            event.setCancelled(true);
            use(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (items().isMenuItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (items().isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        boolean locked = items().isMenuItem(event.getCurrentItem()) || items().isMenuItem(event.getCursor());
        if (!locked && event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            String click = event.getClick().name();
            if (click.equals("NUMBER_KEY") && event.getHotbarButton() >= 0) {
                locked = items().isMenuItem(player.getInventory().getItem(event.getHotbarButton()));
            } else if (click.equals("SWAP_OFFHAND")) {
                locked = items().isMenuItem(player.getInventory().getItemInOffHand());
            }
        }
        if (locked) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (items().isMenuItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (items().isMenuItem(event.getMainHandItem()) || items().isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(items()::isMenuItem);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer(), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        later(event.getPlayer(), 2L);
    }
}
