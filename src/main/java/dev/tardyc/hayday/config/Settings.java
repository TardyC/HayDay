package dev.tardyc.hayday.config;

import dev.tardyc.hayday.events.EventType;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Alle værdier fra config.yml.
 */
public final class Settings {

    // Økonomi
    public String economyType;
    public String currencyName;
    public double startCoins;

    // Generelt
    public Set<String> allowedWorlds;
    public int autosaveMinutes;
    public int massRadius;
    public boolean notifyReady;
    public boolean joinSummary;
    public boolean buildingStructures;
    public boolean fieldsInGround;
    public boolean propsEnabled;

    // Levels
    public int maxLevel;
    public long xpBase;
    public long xpIncrease;
    public double coinsPerLevel;

    // Marker
    public int fieldsBase;
    public int fieldsPerLevel;
    public int fieldsMax;
    public double fieldPriceBase;
    public double fieldPriceIncrease;
    public boolean useSiloCrop;

    // Lager
    public StorageSettings silo;
    public StorageSettings barn;

    // Ordrer
    public int orderBaseSlots;
    public int orderExtraEvery;
    public int orderMaxSlots;
    public int orderMinItems;
    public int orderMaxItems;
    public int orderCropMin;
    public int orderCropMax;
    public int orderProductMin;
    public int orderProductMax;
    public double orderRewardMin;
    public double orderRewardMax;
    public double orderXpMultiplier;
    public long orderNewDelay;
    public long orderDiscardDelay;

    // Start
    public int starterFields;
    public Map<String, Integer> starterStorage;

    // Hologrammer
    public float holoViewRange;
    public float holoScale;
    public boolean holoShadow;
    public Color holoBackground;
    public double fieldHologramHeight;
    public int updateInterval;
    public int barLength;
    public String barSymbol;
    public String barDoneColor;
    public String barLeftColor;
    public List<String> fieldEmpty;
    public List<String> fieldGrowing;
    public List<String> fieldReady;
    public List<String> buildingIdle;
    public List<String> buildingWorking;
    public List<String> buildingDone;
    // Marked (vejbod og avis)
    public int marketBaseSlots;
    public int marketMaxSlots;
    public double marketSlotPrice;
    public int marketMaxAmount;
    public double marketMinMultiplier;
    public double marketMaxMultiplier;
    public double marketDefaultMultiplier;
    public long marketExpireHours;
    public List<String> roadsideLines;

    // Skibet
    public boolean shipEnabled;
    public int shipLevel;
    public int shipTypes;
    public int shipCratesPerType;
    public int shipCropMin;
    public int shipCropMax;
    public int shipProductMin;
    public int shipProductMax;
    public double shipRewardMultiplier;
    public double shipXpMultiplier;
    public double shipBonusPercent;
    public long shipStayTime;
    public long shipAwayTime;
    public List<String> harborDocked;
    public List<String> harborAway;
    public List<String> harborLocked;
    public List<String> harborOffline;

    // Øer (HayDay-verdenen)
    public boolean islandsEnabled;
    public String islandWorld;
    public int islandSize;
    public int islandGap;
    public int islandHeight;
    public boolean islandStarterLayout;
    public boolean islandFarmOnly;
    public boolean islandTeleportOnStart;
    public String islandDefaultAccess;
    public String islandDefaultName;
    public boolean islandHelpShip;
    public String islandDifficulty;
    public boolean islandAlwaysDay;
    public boolean islandNoWeather;
    public boolean islandPvp;
    public boolean islandMonsters;
    public boolean islandFireSpread;
    public List<String> islandHologram;
    public List<String> islandSpawnHologram;
    public Map<String, List<String>> islandLandmarkLines;

    // HayDay-itemet i hotbaren
    public boolean menuItemEnabled;
    public int menuItemSlot;
    public String menuItemMaterial;
    public String menuItemName;
    public List<String> menuItemLore;
    public boolean menuItemGlow;
    public Set<String> menuItemWorlds;

    // Events
    public double eventDefaultMultiplier;
    public int eventDefaultMinutes;
    public boolean eventBossbar;
    public boolean eventAutoEnabled;
    public int eventAutoEvery;
    public int eventAutoDuration;
    public List<EventType> eventAutoTypes;

