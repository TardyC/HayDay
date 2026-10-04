package dev.tardyc.hayday.events;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Events: dobbelt penge, dobbelt XP, hurtig vækst, dobbelt høst og dobbelt produktion.
 * Aktive events vises i en bossbar, overlever genstart (data/events.yml) og kan starte automatisk.
 */
public final class EventManager {

    /** Et event der kører lige nu. */
    public static final class ActiveEvent {
        private final EventType type;
        private final double multiplier;
        private final long startedAt;
        private final long endsAt;
        private BossBar bar;

        ActiveEvent(EventType type, double multiplier, long startedAt, long endsAt) {
            this.type = type;
            this.multiplier = multiplier;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
        }

        public EventType getType() {
            return type;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public long getEndsAt() {
            return endsAt;
        }

        public long timeLeft() {
            return Math.max(0, endsAt - System.currentTimeMillis());
        }
    }

    private final HayDayPlugin plugin;
    private final File file;
    private final Map<EventType, ActiveEvent> active = new EnumMap<>(EventType.class);
    private long nextAutoAt;

    public EventManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(new File(plugin.getDataFolder(), "data"), "events.yml");
    }

    // ------------------------------------------------------------------
    // Gangefaktorer - bruges af resten af pluginet
    // ------------------------------------------------------------------

    public double multiplier(EventType type) {
        ActiveEvent event = active.get(type);
        return event == null || event.timeLeft() <= 0 ? 1.0 : event.multiplier;
    }

    public boolean isActive(EventType type) {
        return multiplier(type) != 1.0;
    }

    /** Penge fra salg, ordrer og skibet. */
    public double money(double amount) {
        return Math.round(amount * multiplier(EventType.PENGE) * 100.0) / 100.0;
    }

    public int xp(int amount) {
        return (int) Math.round(amount * multiplier(EventType.XP));
    }

    /** Vækst- og produktionstid (kortere under "hurtig vækst"). */
    public long time(long millis) {
        double factor = multiplier(EventType.VAEKST);
        return factor <= 0 ? millis : Math.max(1000L, (long) (millis / factor));
    }

    public int harvest(int amount) {
        return Math.max(1, (int) Math.round(amount * multiplier(EventType.HOEST)));
    }

    public int production(int amount) {
        return Math.max(1, (int) Math.round(amount * multiplier(EventType.PRODUKTION)));
    }

    // ------------------------------------------------------------------
    // Start og stop
    // ------------------------------------------------------------------

    public Collection<ActiveEvent> getActive() {
        return Collections.unmodifiableCollection(active.values());
    }

