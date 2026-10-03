package dev.tardyc.hayday.hook;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.util.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Erstatter {icon_navn} i tekster med et ItemsAdder-billede eller en almindelig tekst-fallback.
 */
public final class IconService {

    private static final long CACHE_MS = 30_000;

    private final HayDayPlugin plugin;
    private final Map<String, String> resolved = new HashMap<>();
    private long resolvedAt;

    public IconService(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearCache() {
        resolved.clear();
        resolvedAt = 0;
    }

    public String apply(String text) {
        if (text == null || plugin.getSettings().icons == null || !text.contains("{icon_")) {
            return text;
        }
        long now = System.currentTimeMillis();
        if (now - resolvedAt > CACHE_MS) {
            resolved.clear();
            resolvedAt = now;
        }
        String result = text;
        for (Map.Entry<String, String[]> entry : plugin.getSettings().icons.entrySet()) {
            String placeholder = "{icon_" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, resolve(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    private String resolve(String key, String[] variants) {
        String cached = resolved.get(key);
        if (cached != null) {
            return cached;
        }
        String value = Text.color(variants[1]);
        ItemsAdderHook hook = plugin.getItemsAdder();
        String token = variants[0];
        if (hook.isAvailable() && token != null && token.startsWith(":") && token.endsWith(":") && token.length() > 2) {
            String id = token.substring(1, token.length() - 1).replaceFirst("_", ":");
            if (hook.hasFontImage(id)) {
                // §f nulstiller farven, så billedet ikke bliver farvet af teksten foran
                value = "§f" + hook.replaceFontImages(token);
            }
        }
        resolved.put(key, value);
        return value;
    }
}
