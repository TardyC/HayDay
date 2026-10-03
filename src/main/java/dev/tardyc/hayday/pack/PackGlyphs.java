package dev.tardyc.hayday.pack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Tegnene i resourcepackens font (menu-baggrunde, ikoner og mellemrum). Indlæses fra pack-glyphs.yml,
 * som laves af tools/generate_pack.py sammen med selve teksturerne.
 */
public final class PackGlyphs {

    private final Map<String, String> guis = new HashMap<>();
    private final Map<String, String> icons = new HashMap<>();
    private final TreeMap<Integer, String> negative = new TreeMap<>();
    private final TreeMap<Integer, String> positive = new TreeMap<>();
    private final Set<String> items = new HashSet<>();
    private int guiAdvance = 193;

    public void load(InputStream stream, Logger log) {
        guis.clear();
        icons.clear();
        negative.clear();
        positive.clear();
        items.clear();
        if (stream == null) {
            log.warning("pack-glyphs.yml mangler i jar'en - resourcepack-menuer er slået fra.");
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        guiAdvance = config.getInt("gui-advance", 193);
        readChars(config.getConfigurationSection("gui"), guis);
        readChars(config.getConfigurationSection("icons"), icons);
        readSpaces(config.getConfigurationSection("negative"), negative);
        readSpaces(config.getConfigurationSection("positive"), positive);
        items.addAll(config.getStringList("items"));
    }

    private static String toChar(String hex) {
        return new String(Character.toChars(Integer.parseInt(hex, 16)));
    }

    private static void readChars(ConfigurationSection section, Map<String, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String hex = section.getString(key);
            if (hex != null) {
                target.put(key, toChar(hex));
            }
        }
    }

    private static void readSpaces(ConfigurationSection section, TreeMap<Integer, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String hex = section.getString(key);
            if (hex != null) {
                target.put(Integer.parseInt(key), toChar(hex));
            }
        }
    }

    public boolean hasGui(String name) {
        return guis.containsKey(name);
    }

    public String icon(String name) {
        return icons.get(name);
    }

    public boolean hasItemTexture(String itemId) {
        return items.contains(itemId);
    }

    /** Et vandret mellemrum på {@code pixels} (negativt flytter tilbage). */
    public String space(int pixels) {
        TreeMap<Integer, String> chars = pixels < 0 ? negative : positive;
        int remaining = Math.abs(pixels);
        StringBuilder out = new StringBuilder();
        for (Integer size : chars.descendingKeySet()) {
            while (remaining >= size) {
                out.append(chars.get(size));
                remaining -= size;
            }
        }
        return out.toString();
    }

    /**
     * Menu-titel med baggrund: baggrunden tegnes 16 px til venstre for titlen, og bagefter rykkes
     * tilbage så titelteksten står på sin normale plads.
     */
    public String guiTitle(String gui, String coloredTitle) {
        String glyph = guis.get(gui);
        if (glyph == null) {
            return coloredTitle;
        }
        return "§f" + space(-16) + glyph + space(-(guiAdvance - 16)) + coloredTitle;
    }
}