    public void start(EventType type, double multiplier, long durationMillis) {
        ActiveEvent old = active.remove(type);
        if (old != null) {
            hideBar(old);
        }
        long now = System.currentTimeMillis();
        ActiveEvent event = new ActiveEvent(type, Math.max(0.1, multiplier), now, now + Math.max(1000L, durationMillis));
        active.put(type, event);
        showBar(event);
        save();
        String name = name(event);
        String description = plugin.getMessages().get("events.descriptions." + type.id());
        String time = Text.timeMillis(event.timeLeft());
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getMessages().send(player, "events.started", "name", name, "description", description, "time", time);
            player.sendTitle(plugin.getMessages().get("events.started-title", "name", name),
                    plugin.getMessages().get("events.started-subtitle", "description", description, "time", time), 10, 60, 20);
            Sounds.play(player, "ui.toast.challenge_complete", 1.2f);
        }
        plugin.getLogger().info("Event startet: " + type.id() + " x" + formatMultiplier(event.multiplier) + " i " + time);
    }

    public boolean stop(EventType type, boolean announce) {
        ActiveEvent event = active.remove(type);
        if (event == null) {
            return false;
        }
        hideBar(event);
        save();
        if (announce) {
            String name = name(event);
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getMessages().send(player, "events.ended", "name", name);
            }
        }
        return true;
    }

    public int stopAll() {
        int count = 0;
        for (EventType type : new ArrayList<>(active.keySet())) {
            if (stop(type, true)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Visning
    // ------------------------------------------------------------------

    public static String formatMultiplier(double multiplier) {
        return multiplier == Math.floor(multiplier) ? String.valueOf((long) multiplier)
                : String.format(Locale.ROOT, "%.1f", multiplier);
    }

    public String name(EventType type) {
        return plugin.getMessages().get("events.names." + type.id());
    }

    public String name(ActiveEvent event) {
        return Text.replace(name(event.type), "x", formatMultiplier(event.multiplier));
    }

    /** "Dobbelt penge (12m 3s)" for alle aktive events - til menuer og placeholders. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (ActiveEvent event : active.values()) {
            lines.add(plugin.getMessages().get("events.list-entry", "name", name(event),
                    "x", formatMultiplier(event.multiplier), "time", Text.timeMillis(event.timeLeft())));
        }
        return lines;
    }

    private String barTitle(ActiveEvent event) {
        return plugin.getMessages().get("events.bossbar", "name", name(event), "x", formatMultiplier(event.multiplier),
                "time", Text.timeMillis(event.timeLeft()));
    }

    private void showBar(ActiveEvent event) {
        if (!plugin.getSettings().eventBossbar) {
            return;
        }
        event.bar = Bukkit.createBossBar(barTitle(event), event.type.color(), BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            event.bar.addPlayer(player);
        }
    }

    private void hideBar(ActiveEvent event) {
        if (event.bar != null) {
            event.bar.removeAll();
            event.bar = null;
        }
    }

    public void onJoin(Player player) {
        for (ActiveEvent event : active.values()) {
            if (event.bar != null) {
                event.bar.addPlayer(player);
            }
        }
    }

    public void onQuit(Player player) {
        for (ActiveEvent event : active.values()) {
            if (event.bar != null) {
                event.bar.removePlayer(player);
            }
        }
    }

    // ------------------------------------------------------------------
    // Opdatering, gemning og automatiske events
    // ------------------------------------------------------------------

    public void tick() {
        long now = System.currentTimeMillis();
        for (ActiveEvent event : new ArrayList<>(active.values())) {
            if (now >= event.endsAt) {
                stop(event.type, true);
                continue;
            }
            if (event.bar != null) {
                event.bar.setTitle(barTitle(event));
                double total = Math.max(1, event.endsAt - event.startedAt);
                event.bar.setProgress(Math.max(0, Math.min(1, (event.endsAt - now) / total)));
            }
        }
        Settings settings = plugin.getSettings();
        if (!settings.eventAutoEnabled || settings.eventAutoTypes.isEmpty()) {
            return;
        }
        if (nextAutoAt <= 0) {
            scheduleNextAuto(now);
            return;
        }
        if (now >= nextAutoAt) {
            scheduleNextAuto(now);
            if (active.isEmpty() && !Bukkit.getOnlinePlayers().isEmpty()) {
                List<EventType> types = settings.eventAutoTypes;
                EventType type = types.get(ThreadLocalRandom.current().nextInt(types.size()));
                start(type, settings.eventDefaultMultiplier, settings.eventAutoDuration * 60_000L);
            }
        }
    }

    private void scheduleNextAuto(long now) {
        nextAutoAt = now + Math.max(1, plugin.getSettings().eventAutoEvery) * 60_000L;
        save();
    }

    /** Når config genindlæses: bossbars til/fra. */
    public void reload() {
        for (ActiveEvent event : active.values()) {
            hideBar(event);
            showBar(event);
        }
    }

    public void load() {
        active.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        nextAutoAt = config.getLong("next-auto", 0);
        ConfigurationSection section = config.getConfigurationSection("active");
        if (section == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            EventType type = EventType.parse(key);
            long endsAt = section.getLong(key + ".ends", 0);
            if (type == null || endsAt <= now) {
                continue;
            }
            ActiveEvent event = new ActiveEvent(type, section.getDouble(key + ".multiplier", 2.0),
                    section.getLong(key + ".started", now), endsAt);
            active.put(type, event);
            showBar(event);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-auto", nextAutoAt);
        for (ActiveEvent event : active.values()) {
            String path = "active." + event.type.id();
            config.set(path + ".multiplier", event.multiplier);
            config.set(path + ".started", event.startedAt);
            config.set(path + ".ends", event.endsAt);
        }
        try {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IOException("kunne ikke oprette " + parent.getPath());
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke gemme events.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        save();
        for (ActiveEvent event : active.values()) {
            hideBar(event);
        }
    }
}
