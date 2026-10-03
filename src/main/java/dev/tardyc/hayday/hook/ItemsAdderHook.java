package dev.tardyc.hayday.hook;

import dev.tardyc.hayday.HayDayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Valgfri integration med ItemsAdder: teksturerede menuer, egne ikoner og font-billeder.
 * Alt kaldes via reflection, så HayDay hverken kræver ItemsAdder for at bygge eller for at køre.
 */
public final class ItemsAdderHook {

    public static final String NAMESPACE = "hayday";
    private static final String CONTENT_PREFIX = "itemsadder/hayday/";
    private static final long NEGATIVE_CACHE_MS = 30_000;

    private final HayDayPlugin plugin;
    private boolean available;

    private Constructor<?> fontImageConstructor;
    private Method fontImageExists;
    private Method replaceFontImages;
    private Constructor<?> texturedConstructor;
    private Method texturedGetInternal;
    private Method texturedShow;
    private Method customStackGet;
    private Method customStackItem;
    private Class<?> fontImageClass;

    private final Map<String, Long> missingFontImages = new HashMap<>();
    private final Map<String, Boolean> knownFontImages = new HashMap<>();

    public ItemsAdderHook(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        available = false;
        knownFontImages.clear();
        missingFontImages.clear();
        if (!plugin.getSettings().itemsAdder || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return;
        }
        try {
            fontImageClass = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            fontImageConstructor = fontImageClass.getConstructor(String.class);
            fontImageExists = fontImageClass.getMethod("exists");
            replaceFontImages = fontImageClass.getMethod("replaceFontImages", String.class);
            Class<?> textured = Class.forName("dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper");
            Class<?> fontImageArray = Array.newInstance(fontImageClass, 0).getClass();
            texturedConstructor = textured.getConstructor(InventoryHolder.class, int.class, String.class, int.class, int.class, fontImageArray);
            texturedGetInternal = textured.getMethod("getInternal");
            texturedShow = textured.getMethod("showInventory", Player.class);
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            customStackGet = customStack.getMethod("getInstance", String.class);
            customStackItem = customStack.getMethod("getItemStack");
            available = true;
            plugin.getLogger().info("ItemsAdder fundet - teksturerede menuer og ikoner er slået til.");
        } catch (Throwable t) {
            plugin.getLogger().warning("ItemsAdder fundet, men API'et kunne ikke bruges: " + t);
        }
        if (available && plugin.getSettings().itemsAdderExport) {
            exportContent();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Findes font-billedet (fx "hayday:gui_main") i ItemsAdders resourcepack? */
    public boolean hasFontImage(String id) {
        if (!available) {
            return false;
        }
        Boolean known = knownFontImages.get(id);
        if (known != null) {
            return known;
        }
        Long missingSince = missingFontImages.get(id);
        if (missingSince != null && System.currentTimeMillis() - missingSince < NEGATIVE_CACHE_MS) {
            return false;
        }
        try {
            Object wrapper = fontImageConstructor.newInstance(id);
            boolean exists = (Boolean) fontImageExists.invoke(wrapper);
            if (exists) {
                knownFontImages.put(id, true);
                missingFontImages.remove(id);
            } else {
                missingFontImages.put(id, System.currentTimeMillis());
            }
            return exists;
        } catch (Throwable t) {
            missingFontImages.put(id, System.currentTimeMillis());
            return false;
        }
    }

    /** Erstatter :namespace_id: med ItemsAdder-billeder. */
    public String replaceFontImages(String text) {
        if (!available || text == null || text.indexOf(':') < 0) {
            return text;
        }
        try {
            return (String) replaceFontImages.invoke(null, text);
        } catch (Throwable t) {
            return text;
        }
    }

    /** Opretter en ItemsAdder-menu med baggrund. Returnerer null hvis det ikke kan lade sig gøre. */
    public Object createTexturedInventory(InventoryHolder holder, int size, String title, String textureId) {
        if (!hasFontImage(textureId)) {
            return null;
        }
        try {
            Object textures = Array.newInstance(fontImageClass, 1);
            Array.set(textures, 0, fontImageConstructor.newInstance(textureId));
            return texturedConstructor.newInstance(holder, size, title,
                    plugin.getSettings().itemsAdderTitleOffset, plugin.getSettings().itemsAdderTextureOffset, textures);
        } catch (Throwable t) {
            plugin.getLogger().warning("Kunne ikke lave ItemsAdder-menu '" + textureId + "': " + t);
            return null;
        }
    }

    public Inventory getInventory(Object textured) {
        try {
            return (Inventory) texturedGetInternal.invoke(textured);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean show(Object textured, Player player) {
        try {
            texturedShow.invoke(textured, player);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Et ItemsAdder-item (fx "hayday:invisible"), eller null. */
    public ItemStack customItem(String id) {
        if (!available || id == null || id.isEmpty()) {
            return null;
        }
        try {
            Object stack = customStackGet.invoke(null, id);
            return stack == null ? null : ((ItemStack) customStackItem.invoke(stack)).clone();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Kopierer HayDays ItemsAdder-indhold (teksturer + config) til plugins/ItemsAdder/contents/hayday,
     * hvis det ikke allerede findes.
     */
    private void exportContent() {
        File target = new File(plugin.getDataFolder().getParentFile(), "ItemsAdder/contents/" + NAMESPACE);
        if (target.exists()) {
            return;
        }
        int copied = 0;
        try (JarFile jar = new JarFile(plugin.getPluginFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(CONTENT_PREFIX)) {
                    continue;
                }
                File out = new File(target, entry.getName().substring(CONTENT_PREFIX.length()));
                File parent = out.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke kopiere ItemsAdder-indhold: " + e.getMessage());
            return;
        }
        plugin.getLogger().info("Kopierede " + copied + " ItemsAdder-filer til " + target.getPath());
        if (plugin.getSettings().itemsAdderAutoZip) {
            // Byg ItemsAdders resourcepack igen, når serveren er helt startet
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("Kører /iazip så HayDays teksturer kommer med i resourcepacken...");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
            }, 200L);
        } else {
            plugin.getLogger().info("Kør /iazip så menuerne får Hay Day-tekstur!");
        }
    }
}
