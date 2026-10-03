package dev.tardyc.hayday.island;

import dev.tardyc.hayday.HayDayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Beskyttelse i HayDay-verdenen: man kan kun bygge på sin egen ø, vand/ild/stempler/eksplosioner bliver på
 * øen de starter på, lukkede øer kan ikke betrædes, og der kommer ingen monstre eller vilde dyr.
 */
public final class IslandListener implements Listener {

    /** Naturlige spawns der slås fra i HayDay-verdenen (navne, så det virker på tværs af versioner). */
    private static final Set<String> BLOCKED_SPAWNS = new HashSet<>(Arrays.asList("NATURAL", "CHUNK_GEN", "JOCKEY",
            "MOUNT", "PATROL", "RAID", "REINFORCEMENTS", "VILLAGE_INVASION", "TRAP", "LIGHTNING", "DROWNED"));
    private static final Set<String> NATURAL_FIRE = new HashSet<>(Arrays.asList("SPREAD", "LAVA", "LIGHTNING"));

    private final HayDayPlugin plugin;

    public IslandListener(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private IslandManager islands() {
        return plugin.getIslands();
    }

    /** Må spilleren ændre noget ved blokken? Sender en (rolig) besked hvis ikke. */
    private boolean allowed(Player player, Block block, boolean warn) {
        if (!islands().isIslandWorld(block.getWorld()) || islands().canBuild(player, block.getX(), block.getZ())) {
            return true;
        }
        if (warn) {
            warnNoBuild(player, block.getX(), block.getZ());
        }
        return false;
    }

    private boolean allowed(Player player, Location location) {
        return !islands().isIslandWorld(location.getWorld())
                || islands().canBuild(player, location.getBlockX(), location.getBlockZ());
    }

    private void warnNoBuild(Player player, int x, int z) {
        Island island = islands().getAt(x, z);
        if (island == null) {
            islands().warn(player, "island.no-build-here");
        } else {
            islands().warn(player, "island.no-build", "owner", island.getOwnerName(), "farm", islands().farmName(island));
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            return (Player) ((Projectile) damager).getShooter();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Byg og brug
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getBlockPlaced(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !islands().isIslandWorld(block.getWorld())) {
            return;
        }
        // Marker og bygninger styres af FarmListener (vejboden og havnen må besøgende gerne bruge)
        if (plugin.getFarm().isFarmBlock(block) || islands().canBuild(event.getPlayer(), block.getX(), block.getZ())) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setUseItemInHand(Event.Result.DENY);
            if (block.getType().isInteractable()) {
                warnNoBuild(event.getPlayer(), block.getX(), block.getZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!allowed(event.getPlayer(), target, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!allowed(event.getPlayer(), event.getBlockClicked(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null && !allowed(event.getPlayer(), event.getBlock(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attacker(event.getRemover());
        if (player != null && !allowed(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        // Spillere og både/minecarts må man gerne interagere med
        if (entity instanceof Player || entity instanceof Vehicle || allowed(event.getPlayer(), entity.getLocation())) {
            return;
        }
        event.setCancelled(true);
        warnNoBuild(event.getPlayer(), entity.getLocation().getBlockX(), entity.getLocation().getBlockZ());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!allowed(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!islands().isIslandWorld(victim.getWorld())) {
            return;
        }
        Player player = attacker(event.getDamager());
        if (player == null) {
            return;
        }
        if (victim instanceof Player) {
            if (!plugin.getSettings().islandPvp && !player.equals(victim)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!allowed(player, victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = attacker(event.getAttacker());
        if (player != null && !allowed(player, event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Vand, ild, stempler, eksplosioner og træer bliver på deres egen ø
    // ------------------------------------------------------------------

    private boolean sameZone(Block a, Block b) {
        return islands().zone(a.getX(), a.getZ()) == islands().zone(b.getX(), b.getZ());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (islands().isIslandWorld(event.getBlock().getWorld()) && !sameZone(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        if (!islands().isIslandWorld(piston.getWorld())) {
            return;
        }
        if (!sameZone(piston, piston.getRelative(event.getDirection()))) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (!sameZone(piston, block) || !sameZone(piston, block.getRelative(event.getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        if (!islands().isIslandWorld(piston.getWorld())) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (!sameZone(piston, block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location origin = event.getLocation();
        if (islands().isIslandWorld(origin.getWorld())) {
            long zone = islands().zone(origin.getBlockX(), origin.getBlockZ());
            event.blockList().removeIf(block -> islands().zone(block.getX(), block.getZ()) != zone);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block origin = event.getBlock();
        if (islands().isIslandWorld(origin.getWorld())) {
            event.blockList().removeIf(block -> !sameZone(origin, block));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (!islands().isIslandWorld(block.getWorld())) {
            return;
        }
        if (event.getPlayer() != null) {
            if (!allowed(event.getPlayer(), block, true)) {
                event.setCancelled(true);
            }
        } else if (!plugin.getSettings().islandFireSpread && NATURAL_FIRE.contains(event.getCause().name())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!plugin.getSettings().islandFireSpread && islands().isIslandWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Block block = event.getBlock();
        if (!islands().isIslandWorld(block.getWorld())) {
            return;
        }
        if (event.getSource().getType() == Material.FIRE && !plugin.getSettings().islandFireSpread) {
            event.setCancelled(true);
        } else if (!sameZone(event.getSource(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Location origin = event.getLocation();
        if (islands().isIslandWorld(origin.getWorld())) {
            long zone = islands().zone(origin.getBlockX(), origin.getBlockZ());
            event.getBlocks().removeIf(state -> islands().zone(state.getX(), state.getZ()) != zone);
        }
    }

    // ------------------------------------------------------------------
    // Verden: ingen monstre, ingen vilde dyr og (valgfrit) intet regnvejr
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!islands().isIslandWorld(event.getEntity().getWorld())) {
            return;
        }
        String reason = event.getSpawnReason().name();
        if (reason.equals("CUSTOM") || reason.equals("COMMAND")) {
            return;
        }
        if (BLOCKED_SPAWNS.contains(reason) || (!plugin.getSettings().islandMonsters && event.getEntity() instanceof Monster)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && plugin.getSettings().islandNoWeather && islands().isIslandWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (event.toThunderState() && plugin.getSettings().islandNoWeather && islands().isIslandWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Ind og ud af øer
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        Island island = islands().getAt(to);
        // Man kan ikke gå/svømme ind på en lukket ø (står man allerede på den, sender tick() en væk)
        if (island != null && !islands().canEnter(player, island) && island != islands().getAt(from)) {
            event.setCancelled(true);
            islands().warn(player, islands().deniedKey(player, island), "owner", island.getOwnerName(),
                    "farm", islands().farmName(island));
            return;
        }
        islands().updateZone(player, to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Island island = islands().getAt(event.getTo());
        if (island != null && !islands().canEnter(event.getPlayer(), island)) {
            event.setCancelled(true);
            islands().warn(event.getPlayer(), islands().deniedKey(event.getPlayer(), island), "owner", island.getOwnerName(),
                    "farm", islands().farmName(island));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                islands().updateZone(player, to);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        islands().updateName(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                islands().checkPosition(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        islands().forget(event.getPlayer());
    }
}
