package dev.tardyc.hayday.island;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.gui.FarmMenu;
import dev.tardyc.hayday.gui.NewspaperMenu;
import dev.tardyc.hayday.gui.OrdersMenu;
import dev.tardyc.hayday.gui.StorageMenu;
import dev.tardyc.hayday.gui.VisitMenu;
import dev.tardyc.hayday.hologram.Hologram;
import dev.tardyc.hayday.hologram.PropManager;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.manager.ShipManager;
import dev.tardyc.hayday.model.BlockPos;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.structure.Structure;
import dev.tardyc.hayday.util.PlaceableItems;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Øerne: HayDay-verdenen, hvem der ejer hvilken ø, besøg, venner, likes og beskyttelse.
 * Data gemmes i plugins/HayDay/data/islands.yml.
 */
public final class IslandManager {

    /** Fil i verdensmappen der viser at verdenen er lavet af HayDay (så vi aldrig tegner øer i en anden verden). */
    private static final String MARKER = "hayday-islands.txt";
    /** Zone-nøgle for havet mellem øerne. */
    private static final long SEA = Long.MIN_VALUE;
    /** Zone-nøgle når spilleren ikke er i HayDay-verdenen. */
    private static final long OUTSIDE = Long.MAX_VALUE;
    /** Et besøg tælles højst én gang pr. halve time pr. besøgende. */
    private static final long VISIT_COOLDOWN = 30 * 60 * 1000L;

    private final HayDayPlugin plugin;
    private final IslandBuilder builder;
    private final File file;
    private final Map<UUID, Island> byOwner = new LinkedHashMap<>();
    private final Map<Long, Island> byGrid = new HashMap<>();
    /** Øer der er ved at blive tegnet om - de må ikke deles ud imens. */
    private final Set<Long> busy = new HashSet<>();
    private final Map<UUID, Long> zones = new HashMap<>();
    private final Map<String, Long> recentVisits = new HashMap<>();
    private final Map<UUID, Long> warnings = new HashMap<>();

    private IslandLayout layout;
    private World world;
    private boolean enabled;
    private boolean spawnBuilt;
    private boolean dirty;
    private Hologram spawnHologram;
    private int tickCount;

