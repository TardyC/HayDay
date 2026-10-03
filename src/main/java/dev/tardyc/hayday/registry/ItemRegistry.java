package dev.tardyc.hayday.registry;

import dev.tardyc.hayday.model.CropType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Alle varer fra items.yml.
 */
public final class ItemRegistry {

    private final Map<String, FarmItem> items = new LinkedHashMap<>();
    /** Materialer som afgrøder kan vises med - bruges til hurtigt at filtrere blok-events. */
    private final Set<Material> cropMaterials = EnumSet.noneOf(Material.class);

    public void load(YamlConfiguration config, Logger log) {
        items.clear();
        cropMaterials.clear();
        ConfigurationSection root = config.getConfigurationSection("items");
        if (root == null) {
            log.warning("items.yml indeholder ingen varer!");
            return;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            Material icon = Material.matchMaterial(section.getString("icon", "STONE"));
            if (icon == null || !icon.isItem()) {
                log.warning("Vare '" + id + "' har et ugyldigt ikon: " + section.getString("icon") + " - bruger STONE.");
                icon = Material.STONE;
            }
            ItemCategory category;
            try {
                category = ItemCategory.valueOf(section.getString("category", "PRODUCT").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warning("Vare '" + id + "' har en ugyldig kategori - bruger PRODUCT.");
                category = ItemCategory.PRODUCT;
            }
            CropType crop = null;
            ConfigurationSection cropSection = section.getConfigurationSection("crop");
            if (cropSection != null) {
                crop = loadCrop(id, cropSection, log);
            }
            if (category == ItemCategory.CROP && crop == null) {
                log.warning("Afgrøden '" + id + "' mangler en gyldig 'crop'-sektion og kan ikke plantes.");
            }
            FarmItem item = new FarmItem(id,
                    section.getString("name", id),
                    icon,
                    parseColor(section.getString("color")),
                    category,
                    Math.max(0, section.getDouble("sell-price", 1)),
                    Math.max(0, section.getInt("xp", 1)),
                    crop);
            item.setItemsAdderId(section.getString("itemsadder"));
            item.setModel(section.getString("model"));
            items.put(id, item);
        }
    }

    private CropType loadCrop(String id, ConfigurationSection section, Logger log) {
        List<BlockData> stages = new ArrayList<>();
        for (String stage : section.getStringList("stages")) {
            try {
                BlockData data = Bukkit.createBlockData(stage);
                stages.add(data);
                cropMaterials.add(data.getMaterial());
            } catch (IllegalArgumentException e) {
                log.warning("Afgrøden '" + id + "' har et ugyldigt stadie: " + stage);
            }
        }
        if (stages.isEmpty()) {
            return null;
        }
        return new CropType(id,
                Math.max(1, section.getInt("level", 1)),
                Math.max(1, section.getInt("grow-time", 60)),
                Math.max(0, section.getDouble("seed-price", 0)),
                Math.max(1, section.getInt("harvest-amount", 2)),
                Math.max(0, section.getInt("harvest-xp", 1)),
                Collections.unmodifiableList(stages));
    }

    private static Color parseColor(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(input.replace("#", ""), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public FarmItem get(String id) {
        return id == null ? null : items.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<FarmItem> all() {
        return items.values();
    }

    public List<FarmItem> byCategory(ItemCategory category) {
        List<FarmItem> out = new ArrayList<>();
        for (FarmItem item : items.values()) {
            if (item.getCategory() == category) {
                out.add(item);
            }
        }
        return out;
    }

    public List<FarmItem> crops() {
        List<FarmItem> out = new ArrayList<>();
        for (FarmItem item : items.values()) {
            if (item.isCrop()) {
                out.add(item);
            }
        }
        return out;
    }

    public boolean isCropMaterial(Material material) {
        return cropMaterials.contains(material);
    }

    public int size() {
        return items.size();
    }
}