    // Animationer
    public boolean animations;
    public boolean animHarvest;
    public boolean animFloatingText;
    public boolean animFieldIcons;
    public boolean animBuildingIcons;
    public boolean animFireworks;
    public boolean animSparkles;
    public boolean useProtocolLib;

    // Resourcepack
    public String packMode;
    public boolean texturedMenus;
    public boolean customItemTextures;
    public boolean packSendOnJoin;
    public boolean packRequired;
    public String packPrompt;
    public String packUrl;
    public boolean packHostEnabled;
    public String packHostAddress;
    public int packHostPort;

    // ItemsAdder
    public boolean itemsAdder;
    public boolean itemsAdderMenus;
    public boolean itemsAdderExport;
    public boolean itemsAdderAutoZip;
    public int itemsAdderTextureOffset;
    public int itemsAdderTitleOffset;
    public String itemsAdderTitleColor;
    public Map<String, String[]> icons;

    public int topSize;
    public List<String> topTitle;
    public String topEntry;
    public String topEmpty;

    public void load(FileConfiguration c) {
        economyType = c.getString("economy.type", "vault").toLowerCase(Locale.ROOT);
        currencyName = c.getString("economy.currency-name", "mønter");
        startCoins = c.getDouble("economy.start-coins", 100);

        allowedWorlds = new HashSet<>();
        for (String world : c.getStringList("general.allowed-worlds")) {
            allowedWorlds.add(world.toLowerCase(Locale.ROOT));
        }
        autosaveMinutes = Math.max(1, c.getInt("general.autosave-minutes", 5));
        massRadius = Math.max(1, c.getInt("general.mass-action-radius", 12));
        notifyReady = c.getBoolean("general.notify-ready", true);
        joinSummary = c.getBoolean("general.join-summary", true);
        buildingStructures = c.getBoolean("general.building-structures", true);
        fieldsInGround = c.getBoolean("general.fields-in-ground", true);
        propsEnabled = c.getBoolean("general.3d-models", true);

        maxLevel = Math.max(1, c.getInt("levels.max-level", 50));
        xpBase = Math.max(1, c.getLong("levels.xp-base", 20));
        xpIncrease = Math.max(0, c.getLong("levels.xp-increase", 15));
        coinsPerLevel = c.getDouble("levels.coins-per-level", 50);

        fieldsBase = c.getInt("fields.base-amount", 4);
        fieldsPerLevel = c.getInt("fields.per-level", 1);
        fieldsMax = c.getInt("fields.max-amount", 40);
        fieldPriceBase = c.getDouble("fields.price-base", 50);
        fieldPriceIncrease = c.getDouble("fields.price-increase", 20);
        useSiloCrop = c.getBoolean("planting.use-silo-crop", true);

        silo = new StorageSettings(c.getConfigurationSection("storage.silo"));
        barn = new StorageSettings(c.getConfigurationSection("storage.barn"));

        orderBaseSlots = Math.max(1, c.getInt("orders.base-slots", 3));
        orderExtraEvery = Math.max(1, c.getInt("orders.extra-slot-every-levels", 4));
        orderMaxSlots = Math.max(1, Math.min(9, c.getInt("orders.max-slots", 9)));
        orderMinItems = Math.max(1, c.getInt("orders.min-items", 1));
        orderMaxItems = Math.max(orderMinItems, c.getInt("orders.max-items", 3));
        orderCropMin = Math.max(1, c.getInt("orders.crop-amount-min", 2));
        orderCropMax = Math.max(orderCropMin, c.getInt("orders.crop-amount-max", 6));
        orderProductMin = Math.max(1, c.getInt("orders.product-amount-min", 1));
        orderProductMax = Math.max(orderProductMin, c.getInt("orders.product-amount-max", 3));
        orderRewardMin = c.getDouble("orders.reward-multiplier-min", 1.4);
        orderRewardMax = Math.max(orderRewardMin, c.getDouble("orders.reward-multiplier-max", 2.0));
        orderXpMultiplier = c.getDouble("orders.xp-multiplier", 1.5);
        orderNewDelay = Math.max(0, c.getLong("orders.new-order-delay", 30)) * 1000L;
        orderDiscardDelay = Math.max(0, c.getLong("orders.discard-delay", 180)) * 1000L;

        starterFields = Math.max(0, c.getInt("starter.fields", 3));
        starterStorage = new LinkedHashMap<>();
        ConfigurationSection starter = c.getConfigurationSection("starter.storage");
        if (starter != null) {
            for (String key : starter.getKeys(false)) {
                starterStorage.put(key.toLowerCase(Locale.ROOT), starter.getInt(key));
            }
        }

        holoViewRange = (float) c.getDouble("holograms.view-range", 0.6);
        holoScale = (float) c.getDouble("holograms.scale", 1.0);
        holoShadow = c.getBoolean("holograms.shadow", true);
        holoBackground = parseArgb(c.getString("holograms.background", "#00000000"));
        fieldHologramHeight = c.getDouble("holograms.field-height", 2.1);
        updateInterval = Math.max(5, c.getInt("holograms.update-interval", 20));
        barLength = Math.max(1, c.getInt("holograms.progress-bar.length", 12));
        barSymbol = c.getString("holograms.progress-bar.symbol", "▌");
        barDoneColor = c.getString("holograms.progress-bar.done-color", "&a");
        barLeftColor = c.getString("holograms.progress-bar.left-color", "&8");
        fieldEmpty = lines(c, "holograms.field.empty");
        fieldGrowing = lines(c, "holograms.field.growing");
        fieldReady = lines(c, "holograms.field.ready");
        buildingIdle = lines(c, "holograms.building.idle");
        buildingWorking = lines(c, "holograms.building.working");
        buildingDone = lines(c, "holograms.building.done");
        roadsideLines = lines(c, "holograms.roadside");

        marketBaseSlots = Math.max(1, c.getInt("market.base-slots", 4));
        marketMaxSlots = Math.max(marketBaseSlots, Math.min(14, c.getInt("market.max-slots", 14)));
        marketSlotPrice = c.getDouble("market.slot-price", 300);
        marketMaxAmount = Math.max(1, c.getInt("market.max-amount", 30));
        marketMinMultiplier = c.getDouble("market.min-price-multiplier", 0.5);
        marketMaxMultiplier = Math.max(marketMinMultiplier, c.getDouble("market.max-price-multiplier", 3.0));
        marketDefaultMultiplier = c.getDouble("market.default-price-multiplier", 1.5);
        marketExpireHours = Math.max(0, c.getLong("market.expire-hours", 48));

        shipEnabled = c.getBoolean("ship.enabled", true);
        shipLevel = Math.max(1, c.getInt("ship.level", 6));
        shipTypes = Math.max(1, Math.min(3, c.getInt("ship.crate-types", 3)));
        shipCratesPerType = Math.max(1, Math.min(3, c.getInt("ship.crates-per-type", 3)));
        shipCropMin = Math.max(1, c.getInt("ship.crop-amount-min", 4));
        shipCropMax = Math.max(shipCropMin, c.getInt("ship.crop-amount-max", 8));
        shipProductMin = Math.max(1, c.getInt("ship.product-amount-min", 1));
        shipProductMax = Math.max(shipProductMin, c.getInt("ship.product-amount-max", 3));
        shipRewardMultiplier = c.getDouble("ship.reward-multiplier", 1.6);
        shipXpMultiplier = c.getDouble("ship.xp-multiplier", 1.5);
        shipBonusPercent = c.getDouble("ship.bonus-percent", 25);
        shipStayTime = Math.max(60, c.getLong("ship.stay-time", 7200)) * 1000L;
        shipAwayTime = Math.max(10, c.getLong("ship.away-time", 1800)) * 1000L;
        harborDocked = lines(c, "holograms.harbor.docked");
        harborAway = lines(c, "holograms.harbor.away");
        harborLocked = lines(c, "holograms.harbor.locked");
        harborOffline = lines(c, "holograms.harbor.offline");

        islandsEnabled = c.getBoolean("islands.enabled", true);
        islandWorld = c.getString("islands.world", "hayday");
        islandSize = c.getInt("islands.size", 48);
        islandGap = c.getInt("islands.gap", 24);
        islandHeight = c.getInt("islands.height", 64);
        islandStarterLayout = c.getBoolean("islands.starter-layout", true);
        islandFarmOnly = c.getBoolean("islands.farm-only-on-island", true);
        islandTeleportOnStart = c.getBoolean("islands.teleport-on-start", true);
        islandDefaultAccess = c.getString("islands.default-access", "alle");
        islandDefaultName = c.getString("islands.default-name", "{owner}s gård");
        islandHelpShip = c.getBoolean("islands.visitors-can-help-ship", true);
        islandDifficulty = c.getString("islands.difficulty", "peaceful");
        islandAlwaysDay = c.getBoolean("islands.always-day", false);
        islandNoWeather = c.getBoolean("islands.no-weather", true);
        islandPvp = c.getBoolean("islands.pvp", false);
        islandMonsters = c.getBoolean("islands.monsters", false);
        islandFireSpread = c.getBoolean("islands.fire-spread", false);
        islandHologram = lines(c, "islands.hologram");
        islandSpawnHologram = lines(c, "islands.spawn-hologram");
        islandLandmarkLines = new HashMap<>();
        for (String id : new String[]{"barn", "silo", "orderboard", "mailbox"}) {
            islandLandmarkLines.put(id, lines(c, "islands.landmarks." + id));
        }

        menuItemEnabled = c.getBoolean("menu-item.enabled", true);
        menuItemSlot = Math.max(0, Math.min(8, c.getInt("menu-item.slot", 8)));
        menuItemMaterial = c.getString("menu-item.material", "WHEAT");
        menuItemName = c.getString("menu-item.name", "&a&lHay&e&lDay");
        menuItemLore = c.getStringList("menu-item.lore");
        menuItemGlow = c.getBoolean("menu-item.glow", true);
        menuItemWorlds = new HashSet<>();
        for (String world : c.getStringList("menu-item.worlds")) {
            menuItemWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        eventDefaultMultiplier = Math.max(0.1, c.getDouble("events.default-multiplier", 2.0));
        eventDefaultMinutes = Math.max(1, c.getInt("events.default-minutes", 30));
        eventBossbar = c.getBoolean("events.bossbar", true);
        eventAutoEnabled = c.getBoolean("events.auto.enabled", false);
        eventAutoEvery = Math.max(1, c.getInt("events.auto.every-minutes", 180));
        eventAutoDuration = Math.max(1, c.getInt("events.auto.duration-minutes", 30));
        eventAutoTypes = new ArrayList<>();
        for (String type : c.getStringList("events.auto.types")) {
            EventType parsed = EventType.parse(type);
            if (parsed != null && !eventAutoTypes.contains(parsed)) {
                eventAutoTypes.add(parsed);
            }
        }

        animations = c.getBoolean("animations.enabled", true);
        animHarvest = animations && c.getBoolean("animations.harvest", true);
        animFloatingText = animations && c.getBoolean("animations.floating-text", true);
        animFieldIcons = animations && c.getBoolean("animations.field-icons", true);
        animBuildingIcons = animations && c.getBoolean("animations.building-icons", true);
        animFireworks = animations && c.getBoolean("animations.level-up-firework", true);
        animSparkles = animations && c.getBoolean("animations.ready-sparkles", true);
        useProtocolLib = c.getBoolean("animations.use-protocollib", true);

        packMode = c.getString("resource-pack.mode", "auto").toLowerCase(Locale.ROOT);
        texturedMenus = c.getBoolean("resource-pack.textured-menus", true);
        customItemTextures = c.getBoolean("resource-pack.custom-item-textures", true);
        packSendOnJoin = c.getBoolean("resource-pack.send-on-join", true);
        packRequired = c.getBoolean("resource-pack.required", false);
        packPrompt = Text.color(c.getString("resource-pack.prompt", "&aHayDay bruger en resourcepack med farm-grafik."));
        packUrl = c.getString("resource-pack.url", "").trim();
        packHostEnabled = c.getBoolean("resource-pack.host.enabled", true);
        packHostAddress = c.getString("resource-pack.host.address", "").trim();
        packHostPort = c.getInt("resource-pack.host.port", 8163);

        itemsAdder = c.getBoolean("itemsadder.enabled", true);
        itemsAdderMenus = c.getBoolean("itemsadder.textured-menus", true);
        itemsAdderExport = c.getBoolean("itemsadder.export-content", true);
        itemsAdderAutoZip = c.getBoolean("itemsadder.auto-zip", true);
        itemsAdderTextureOffset = c.getInt("itemsadder.texture-offset", -16);
        itemsAdderTitleOffset = c.getInt("itemsadder.title-offset", 0);
        itemsAdderTitleColor = c.getString("itemsadder.title-color", "&#3B2410");
        icons = new LinkedHashMap<>();
        ConfigurationSection iconSection = c.getConfigurationSection("icons");
        if (iconSection != null) {
            for (String key : iconSection.getKeys(false)) {
                icons.put(key.toLowerCase(Locale.ROOT), new String[]{
                        iconSection.getString(key + ".itemsadder", ""),
                        iconSection.getString(key + ".fallback", "")});
            }
        }

        topSize = Math.max(1, Math.min(30, c.getInt("holograms.top.size", 10)));
        topTitle = lines(c, "holograms.top.title");
        topEntry = c.getString("holograms.top.entry", "&e#{rank} &f{name} &8- &aLevel {level}");
        topEmpty = c.getString("holograms.top.empty", "&8#{rank} ---");
    }

    private static List<String> lines(FileConfiguration c, String path) {
        List<String> list = c.getStringList(path);
        return list.isEmpty() ? Collections.singletonList("") : new ArrayList<>(list);
    }

    /** "#AARRGGBB" eller "#RRGGBB" -> farve. "default" -> null (Minecrafts standard-baggrund). */
    private static Color parseArgb(String input) {
        if (input == null || input.equalsIgnoreCase("default")) {
            return null;
        }
        String hex = input.startsWith("#") ? input.substring(1) : input;
        try {
            long value = Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                return Color.fromARGB(255, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
            }
            return Color.fromARGB((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
        } catch (NumberFormatException e) {
            return Color.fromARGB(0, 0, 0, 0);
        }
    }

    public boolean isWorldAllowed(World world) {
        return allowedWorlds.isEmpty() || allowedWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public StorageSettings storage(ItemCategory category) {
        return category == ItemCategory.CROP ? silo : barn;
    }

    public int maxFields(int level) {
        return Math.max(0, Math.min(fieldsMax, fieldsBase + (level - 1) * fieldsPerLevel));
    }

    public double fieldPrice(int bought) {
        return fieldPriceBase + bought * fieldPriceIncrease;
    }

    public long xpForNext(int level) {
        return xpBase + (long) (level - 1) * xpIncrease;
    }

    public int orderSlots(int level) {
        return Math.min(orderMaxSlots, orderBaseSlots + level / orderExtraEvery);
    }

    public String bar(double progress) {
        return Text.progressBar(progress, barLength, barSymbol, barDoneColor, barLeftColor);
    }

    /** Lager-indstillinger for silo eller lade. */
    public static final class StorageSettings {
        public final int baseCapacity;
        public final int upgradeCapacity;
        public final double upgradePriceBase;
        public final double upgradePriceIncrease;
        public final int maxUpgrades;

        StorageSettings(ConfigurationSection section) {
            if (section == null) {
                baseCapacity = 50;
                upgradeCapacity = 25;
                upgradePriceBase = 300;
                upgradePriceIncrease = 150;
                maxUpgrades = 20;
                return;
            }
            baseCapacity = Math.max(1, section.getInt("base-capacity", 50));
            upgradeCapacity = Math.max(1, section.getInt("upgrade-capacity", 25));
            upgradePriceBase = section.getDouble("upgrade-price-base", 300);
            upgradePriceIncrease = section.getDouble("upgrade-price-increase", 150);
            maxUpgrades = Math.max(0, section.getInt("max-upgrades", 20));
        }

        public int capacity(int upgrades) {
            return baseCapacity + upgrades * upgradeCapacity;
        }

        public double upgradePrice(int upgrades) {
            return upgradePriceBase + upgrades * upgradePriceIncrease;
        }
    }
}