    public IslandManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.builder = new IslandBuilder(plugin);
        this.file = new File(new File(plugin.getDataFolder(), "data"), "islands.yml");
    }

    private Settings settings() {
        return plugin.getSettings();
    }

    // ------------------------------------------------------------------
    // Opstart
    // ------------------------------------------------------------------

    /** Opretter/indlæser HayDay-verdenen. Skal kaldes før markerne indlæses. */
    public void setup() {
        Settings settings = settings();
        enabled = false;
        if (!settings.islandsEnabled) {
            return;
        }
        loadData();
        String name = settings.islandWorld;
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            if (!(existing.getGenerator() instanceof IslandGenerator)) {
                plugin.getLogger().severe("Verdenen '" + name + "' findes allerede, men er ikke lavet af HayDay. "
                        + "Vælg et andet navn under islands.world i config.yml. Øerne er slået fra.");
                return;
            }
            world = existing;
        } else {
            File folder = new File(Bukkit.getWorldContainer(), name);
            if (new File(folder, "level.dat").exists() && !new File(folder, MARKER).exists()) {
                plugin.getLogger().severe("Mappen '" + name + "' indeholder en verden der ikke er lavet af HayDay. "
                        + "Vælg et andet navn under islands.world i config.yml. Øerne er slået fra.");
                return;
            }
            plugin.getLogger().info("Indlæser HayDay-verdenen '" + name + "' (første gang tager det et øjeblik) ...");
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new IslandGenerator(layout))
                    .generateStructures(false)
                    .createWorld();
            if (world == null) {
                plugin.getLogger().severe("Kunne ikke oprette HayDay-verdenen '" + name + "'. Øerne er slået fra.");
                return;
            }
            writeMarker(new File(Bukkit.getWorldContainer(), name));
        }
        enabled = true;
        configureWorld();
        if (!spawnBuilt) {
            builder.buildSpawn(world, layout);
            spawnBuilt = true;
            dirty = true;
        }
        createSpawnHologram();
        for (Island island : byOwner.values()) {
            createHologram(island);
        }
        plugin.getLogger().info("HayDay-øer er klar: " + byOwner.size() + " øer i verdenen '" + name + "'.");
    }

    /** /hayday reload: nye tekster og verdens-indstillinger (selve verdenen kræver en genstart at skifte). */
    public void reload() {
        if (settings().islandsEnabled != enabled || (enabled && !settings().islandWorld.equals(world.getName()))) {
            plugin.getLogger().warning("Ændringer af islands.enabled/islands.world kræver en genstart af serveren.");
        }
        if (!enabled) {
            return;
        }
        configureWorld();
        if (spawnHologram != null) {
            spawnHologram.setLines(settings().islandSpawnHologram);
        }
        for (Island island : byOwner.values()) {
            refreshHologram(island);
        }
    }

    private void configureWorld() {
        world.setSpawnLocation(spawnLocation());
        try {
            world.setDifficulty(Difficulty.valueOf(settings().islandDifficulty.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ukendt sværhedsgrad '" + settings().islandDifficulty + "' (brug peaceful/easy/normal/hard).");
        }
        if (settings().islandNoWeather) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    private void writeMarker(File folder) {
        try {
            Files.write(new File(folder, MARKER).toPath(),
                    "Denne verden er lavet af HayDay-pluginet (øer til hver spiller).\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke skrive " + MARKER + ": " + e.getMessage());
        }
    }

    private void createSpawnHologram() {
        double height = plugin.getStructures().get("fountain") != null ? 5.4 : 3.2;
        Location location = new Location(world, layout.centerX(0) + 0.5, layout.getHeight() + height, layout.centerZ(0) + 0.5);
        spawnHologram = plugin.getHolograms().create(location, settings().islandSpawnHologram,
                player -> new VisitMenu(plugin, player, 0).open());
    }

    // ------------------------------------------------------------------
    // Gemning
    // ------------------------------------------------------------------

    /**
     * Øernes geometri: fra data/islands.yml hvis verdenen allerede er lavet, ellers fra config.yml.
     * Bruges også når verdenen indlæses via bukkit.yml/Multiverse før pluginet er startet.
     */
    public static IslandLayout readLayout(File dataFolder, org.bukkit.configuration.file.FileConfiguration config) {
        File data = new File(new File(dataFolder, "data"), "islands.yml");
        int size = config.getInt("islands.size", 48);
        int gap = config.getInt("islands.gap", 24);
        int height = config.getInt("islands.height", 64);
        if (data.exists()) {
            ConfigurationSection stored = YamlConfiguration.loadConfiguration(data).getConfigurationSection("layout");
            if (stored != null) {
                return new IslandLayout(stored.getInt("size", size), stored.getInt("gap", gap), stored.getInt("height", height));
            }
        }
        return new IslandLayout(size, gap, height);
    }

    private void loadData() {
        byOwner.clear();
        byGrid.clear();
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        Settings settings = settings();
        ConfigurationSection root = config.getConfigurationSection("islands");
        ConfigurationSection stored = config.getConfigurationSection("layout");
        if (stored != null) {
            layout = new IslandLayout(stored.getInt("size", settings.islandSize), stored.getInt("gap", settings.islandGap),
                    stored.getInt("height", settings.islandHeight));
            if (layout.getSize() != settings.islandSize || layout.getGap() != settings.islandGap
                    || layout.getHeight() != settings.islandHeight) {
                plugin.getLogger().warning("Øernes størrelse/afstand/højde kan ikke ændres når verdenen findes - bruger size="
                        + layout.getSize() + ", gap=" + layout.getGap() + ", height=" + layout.getHeight() + ".");
            }
        } else {
            layout = new IslandLayout(settings.islandSize, settings.islandGap, settings.islandHeight);
            dirty = true;
        }
        spawnBuilt = config.getBoolean("spawn-built", false);
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Island island = new Island(UUID.fromString(key), section.getString("name", "?"),
                        section.getInt("x"), section.getInt("z"));
                island.setFarmName(section.getString("farm-name", null));
                island.setHome(parseHome(section.getString("home", null)));
                island.setAccess(Island.Access.parse(section.getString("access"), Island.Access.ALLE));
                readNames(section.getConfigurationSection("friends"), island.getFriends());
                readNames(section.getConfigurationSection("banned"), island.getBanned());
                Set<UUID> likes = new HashSet<>();
                for (String like : section.getStringList("likes")) {
                    try {
                        likes.add(UUID.fromString(like));
                    } catch (IllegalArgumentException ignored) {
                        // ugyldig uuid
                    }
                }
                island.setLikes(likes);
                island.setVisits(section.getInt("visits", 0));
                island.setCreated(section.getLong("created", 0));
                island.setStyle(section.getInt("style", 1));
                if (byGrid.containsKey(island.key())) {
                    plugin.getLogger().warning("To øer har samme plads (" + island.getGridX() + ", " + island.getGridZ() + ") - "
                            + key + " springes over.");
                    continue;
                }
                register(island);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ugyldig ø i islands.yml: " + key);
            }
        }
    }

    private static void readNames(ConfigurationSection section, Map<UUID, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                target.put(UUID.fromString(key), section.getString(key, "?"));
            } catch (IllegalArgumentException ignored) {
                // ugyldig uuid
            }
        }
    }

    private static double[] parseHome(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String[] parts = input.split(";");
        if (parts.length != 5) {
            return null;
        }
        try {
            double[] home = new double[5];
            for (int i = 0; i < 5; i++) {
                home[i] = Double.parseDouble(parts[i]);
            }
            return home;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void save(boolean sync) {
        if (layout == null || (!dirty && !sync)) {
            return;
        }
        dirty = false;
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(Collections.singletonList(
                "HayDay - alle øer. Ret ikke i layout-sektionen når verdenen først er lavet."));
        config.set("layout.size", layout.getSize());
        config.set("layout.gap", layout.getGap());
        config.set("layout.height", layout.getHeight());
        config.set("spawn-built", spawnBuilt);
        for (Island island : byOwner.values()) {
            String path = "islands." + island.getOwner();
            config.set(path + ".name", island.getOwnerName());
            config.set(path + ".x", island.getGridX());
            config.set(path + ".z", island.getGridZ());
            config.set(path + ".farm-name", island.getFarmName());
            double[] home = island.getHome();
            if (home != null) {
                config.set(path + ".home", home[0] + ";" + home[1] + ";" + home[2] + ";" + home[3] + ";" + home[4]);
            }
            config.set(path + ".access", island.getAccess().id());
            for (Map.Entry<UUID, String> friend : island.getFriends().entrySet()) {
                config.set(path + ".friends." + friend.getKey(), friend.getValue());
            }
            for (Map.Entry<UUID, String> ban : island.getBanned().entrySet()) {
                config.set(path + ".banned." + ban.getKey(), ban.getValue());
            }
            List<String> likes = new ArrayList<>();
            for (UUID like : island.getLikes()) {
                likes.add(like.toString());
            }
            config.set(path + ".likes", likes);
            config.set(path + ".visits", island.getVisits());
            config.set(path + ".created", island.getCreated());
            config.set(path + ".style", island.getStyle());
        }
        String data = config.saveToString();
        if (sync) {
            write(data);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                synchronized (this) {
                    write(data);
                }
            });
        }
    }

    private void write(String data) {
        try {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IOException("kunne ikke oprette " + parent.getPath());
            }
            File temp = new File(parent, file.getName() + ".tmp");
            Files.write(temp.toPath(), data.getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke gemme islands.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Opslag
    // ------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public World getWorld() {
        return world;
    }

    public IslandLayout getLayout() {
        return layout;
    }

    public boolean isIslandWorld(World other) {
        return enabled && other != null && world != null && other.getName().equals(world.getName());
    }

    public Island get(UUID owner) {
        return byOwner.get(owner);
    }

    public Collection<Island> all() {
        return Collections.unmodifiableCollection(byOwner.values());
    }

    public int count() {
        return byOwner.size();
    }

    /** Øen der dækker (x, z) i HayDay-verdenen, eller null (hav, torvet eller en ledig plads). */
    public Island getAt(int x, int z) {
        if (!enabled || !layout.inSquare(x, z)) {
            return null;
        }
        return byGrid.get(IslandLayout.key(layout.gridX(x), layout.gridZ(z)));
    }

    public Island getAt(Location location) {
        return location != null && isIslandWorld(location.getWorld()) ? getAt(location.getBlockX(), location.getBlockZ()) : null;
    }

    /** Hvilken "zone" (ø-plads eller hav) et punkt ligger i - bruges til at holde vand, ild og stempler på egen ø. */
    public long zone(int x, int z) {
        return layout.inSquare(x, z) ? IslandLayout.key(layout.gridX(x), layout.gridZ(z)) : SEA;
    }

    public boolean contains(Island island, int x, int z) {
        return layout.inSquare(x, z) && layout.gridX(x) == island.getGridX() && layout.gridZ(z) == island.getGridZ();
    }

    public boolean contains(Island island, BlockPos pos) {
        return world != null && pos.getWorldName().equals(world.getName()) && contains(island, pos.getX(), pos.getZ());
    }

    /** Gårdens navn: eget navn eller "Tobias' gård". */
    public String farmName(Island island) {
        return island.getFarmName() != null ? island.getFarmName()
                : Text.replace(settings().islandDefaultName, "owner", island.getOwnerName());
    }

    public String accessName(Island.Access access) {
        return plugin.getMessages().get("island.access-names." + access.id());
    }

    /** Ejerens level - også når ejeren er offline (fra top-listen). */
    public int level(Island island) {
        PlayerData data = plugin.getPlayers().getIfLoaded(island.getOwner());
        if (data != null) {
            return data.getLevel();
        }
        LeaderboardManager.Entry entry = plugin.getLeaderboard().get(island.getOwner());
        return entry == null ? 1 : entry.getLevel();
    }

    /** Spillere der lige nu er på øen (ikke ejeren). */
    public List<Player> visitors(Island island) {
        List<Player> list = new ArrayList<>();
        if (!enabled) {
            return list;
        }
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            if (!island.isOwner(player.getUniqueId()) && contains(island, location.getBlockX(), location.getBlockZ())) {
                list.add(player);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Regler
    // ------------------------------------------------------------------

    /** Må spilleren bygge på (x, z) i HayDay-verdenen? Kun på egen ø (eller med hayday.bypass). */
    public boolean canBuild(Player player, int x, int z) {
        if (player.hasPermission("hayday.bypass")) {
            return true;
        }
        Island island = getAt(x, z);
        return island != null && island.isOwner(player.getUniqueId());
    }

    public boolean canEnter(Player player, Island island) {
        if (island.isOwner(player.getUniqueId()) || player.hasPermission("hayday.bypass")) {
            return true;
        }
        if (island.isBanned(player.getUniqueId())) {
            return false;
        }
        switch (island.getAccess()) {
            case ALLE:
                return true;
            case VENNER:
                return island.isFriend(player.getUniqueId());
            default:
                return false;
        }
    }

    /** Fortæller spilleren hvorfor de ikke må komme ind på øen. */
    public void sendDenied(Player player, Island island) {
        plugin.getMessages().send(player, deniedKey(player, island), "owner", island.getOwnerName(), "farm", farmName(island));
        Sounds.play(player, Sounds.ERROR);
    }

    public String deniedKey(Player player, Island island) {
        return island.isBanned(player.getUniqueId()) ? "island.banned"
                : island.getAccess() == Island.Access.VENNER ? "island.friends-only" : "island.closed";
    }

    /** Besked med en lille pause, så den ikke spammer når man fx går langs en lukket ø. */
    public void warn(Player player, String key, Object... placeholders) {
        long now = System.currentTimeMillis();
        Long last = warnings.get(player.getUniqueId());
        if (last != null && now - last < 2000) {
            return;
        }
        warnings.put(player.getUniqueId(), now);
        plugin.getMessages().send(player, key, placeholders);
        Sounds.play(player, Sounds.ERROR);
    }

    /**
     * Må spilleren placere en mark/bygning her? Med øer slået til: kun på egen ø. Sender en besked hvis ikke.
     */
    public boolean allowsFarm(Player player, Block block) {
        Settings settings = settings();
        boolean bypass = player.hasPermission("hayday.bypass");
        if (enabled && isIslandWorld(block.getWorld())) {
            Island island = getAt(block.getX(), block.getZ());
            if (bypass || (island != null && island.isOwner(player.getUniqueId()))) {
                return true;
            }
            plugin.getMessages().send(player, "island.not-your-island");
            return false;
        }
        if (enabled && settings.islandFarmOnly && !bypass) {
            plugin.getMessages().send(player, "island.farm-only");
            return false;
        }
        if (!settings.isWorldAllowed(block.getWorld())) {
            plugin.getMessages().send(player, "general.world-not-allowed");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Oprettelse
    // ------------------------------------------------------------------

    /**
     * Spillerens ø - laves hvis den ikke findes. {@code starterFields} marker lægges på en ny ø.
     * Returnerer null hvis øerne er slået fra.
     */
    public Island getOrCreate(Player player, int starterFields) {
        Island island = byOwner.get(player.getUniqueId());
        if (island != null || !enabled) {
            return island;
        }
        int[] grid = nextFree();
        island = new Island(player.getUniqueId(), player.getName(), grid[0], grid[1]);
        island.setAccess(Island.Access.parse(settings().islandDefaultAccess, Island.Access.ALLE));
        island.setCreated(System.currentTimeMillis());
        register(island);
        if (settings().islandStarterLayout) {
            builder.buildStarter(world, layout, island, starterFields);
        }
        createHologram(island);
        dirty = true;
        save(false);
        plugin.getMessages().send(player, "island.created", "farm", farmName(island));
        return island;
    }

    private void register(Island island) {
        byOwner.put(island.getOwner(), island);
        byGrid.put(island.key(), island);
    }

    /** Næste ledige plads, ring for ring rundt om torvet. */
    private int[] nextFree() {
        for (int ring = 1; ring <= 2000; ring++) {
            for (int gx = -ring; gx <= ring; gx++) {
                for (int gz = -ring; gz <= ring; gz++) {
                    if (Math.max(Math.abs(gx), Math.abs(gz)) != ring) {
                        continue;
                    }
                    long key = IslandLayout.key(gx, gz);
                    if (!byGrid.containsKey(key) && !busy.contains(key)) {
                        return new int[]{gx, gz};
                    }
                }
            }
        }
        throw new IllegalStateException("Ingen ledige øer");
    }

    // ------------------------------------------------------------------
    // Hologrammer
    // ------------------------------------------------------------------

    private void createHologram(Island island) {
        if (!enabled || island.getHologram() != null) {
            return;
        }
        if (island.getStyle() >= 2) {
            createProps(island);
            for (Landmark landmark : Landmark.values()) {
                Location location = landmarkHologramLocation(island, landmark);
                if (location != null) {
                    island.getLandmarkHolograms().put(landmark, plugin.getHolograms().create(location,
                            landmarkLines(island, landmark), player -> useLandmark(player, island, landmark)));
                }
            }
        }
        Location location = IslandBuilder.signLocation(world, layout, island);
        island.setHologram(plugin.getHolograms().create(location, hologramLines(island), player -> {
            if (island.isOwner(player.getUniqueId())) {
                new FarmMenu(plugin, player).open();
            } else {
                like(player, island);
            }
        }));
    }

    private void removeHologram(Island island) {
        if (island.getHologram() != null) {
            plugin.getHolograms().remove(island.getHologram());
            island.setHologram(null);
        }
        for (Hologram hologram : island.getLandmarkHolograms().values()) {
            plugin.getHolograms().remove(hologram);
        }
        island.getLandmarkHolograms().clear();
        plugin.getProps().removeAll(island.getProps());
        island.setShip(null);
    }

    /** 3D-modellerne på gården (kun synlige for spillere med resourcepacken). */
    private void createProps(Island island) {
        double ground = layout.getHeight() + 1;
        for (IslandBuilder.FarmProp prop : IslandBuilder.FARM_PROPS) {
            island.getProps().add(createProp(island, prop, ground));
        }
        PropManager.Prop ship = createProp(island, IslandBuilder.SHIP, ground);
        ship.setEnabled(false);
        island.getProps().add(ship);
        island.setShip(ship);
    }

    private PropManager.Prop createProp(Island island, IslandBuilder.FarmProp prop, double ground) {
        double x = layout.centerX(island.getGridX()) + (prop.x - 24);
        double z = layout.centerZ(island.getGridZ()) + (prop.z - 24);
        return plugin.getProps().create(world.getName(), x, ground + prop.dy, z, prop.yaw, prop.scale, prop.model, 0f);
    }

    public List<String> hologramLines(Island island) {
        List<String> lines = new ArrayList<>();
        for (String line : settings().islandHologram) {
            lines.add(Text.replace(line, "farm", farmName(island), "owner", island.getOwnerName(),
                    "level", level(island), "likes", island.getLikes().size(), "visits", island.getVisits(),
                    "access", accessName(island.getAccess())));
        }
        return lines;
    }

    public void refreshHologram(Island island) {
        if (island.getHologram() != null) {
            island.getHologram().setLines(hologramLines(island));
        }
        for (Map.Entry<Landmark, Hologram> entry : island.getLandmarkHolograms().entrySet()) {
            entry.getValue().setLines(landmarkLines(island, entry.getKey()));
        }
    }

    // ------------------------------------------------------------------
    // Gårdens faste bygninger: lade, silo, ordretavle og postkasse
    // ------------------------------------------------------------------

    private int landmarkX(Island island, Landmark landmark) {
        return IslandBuilder.x(layout, island, landmark.x());
    }

    private int landmarkZ(Island island, Landmark landmark) {
        return IslandBuilder.z(layout, island, landmark.z());
    }

    /** Hvilken af gårdens faste bygninger blokken hører til (null hvis ingen). */
    public Landmark landmarkAt(Block block) {
        if (!enabled || !isIslandWorld(block.getWorld())) {
            return null;
        }
        Island island = getAt(block.getX(), block.getZ());
        if (island == null || island.getStyle() < 2) {
            return null;
        }
        for (Landmark landmark : Landmark.values()) {
            Structure structure = plugin.getStructures().get(landmark.structure());
            if (structure != null && structure.contains(block.getX() - landmarkX(island, landmark),
                    block.getY() - layout.getHeight(), block.getZ() - landmarkZ(island, landmark), 0)) {
                return landmark;
            }
        }
        return null;
    }

    private Location landmarkHologramLocation(Island island, Landmark landmark) {
        Structure structure = plugin.getStructures().get(landmark.structure());
        if (structure == null) {
            return null;
        }
        double x = landmarkX(island, landmark) + 0.5;
        double z = landmarkZ(island, landmark) + 0.5;
        if (landmark == Landmark.BARN || landmark == Landmark.ORDERS) {
            z -= 1.0;
        }
        return new Location(world, x, layout.getHeight() + structure.getHologramHeight(), z);
    }

    private List<String> landmarkLines(Island island, Landmark landmark) {
        List<String> template = settings().islandLandmarkLines.get(landmark.id());
        if (template == null) {
            return Collections.singletonList("");
        }
        PlayerData data = plugin.getPlayers().getIfLoaded(island.getOwner());
        String used = "-";
        String capacity = "-";
        String ready = "-";
        if (data != null) {
            if (landmark == Landmark.BARN || landmark == Landmark.SILO) {
                ItemCategory category = landmark == Landmark.BARN ? ItemCategory.PRODUCT : ItemCategory.CROP;
                used = String.valueOf(plugin.getStorage().used(data, category));
                capacity = String.valueOf(plugin.getStorage().capacity(data, category));
            } else if (landmark == Landmark.ORDERS) {
                ready = String.valueOf(plugin.getOrders().countReady(data));
            }
        }
        int offers = landmark == Landmark.MAILBOX ? plugin.getMarket().getNewspaper(island.getOwner()).size() : 0;
        List<String> lines = new ArrayList<>();
        for (String line : template) {
            lines.add(Text.replace(line, "owner", island.getOwnerName(), "used", used, "capacity", capacity,
                    "ready", ready, "offers", offers));
        }
        return lines;
    }

    /** Klik på laden, siloen, ordretavlen eller postkassen. */
    public void useLandmark(Player player, Island island, Landmark landmark) {
        if (landmark == Landmark.MAILBOX) {
            plugin.getService().ensureStarted(player);
            new NewspaperMenu(plugin, player, 0).open();
            return;
        }
        if (!island.isOwner(player.getUniqueId())) {
            plugin.getMessages().send(player, "island.landmark-visitor", "owner", island.getOwnerName(),
                    "landmark", plugin.getMessages().get("island.landmark-names." + landmark.id()));
            return;
        }
        plugin.getService().ensureStarted(player);
        switch (landmark) {
            case BARN:
                new StorageMenu(plugin, player, ItemCategory.PRODUCT).open();
                break;
            case SILO:
                new StorageMenu(plugin, player, ItemCategory.CROP).open();
                break;
            default:
                new OrdersMenu(plugin, player).open();
                break;
        }
        Sounds.play(player, Sounds.CLICK);
    }

    /**
     * Ejeren bygger sin gård om i den nye stil. Marker og bygninger lægges i ejerens inventory,
     * og øen tegnes forfra med stuehus, lade, silo osv.
     */
    public boolean rebuild(Player player) {
        Island island = byOwner.get(player.getUniqueId());
        if (island == null || busy.contains(island.key())) {
            return false;
        }
        int fields = 0;
        for (Field field : plugin.getFarm().getFields(player.getUniqueId())) {
            if (contains(island, field.getSoil())) {
                fields++;
            }
        }
        List<ItemStack> refund = new ArrayList<>();
        if (fields > 0) {
            refund.add(PlaceableItems.field(fields));
        }
        for (Building building : plugin.getFarm().getBuildings(player.getUniqueId())) {
            BuildingType type = plugin.getBuildings().get(building.getTypeId());
            if (type != null && contains(island, building.getPos())) {
                refund.add(PlaceableItems.building(type, plugin.getItems(), 1));
            }
        }
        reset(island, false, () -> {
            if (player.isOnline()) {
                for (ItemStack stack : refund) {
                    PlaceableItems.give(player, stack);
                }
                plugin.getMessages().send(player, "island.rebuilt", "farm", farmName(island));
                teleportHome(player);
            }
        });
        return true;
    }

    /** Admin: byg torvets pynt igen (fx efter en opdatering). */
    public void rebuildSpawn() {
        if (!enabled) {
            return;
        }
        builder.buildSpawn(world, layout);
        if (spawnHologram != null) {
            plugin.getHolograms().remove(spawnHologram);
        }
        createSpawnHologram();
    }

    // ------------------------------------------------------------------
    // Teleport og besøg
    // ------------------------------------------------------------------

    public Location spawnLocation() {
        return new Location(world, layout.centerX(0) + 0.5, layout.getHeight() + 1, layout.centerZ(0) + 6.5, 180f, 0f);
    }

    /** Øens hjem: det spilleren selv har sat, ellers stranden ved stien. */
    public Location home(Island island) {
        double[] home = island.getHome();
        if (home != null) {
            return new Location(world, home[0], home[1], home[2], (float) home[3], (float) home[4]);
        }
        int x = layout.centerX(island.getGridX());
        int z = layout.maxZ(island.getGridZ()) - 3;
        int y = Math.max(layout.getHeight(), world.getHighestBlockYAt(x, z)) + 1;
        return new Location(world, x + 0.5, y, z + 0.5, 180f, 0f);
    }

    public void teleportHome(Player player) {
        Island island = getOrCreate(player, 0);
        if (island == null) {
            plugin.getMessages().send(player, "island.disabled");
            return;
        }
        player.teleport(home(island));
        Sounds.play(player, "entity.enderman.teleport", 1.2f);
        plugin.getMessages().send(player, "island.home", "farm", farmName(island));
    }

    public void teleportSpawn(Player player) {
        if (!enabled) {
            plugin.getMessages().send(player, "island.disabled");
            return;
        }
        player.teleport(spawnLocation());
        Sounds.play(player, "entity.enderman.teleport", 1.2f);
        plugin.getMessages().send(player, "island.spawn");
    }

    /** Besøg en andens ø (eller din egen). */
    public void visit(Player player, Island island) {
        if (island.isOwner(player.getUniqueId())) {
            teleportHome(player);
            return;
        }
        if (!canEnter(player, island)) {
            sendDenied(player, island);
            return;
        }
        player.teleport(home(island));
        Sounds.play(player, "entity.enderman.teleport", 1.2f);
        plugin.getMessages().send(player, "island.visiting", "farm", farmName(island), "owner", island.getOwnerName());
    }

    /** Sender en spiller væk fra en ø (til torvet). */
    public void kick(Player player, Island island, String messageKey) {
        player.teleport(spawnLocation());
        plugin.getMessages().send(player, messageKey, "owner", island.getOwnerName(), "farm", farmName(island));
        Sounds.play(player, Sounds.ERROR);
    }

    /** Smider alle ud som ikke må være på øen længere (fx efter at adgangen er ændret). */
    public void kickForbidden(Island island) {
        for (Player visitor : visitors(island)) {
            if (!canEnter(visitor, island)) {
                kick(visitor, island, "island.kicked");
            }
        }
    }

    /** Kaldes når en spiller flytter sig til et nyt sted: titler og besøgstæller. */
    public void updateZone(Player player, Location to) {
        long zone = to != null && isIslandWorld(to.getWorld()) ? zone(to.getBlockX(), to.getBlockZ()) : OUTSIDE;
        Long previous = zones.put(player.getUniqueId(), zone);
        if (previous != null && previous == zone) {
            return;
        }
        if (zone == OUTSIDE || zone == SEA) {
            return;
        }
        Island island = byGrid.get(zone);
        if (island == null) {
            if (zone == IslandLayout.key(0, 0)) {
                title(player, "island.spawn-title", "island.spawn-subtitle");
            }
            return;
        }
        String farm = farmName(island);
        if (island.isOwner(player.getUniqueId())) {
            title(player, "island.home-title", "island.home-subtitle", "farm", farm);
            return;
        }
        title(player, "island.enter-title", "island.enter-subtitle", "farm", farm, "owner", island.getOwnerName(),
                "level", level(island), "likes", island.getLikes().size());
        registerVisit(player, island);
    }

    private void title(Player player, String titleKey, String subtitleKey, Object... placeholders) {
        player.sendTitle(plugin.getMessages().get(titleKey, placeholders), plugin.getMessages().get(subtitleKey, placeholders),
                5, 40, 10);
    }

    private void registerVisit(Player visitor, Island island) {
        String key = visitor.getUniqueId() + ":" + island.getOwner();
        long now = System.currentTimeMillis();
        Long last = recentVisits.get(key);
        if (last != null && now - last < VISIT_COOLDOWN) {
            return;
        }
        recentVisits.put(key, now);
        island.setVisits(island.getVisits() + 1);
        dirty = true;
        refreshHologram(island);
        Player owner = Bukkit.getPlayer(island.getOwner());
        if (owner != null) {
            plugin.getMessages().send(owner, "island.visitor-joined", "player", visitor.getName());
            Sounds.play(owner, "block.note_block.bell", 1.4f);
        }
    }

    public void forget(Player player) {
        zones.remove(player.getUniqueId());
        warnings.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Ejerens indstillinger
    // ------------------------------------------------------------------

    public void like(Player player, Island island) {
        if (island.isOwner(player.getUniqueId())) {
            plugin.getMessages().send(player, "island.like-own");
            return;
        }
        if (!island.addLike(player.getUniqueId())) {
            plugin.getMessages().send(player, "island.already-liked", "farm", farmName(island));
            return;
        }
        dirty = true;
        refreshHologram(island);
        plugin.getMessages().send(player, "island.liked", "farm", farmName(island));
        Sounds.play(player, Sounds.SUCCESS, 1.4f);
        plugin.getAnimations().floatingText(player.getLocation().add(0, 2.3, 0), "&c❤ &f+1");
        Player owner = Bukkit.getPlayer(island.getOwner());
        if (owner != null) {
            plugin.getMessages().send(owner, "island.like-notify", "player", player.getName());
        }
    }

    public void setAccess(Island island, Island.Access access) {
        island.setAccess(access);
        dirty = true;
        refreshHologram(island);
        kickForbidden(island);
    }

    public void setFarmName(Island island, String name) {
        island.setFarmName(name);
        dirty = true;
        refreshHologram(island);
    }

    public void setHome(Island island, Location location) {
        island.setHome(new double[]{location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()});
        dirty = true;
    }

    public boolean addFriend(Island island, UUID uuid, String name) {
        if (island.getFriends().containsKey(uuid)) {
            return false;
        }
        island.getFriends().put(uuid, name);
        island.getBanned().remove(uuid);
        dirty = true;
        return true;
    }

    public boolean removeFriend(Island island, UUID uuid) {
        if (island.getFriends().remove(uuid) == null) {
            return false;
        }
        dirty = true;
        kickForbidden(island);
        return true;
    }

    public boolean ban(Island island, UUID uuid, String name) {
        if (island.getBanned().containsKey(uuid)) {
            return false;
        }
        island.getBanned().put(uuid, name);
        island.getFriends().remove(uuid);
        dirty = true;
        kickForbidden(island);
        return true;
    }

    public boolean unban(Island island, UUID uuid) {
        if (island.getBanned().remove(uuid) == null) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** Opdaterer ejer- og vennenavne når en spiller logger ind med et nyt navn. */
    public void updateName(Player player) {
        Island own = byOwner.get(player.getUniqueId());
        if (own != null && !player.getName().equals(own.getOwnerName())) {
            own.setOwnerName(player.getName());
            dirty = true;
            refreshHologram(own);
        }
        for (Island island : byOwner.values()) {
            if (island.getFriends().containsKey(player.getUniqueId())) {
                island.getFriends().put(player.getUniqueId(), player.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // Admin: nulstil og slet
    // ------------------------------------------------------------------

    /**
     * Fjerner alle marker og bygninger på øen og tegner den helt om. Ved {@code delete} bliver pladsen fri
     * (spilleren får en ny ø næste gang); ellers bygges startgården igen på samme plads.
     */
    public void reset(Island island, boolean delete, Runnable done) {
        long key = island.key();
        busy.add(key);
        for (Field field : new ArrayList<>(plugin.getFarm().getFields())) {
            if (contains(island, field.getSoil())) {
                plugin.getFarm().removeField(field);
            }
        }
        for (Building building : new ArrayList<>(plugin.getFarm().getBuildings())) {
            if (contains(island, building.getPos())) {
                plugin.getFarm().removeBuilding(building);
            }
        }
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            if (contains(island, location.getBlockX(), location.getBlockZ())) {
                player.teleport(spawnLocation());
            }
        }
        removeHologram(island);
        if (delete) {
            byOwner.remove(island.getOwner());
            byGrid.remove(key);
        } else {
            island.setHome(null);
        }
        dirty = true;
        save(false);
        builder.repaint(world, layout, island.getGridX(), island.getGridZ(), () -> {
            busy.remove(key);
            if (!delete && byOwner.get(island.getOwner()) == island) {
                if (settings().islandStarterLayout) {
                    builder.buildStarter(world, layout, island, 0);
                }
                dirty = true;
                createHologram(island);
            }
            done.run();
        });
    }

    // ------------------------------------------------------------------
    // Opdatering
    // ------------------------------------------------------------------

    public void tick() {
        if (!enabled) {
            return;
        }
        tickCount++;
        if (settings().islandAlwaysDay) {
            world.setTime(6000L);
        }
        // Har ejeren lukket øen (eller er man blevet forbudt), bliver man sendt til torvet
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            Island island = getAt(location.getBlockX(), location.getBlockZ());
            if (island != null && !canEnter(player, island)) {
                kick(player, island, "island.kicked");
            }
        }
        if (tickCount % 5 == 0) {
            for (Island island : byOwner.values()) {
                refreshHologram(island);
            }
        }
        // Skibet ligger ved bryggen når ejerens skib er i havn
        for (Island island : byOwner.values()) {
            if (island.getShip() != null) {
                PlayerData data = plugin.getPlayers().getIfLoaded(island.getOwner());
                island.getShip().setEnabled(data != null && plugin.getShip().state(data) == ShipManager.State.DOCKED);
            }
        }
        if (tickCount % 600 == 0) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Long>> iterator = recentVisits.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue() > VISIT_COOLDOWN) {
                    iterator.remove();
                }
            }
        }
    }

    /** En spiller der logger ind på en lukket ø, bliver sendt til torvet. */
    public void checkPosition(Player player) {
        Island island = getAt(player.getLocation());
        if (island != null && !canEnter(player, island)) {
            kick(player, island, "island.kicked");
        }
        updateZone(player, player.getLocation());
    }

    public void shutdown() {
        save(true);
    }
}
