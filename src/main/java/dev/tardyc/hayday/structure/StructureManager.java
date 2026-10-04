package dev.tardyc.hayday.structure;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.BuildingType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Indlæser structures.yml: bygningernes og gårdens blueprints.
 * Kommer der en nyere version med pluginet, gemmes den gamle fil som structures.yml.old.
 */
public final class StructureManager {

    private final HayDayPlugin plugin;
    private final Map<String, Structure> structures = new HashMap<>();

    public StructureManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        structures.clear();
        File file = new File(plugin.getDataFolder(), "structures.yml");
        YamlConfiguration bundled = null;
        InputStream stream = plugin.getResource("structures.yml");
        if (stream != null) {
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        if (!file.exists()) {
            plugin.saveResource("structures.yml", false);
        } else if (bundled != null) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            if (current.getInt("version", 0) < bundled.getInt("version", 0)) {
                File old = new File(plugin.getDataFolder(), "structures.yml.old");
                if (old.exists() && !old.delete()) {
                    plugin.getLogger().warning("Kunne ikke slette " + old.getName());
                }
                if (file.renameTo(old)) {
                    plugin.saveResource("structures.yml", true);
                    plugin.getLogger().info("structures.yml er opdateret - den gamle ligger i structures.yml.old.");
                }
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("structures");
        if (root == null && bundled != null) {
            plugin.getLogger().warning("structures.yml er tom eller ugyldig - bruger standard-bygningerne.");
            root = bundled.getConfigurationSection("structures");
        }
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                structures.put(id.toLowerCase(Locale.ROOT), Structure.parse(id.toLowerCase(Locale.ROOT), section));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Strukturen '" + id + "' i structures.yml er ugyldig: " + e.getMessage());
            }
        }
        plugin.getLogger().info("Indlæste " + structures.size() + " bygninger/strukturer fra structures.yml.");
    }

    public Structure get(String id) {
        return id == null ? null : structures.get(id.toLowerCase(Locale.ROOT));
    }

    /** Strukturen en bygningstype bygges som (null = kun én blok som før). */
    public Structure forBuilding(BuildingType type) {
        if (!plugin.getSettings().buildingStructures) {
            return null;
        }
        return get(type.getStructureId());
    }

    public int size() {
        return structures.size();
    }
}
