package dev.tardyc.hayday;

import dev.tardyc.hayday.command.HayDayCommand;
import dev.tardyc.hayday.config.Messages;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.economy.EconomyManager;
import dev.tardyc.hayday.events.EventManager;
import dev.tardyc.hayday.gui.Menu;
import dev.tardyc.hayday.gui.MenuListener;
import dev.tardyc.hayday.hologram.AdminHologramManager;
import dev.tardyc.hayday.hologram.HologramManager;
import dev.tardyc.hayday.hook.IconService;
import dev.tardyc.hayday.hook.ItemsAdderHook;
import dev.tardyc.hayday.island.IslandGenerator;
import dev.tardyc.hayday.island.IslandListener;
import dev.tardyc.hayday.island.IslandManager;
import dev.tardyc.hayday.listener.EntityListener;
import dev.tardyc.hayday.listener.FarmListener;
import dev.tardyc.hayday.listener.MenuItemListener;
import dev.tardyc.hayday.listener.PlayerListener;
import dev.tardyc.hayday.listener.ProtectionListener;
import dev.tardyc.hayday.manager.AnimationManager;
import dev.tardyc.hayday.manager.FarmManager;
import dev.tardyc.hayday.manager.FarmService;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.manager.LevelManager;
import dev.tardyc.hayday.manager.MenuItemManager;
import dev.tardyc.hayday.manager.MarketManager;
import dev.tardyc.hayday.manager.OrderManager;
import dev.tardyc.hayday.manager.PlayerManager;
import dev.tardyc.hayday.manager.ShipManager;
import dev.tardyc.hayday.manager.StorageManager;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.pack.ResourcePackManager;
import dev.tardyc.hayday.registry.BuildingRegistry;
import dev.tardyc.hayday.registry.ItemRegistry;
import dev.tardyc.hayday.structure.StructureManager;
import dev.tardyc.hayday.util.ClickGuard;
import dev.tardyc.hayday.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HayDay - farm-plugin med marker, dyr, produktion, ordrer, levels og hologrammer.
 */
public final class HayDayPlugin extends JavaPlugin {

    private final Settings settings = new Settings();
    private final ItemRegistry items = new ItemRegistry();
    private final BuildingRegistry buildings = new BuildingRegistry();
    private final ClickGuard clickGuard = new ClickGuard();
    private final StructureManager structures = new StructureManager(this);
    private Messages messages;
    private EconomyManager economy;
    private PlayerManager players;
    private StorageManager storage;
    private LevelManager levels;
    private OrderManager orders;
    private LeaderboardManager leaderboard;
    private HologramManager holograms;
    private AdminHologramManager adminHolograms;
    private FarmManager farm;
    private FarmService service;
    private MarketManager market;
    private AnimationManager animations;
    private ItemsAdderHook itemsAdder;
    private IconService icons;
    private ResourcePackManager pack;
    private ShipManager ship;
    private IslandManager islands;
    private EventManager events;
    private MenuItemManager menuItem;

    private BukkitTask tickTask;
    private BukkitTask menuTask;
    private BukkitTask saveTask;
    private int menuItemTicks;

    @Override
    public void onEnable() {
        Keys.init(this);
        messages = new Messages(this);
        itemsAdder = new ItemsAdderHook(this);
        icons = new IconService(this);
        pack = new ResourcePackManager(this);
        FarmItem.setCustomItemResolver(id -> itemsAdder.customItem(id));
        FarmItem.setPackPolicy(
                id -> settings.customItemTextures && pack.getGlyphs().hasItemTexture(id),
                player -> pack.hasPack(player),
                () -> pack.globalTextures());
        messages.setPostProcessor(text -> icons.apply(text), (text, receiver) -> icons.apply(text, receiver));
        loadConfiguration();

        economy = new EconomyManager(this);
        players = new PlayerManager(this);
        storage = new StorageManager(this);
        levels = new LevelManager(this);
        orders = new OrderManager(this);
        leaderboard = new LeaderboardManager(this);
        holograms = new HologramManager(this);
        adminHolograms = new AdminHologramManager(this);
        farm = new FarmManager(this);
        service = new FarmService(this);
        market = new MarketManager(this);
        ship = new ShipManager(this);
        animations = new AnimationManager(this);
        islands = new IslandManager(this);
        events = new EventManager(this);
        menuItem = new MenuItemManager(this);

        economy.setup();
        itemsAdder.setup();
        pack.setup();
        animations.setup();
        // HayDay-verdenen skal findes før markerne indlæses (så de får deres hologrammer)
        islands.setup();
        farm.load();
        market.load();
        events.load();
        adminHolograms.load();
        leaderboard.loadAsync(players.getFolder());

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new FarmListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new EntityListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new IslandListener(this), this);
        pm.registerEvents(new MenuItemListener(this), this);

        PluginCommand command = getCommand("hayday");
        if (command != null) {
            HayDayCommand executor = new HayDayCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // Spillere der allerede er online (fx efter /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            leaderboard.update(players.get(player));
            events.onJoin(player);
        }
        menuItem.updateAll();

