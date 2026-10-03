package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Top-liste over spillere sorteret efter level og xp. Offline spillere indlæses asynkront ved opstart.
 */
public final class LeaderboardManager {

    private static final Comparator<Entry> ORDER = Comparator.<Entry>comparingInt(Entry::getLevel).reversed()
            .thenComparing(Comparator.<Entry>comparingLong(Entry::getXp).reversed())
            .thenComparing(Entry::getName, String.CASE_INSENSITIVE_ORDER);

    private final HayDayPlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private List<Entry> sorted = new ArrayList<>();
    private boolean dirty = true;

    public LeaderboardManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    /** Læser alle spillerfiler i baggrunden og fletter dem ind på main-tråden. */
    public void loadAsync(File folder) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Entry> read = new HashMap<>();
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    try {
                        UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        read.put(uuid, new Entry(uuid, config.getString("name", "?"), config.getInt("level", 1), config.getLong("xp", 0)));
                    } catch (IllegalArgumentException ignored) {
                        // ikke en spillerfil
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Map.Entry<UUID, Entry> entry : read.entrySet()) {
                    entries.putIfAbsent(entry.getKey(), entry.getValue());
                }
                dirty = true;
                plugin.getAdminHolograms().refreshTop();
            });
        });
    }

    public void update(PlayerData data) {
        Entry entry = entries.get(data.getUuid());
        if (entry != null && entry.level == data.getLevel() && entry.xp == data.getXp() && entry.name.equals(data.getName())) {
            return;
        }
        entries.put(data.getUuid(), new Entry(data.getUuid(), data.getName(), data.getLevel(), data.getXp()));
        dirty = true;
    }

    /** Top-liste-data for en spiller (også offline), eller null. */
    public Entry get(UUID uuid) {
        return entries.get(uuid);
    }

    /** Finder en spillers UUID ud fra navnet (også offline spillere der har en HayDay-fil). */
    public Entry findByName(String name) {
        for (Entry entry : entries.values()) {
            if (entry.getName().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Entry entry : entries.values()) {
            names.add(entry.getName());
        }
        return names;
    }

    public void remove(UUID uuid) {
        if (entries.remove(uuid) != null) {
            dirty = true;
        }
    }

    private List<Entry> sorted() {
        if (dirty) {
            List<Entry> list = new ArrayList<>(entries.values());
            list.sort(ORDER);
            sorted = list;
            dirty = false;
        }
        return sorted;
    }

    public List<Entry> top(int amount) {
        List<Entry> list = sorted();
        return Collections.unmodifiableList(list.subList(0, Math.min(amount, list.size())));
    }

    /** Placering (1 = bedst), eller 0 hvis spilleren ikke findes. */
    public int rank(UUID uuid) {
        List<Entry> list = sorted();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** En linje i top-listen. */
    public static final class Entry {
        private final UUID uuid;
        private final String name;
        private final int level;
        private final long xp;

        Entry(UUID uuid, String name, int level, long xp) {
            this.uuid = uuid;
            this.name = name == null ? "?" : name;
            this.level = level;
            this.xp = xp;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public long getXp() {
            return xp;
        }
    }
}
