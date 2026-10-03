package dev.tardyc.hayday.hologram;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hologrammer som admins opretter med /hayday holo (tekst eller top-liste). Gemmes i holograms.yml.
 */
public final class AdminHologramManager {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_TOP = "top";

    private final HayDayPlugin plugin;
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private int tickCounter;

    public AdminHologramManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    public void load() {
        for (Entry entry : entries.values()) {
            despawn(entry);
        }
        entries.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("holograms");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Entry entry = new Entry(name,
                    section.getString("world", "world"),
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    section.getString("type", TYPE_TEXT),
                    new ArrayList<>(section.getStringList("lines")),
                    section.getString("command", ""));
            entries.put(name.toLowerCase(Locale.ROOT), entry);
        }
        spawnAvailable();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Entry entry : entries.values()) {
            String path = "holograms." + entry.name;
            config.set(path + ".world", entry.world);
            config.set(path + ".x", entry.x);
            config.set(path + ".y", entry.y);
            config.set(path + ".z", entry.z);
            config.set(path + ".type", entry.type);
            config.set(path + ".lines", entry.lines);
            config.set(path + ".command", entry.command);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke gemme holograms.yml: " + e.getMessage());
        }
    }

    /** Opretter hologrammer for verdener der er indlæst (kaldes også når en verden indlæses). */
    public void spawnAvailable() {
        for (Entry entry : entries.values()) {
            if (entry.hologram == null && Bukkit.getWorld(entry.world) != null) {
                entry.hologram = plugin.getHolograms().create(entry.location(), render(entry), clickAction(entry));
            }
        }
    }

    public void tick() {
        // Top-lister opdateres hvert ~30. sekund
        if (++tickCounter * plugin.getSettings().updateInterval < 600) {
            return;
        }
        tickCounter = 0;
        refreshTop();
    }

    public void refreshTop() {
        for (Entry entry : entries.values()) {
            if (entry.hologram != null && entry.type.equals(TYPE_TOP)) {
                entry.hologram.setLines(render(entry));
            }
        }
    }

    public boolean exists(String name) {
        return entries.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Entry get(String name) {
        return entries.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Entry create(String name, Location location, String type, List<String> lines) {
        Entry entry = new Entry(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                type, new ArrayList<>(lines), "");
        entries.put(name.toLowerCase(Locale.ROOT), entry);
        entry.hologram = plugin.getHolograms().create(location, render(entry), clickAction(entry));
        save();
        return entry;
    }

    public void delete(Entry entry) {
        despawn(entry);
        entries.remove(entry.name.toLowerCase(Locale.ROOT));
        save();
    }

    public void update(Entry entry) {
        if (entry.hologram != null) {
            entry.hologram.setLines(render(entry));
            entry.hologram.setClickAction(clickAction(entry));
        }
        save();
    }

    public void move(Entry entry, Location location) {
        entry.world = location.getWorld().getName();
        entry.x = location.getX();
        entry.y = location.getY();
        entry.z = location.getZ();
        if (entry.hologram != null) {
            plugin.getHolograms().move(entry.hologram, location);
        } else {
            entry.hologram = plugin.getHolograms().create(location, render(entry), clickAction(entry));
        }
        save();
    }

    private void despawn(Entry entry) {
        if (entry.hologram != null) {
            plugin.getHolograms().remove(entry.hologram);
            entry.hologram = null;
        }
    }

    public void despawnWorld(World world) {
        for (Entry entry : entries.values()) {
            if (entry.world.equals(world.getName())) {
                despawn(entry);
            }
        }
    }

    private Consumer<Player> clickAction(Entry entry) {
        if (entry.command == null || entry.command.isEmpty()) {
            return null;
        }
        return player -> {
            String command = entry.command.startsWith("/") ? entry.command.substring(1) : entry.command;
            player.performCommand(command.replace("{player}", player.getName()));
        };
    }

    private List<String> render(Entry entry) {
        if (!entry.type.equals(TYPE_TOP)) {
            return entry.lines.isEmpty() ? Collections.singletonList("") : entry.lines;
        }
        Settings settings = plugin.getSettings();
        List<String> lines = new ArrayList<>(settings.topTitle);
        List<LeaderboardManager.Entry> top = plugin.getLeaderboard().top(settings.topSize);
        for (int i = 0; i < settings.topSize; i++) {
            if (i < top.size()) {
                LeaderboardManager.Entry e = top.get(i);
                lines.add(Text.replace(settings.topEntry, "rank", i + 1, "name", e.getName(),
                        "level", e.getLevel(), "xp", Text.number(e.getXp())));
            } else {
                lines.add(Text.replace(settings.topEmpty, "rank", i + 1));
            }
        }
        return lines;
    }

    /** Et gemt admin-hologram. */
    public static final class Entry {
        private final String name;
        private String world;
        private double x;
        private double y;
        private double z;
        private final String type;
        private final List<String> lines;
        private String command;
        private Hologram hologram;

        Entry(String name, String world, double x, double y, double z, String type, List<String> lines, String command) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type == null ? TYPE_TEXT : type.toLowerCase(Locale.ROOT);
            this.lines = lines;
            this.command = command == null ? "" : command;
        }

        Location location() {
            return new Location(Bukkit.getWorld(world), x, y, z);
        }

        public String getName() {
            return name;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public String getType() {
            return type;
        }

        public List<String> getLines() {
            return lines;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command == null ? "" : command;
        }

        public boolean isTop() {
            return TYPE_TOP.equals(type);
        }

        public Location getLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }
}