        // Økonomi-plugins kan starte efter os - find økonomien når serveren er helt oppe
        Bukkit.getScheduler().runTask(this, () -> economy.resolve());

        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new dev.tardyc.hayday.hook.HayDayExpansion(this).register();
                getLogger().info("PlaceholderAPI fundet - %hayday_...% placeholders er klar.");
            } catch (Throwable t) {
                getLogger().warning("Kunne ikke registrere PlaceholderAPI-udvidelsen: " + t.getMessage());
            }
        }

        startTasks();
        getLogger().info("HayDay er klar! " + items.size() + " varer, " + buildings.size() + " bygninger.");
    }

    @Override
    public void onDisable() {
        stopTasks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof Menu) {
                player.closeInventory();
            }
        }
        if (farm != null) {
            farm.save(true);
            farm.removeAllAnimals();
            farm.hideAllIcons();
        }
        if (market != null) {
            market.save(true);
        }
        if (islands != null) {
            islands.shutdown();
        }
        if (events != null) {
            events.shutdown();
        }
        if (animations != null) {
            animations.cleanup();
        }
        if (players != null) {
            players.saveAll();
        }
        if (adminHolograms != null) {
            adminHolograms.save();
        }
        if (holograms != null) {
            holograms.despawnAll();
        }
        if (pack != null) {
            pack.shutdown();
        }
    }

    /** Indlæser config.yml, items.yml, buildings.yml og messages.yml. */
    private void loadConfiguration() {
        saveDefaultConfig();
        reloadConfig();
        settings.load(getConfig());
        messages.load();
        items.load(loadYaml("items.yml"), getLogger());
        buildings.load(loadYaml("buildings.yml"), items, getLogger());
        structures.load();
    }

    private YamlConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = getResource(name);
        if (defaults != null && config.getKeys(false).isEmpty()) {
            getLogger().warning(name + " er tom eller ugyldig - bruger standardværdier.");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8));
        }
        return config;
    }

    /** /hayday reload */
    public void reload() {
        stopTasks();
        farm.save(true);
        market.save(true);
        islands.save(true);
        players.saveAll();
        animations.cleanup();
        farm.hideAllIcons();
        loadConfiguration();
        economy.setup();
        itemsAdder.setup();
        pack.setup();
        icons.clearCache();
        animations.setup();
        holograms.respawnAll();
        farm.reloadStructures();
        farm.reloadHolograms();
        islands.reload();
        events.reload();
        menuItem.updateAll();
        adminHolograms.load();
        startTasks();
    }

    private void startTasks() {
        int interval = settings.updateInterval;
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            market.tick();
            ship.tick();
            farm.tick();
            islands.tick();
            events.tick();
            adminHolograms.tick();
            holograms.tick();
        }, 20L, interval);
        menuTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            // HayDay-itemet kommer tilbage hvis det fx er blevet ryddet med /clear
            if (++menuItemTicks % 5 == 0) {
                menuItem.updateAll();
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof Menu && ((Menu) holder).isAutoRefresh()) {
                    ((Menu) holder).update();
                }
            }
        }, 20L, 20L);
        long saveTicks = settings.autosaveMinutes * 60L * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            farm.save(false);
            market.save(false);
            islands.save(false);
            players.saveDirty();
        }, saveTicks, saveTicks);
    }

    private void stopTasks() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (menuTask != null) {
            menuTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public Messages getMessages() {
        return messages;
    }

    public ItemRegistry getItems() {
        return items;
    }

    public BuildingRegistry getBuildings() {
        return buildings;
    }

    public StructureManager getStructures() {
        return structures;
    }

    public ClickGuard getClickGuard() {
        return clickGuard;
    }

    public EconomyManager getEconomy() {
        return economy;
    }

    public PlayerManager getPlayers() {
        return players;
    }

    public StorageManager getStorage() {
        return storage;
    }

    public LevelManager getLevels() {
        return levels;
    }

    public OrderManager getOrders() {
        return orders;
    }

    public LeaderboardManager getLeaderboard() {
        return leaderboard;
    }

    public HologramManager getHolograms() {
        return holograms;
    }

    public AdminHologramManager getAdminHolograms() {
        return adminHolograms;
    }

    public FarmManager getFarm() {
        return farm;
    }

    public FarmService getService() {
        return service;
    }

    public MarketManager getMarket() {
        return market;
    }

    public AnimationManager getAnimations() {
        return animations;
    }

    public ItemsAdderHook getItemsAdder() {
        return itemsAdder;
    }

    public IconService getIcons() {
        return icons;
    }

    public ResourcePackManager getPack() {
        return pack;
    }

    public ShipManager getShip() {
        return ship;
    }

    public IslandManager getIslands() {
        return islands;
    }

    public EventManager getEvents() {
        return events;
    }

    public MenuItemManager getMenuItem() {
        return menuItem;
    }

    /** Så HayDay-verdenen også kan indlæses via bukkit.yml eller Multiverse ("generator: HayDay"). */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new IslandGenerator(IslandManager.readLayout(getDataFolder(), getConfig()));
    }

    /** Pluginets jar-fil (bruges til at kopiere ItemsAdder-indhold ud). */
    public File getPluginFile() {
        return getFile();
    }
}
