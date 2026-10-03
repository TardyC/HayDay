package dev.tardyc.hayday.hook;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.pack.ResourcePackManager;
import dev.tardyc.hayday.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Erstatter {icon_navn} i tekster med et billede fra resourcepacken (HayDays egen eller ItemsAdder),
 * eller en almindelig tekst-fallback for spillere der ikke har pakken.
 */
public final class IconService {

    private static final long CACHE_MS = 30_000;

    private final HayDayPlugin plugin;
    private final Map<String, String> textured = new HashMap<>();
    private long resolvedAt;

    public IconService(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearCache() {
        textured.clear();
        resolvedAt = 0;
    }

    /** Tekst som alle kan se (hologrammer): billeder kun hvis alle spillere har resourcepacken. */
    public String apply(String text) {
        return apply(text, plugin.getPack() != null && plugin.getPack().globalTextures());
    }

    /** Tekst til én modtager (chat-beskeder). */
    public String apply(String text, CommandSender receiver) {
        boolean images = receiver instanceof Player && plugin.getPack() != null && plugin.getPack().hasPack((Player) receiver);
        return apply(text, images);
    }

    private String apply(String text, boolean images) {
        if (text == null || plugin.getSettings().icons == null || !text.contains("{icon_")) {
            return text;
        }
        long now = System.currentTimeMillis();
        if (now - resolvedAt > CACHE_MS) {
            textured.clear();
            resolvedAt = now;
        }
        String result = text;
        for (Map.Entry<String, String[]> entry : plugin.getSettings().icons.entrySet()) {
            String placeholder = "{icon_" + entry.getKey() + "}";
            if (!result.contains(placeholder)) {
                continue;
            }
            String image = images ? image(entry.getKey(), entry.getValue()) : null;
            result = result.replace(placeholder, image != null ? image : Text.color(entry.getValue()[1]));
        }
        return result;
    }

    /** Billedet for et ikon, eller null hvis det ikke findes i den aktive resourcepack. */
    private String image(String key, String[] variants) {
        if (textured.containsKey(key)) {
            return textured.get(key);
        }
        String value = null;
        ResourcePackManager pack = plugin.getPack();
        if (pack.getMode() == ResourcePackManager.Mode.OWN) {
            String glyph = pack.getGlyphs().icon(key);
            if (glyph != null) {
                // §f nulstiller farven, så billedet ikke bliver farvet af teksten foran
                value = "§f" + glyph;
            }
        } else if (pack.getMode() == ResourcePackManager.Mode.ITEMSADDER) {
            ItemsAdderHook hook = plugin.getItemsAdder();
            String token = variants[0];
            if (token != null && token.startsWith(":") && token.endsWith(":") && token.length() > 2) {
                String id = token.substring(1, token.length() - 1).replaceFirst("_", ":");
                if (hook.hasFontImage(id)) {
                    value = "§f" + hook.replaceFontImages(token);
                }
            }
        }
        textured.put(key, value);
        return value;
    }
}
