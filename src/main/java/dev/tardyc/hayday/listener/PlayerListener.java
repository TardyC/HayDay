package dev.tardyc.hayday.listener;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Login/logud og verdener der indlæses/fjernes.
 */
public final class PlayerListener implements Listener {

    private final HayDayPlugin plugin;

    public PlayerListener(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayers().get(player);
        data.setLastSeen(System.currentTimeMillis());
        plugin.getFarm().updateOwnerName(player);
        plugin.getMarket().updateSellerName(player);
        plugin.getLeaderboard().update(data);
        plugin.getEvents().onJoin(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getPack().send(player);
            }
        }, 20L);
        if (!data.isStarted()) {
            return;
        }
        plugin.getOrders().ensure(data);
        if (plugin.getSettings().joinSummary) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                int[] ready = plugin.getService().readyCounts(player.getUniqueId());
                if (ready[0] + ready[1] > 0) {
                    plugin.getMessages().send(player, "join.summary", "fields", ready[0], "items", ready[1]);
                }
                int sold = plugin.getMarket().count(player.getUniqueId(), Listing.State.SOLD);
                if (sold > 0) {
                    plugin.getMessages().send(player, "market.sold-summary", "count", sold);
                }
            }, 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = plugin.getPlayers().getIfLoaded(event.getPlayer().getUniqueId());
        if (data != null) {
            data.setLastSeen(System.currentTimeMillis());
        }
        plugin.getPlayers().unload(event.getPlayer());
        plugin.getEvents().onQuit(event.getPlayer());
        plugin.getClickGuard().forget(event.getPlayer());
        plugin.getPack().forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        plugin.getPack().onStatus(event);
        // HayDay-itemet får sin egen tekstur når pakken er indlæst
        plugin.getMenuItem().update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getFarm().onWorldLoad(event.getWorld());
        plugin.getAdminHolograms().spawnAvailable();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.getFarm().onWorldUnload(event.getWorld());
        plugin.getAdminHolograms().despawnWorld(event.getWorld());
        plugin.getHolograms().despawnWorld(event.getWorld());
    }
}
