package dev.tardyc.hayday.hook;

import dev.tardyc.hayday.HayDayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valgfri integration med ItemsAdder: teksturerede menuer, egne ikoner og font-billeder.
 * Alt kaldes via reflection, så HayDay hverken kræver ItemsAdder for at bygge eller for at køre.
 */
public final class ItemsAdderHook {

    public static final String NAMESPACE = "hayday";
    private static final String CONFIG_PREFIX = "itemsadder/configs/";
    private static final String ASSET_PREFIX = "resourcepack/assets/hayday/";
    private static final long NEGATIVE_CACHE_MS = 30_000;

    private final HayDayPlugin plugin;
    private boolean available;
    /** Hvorfor ItemsAdder ikke kan bruges (vises i /hayday admin pakke), eller null. */
    private String problem;

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
        problem = null;
        knownFontImages.clear();
        missingFontImages.clear();
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return;
        }
        if (!plugin.getSettings().itemsAdder) {
            problem = "itemsadder.enabled er false i HayDays config.yml";
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
            problem = "ItemsAdders API kunne ikke bruges (" + t.getClass().getSimpleName() + ") - se konsollen";
            plugin.getLogger().warning("ItemsAdder fundet, men API'et kunne ikke bruges: " + t);
        }
        if (available && plugin.getSettings().itemsAdderExport) {
            exportContent();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Hvorfor ItemsAdder er installeret men ikke bruges, ellers null. */
    public String getProblem() {
        return problem;
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
     * Kopierer HayDays indhold til plugins/ItemsAdder/contents/hayday: ItemsAdder-config'en og de rå
     * resourcepack-filer (teksturer og item-modeller). Gøres igen når HayDay opdateres.
     */
    private void exportContent() {
        File target = new File(plugin.getDataFolder().getParentFile(), "ItemsAdder/contents/" + NAMESPACE);
        File marker = new File(target, ".hayday-version");
        String version = plugin.getDescription().getVersion();
        try {
            if (marker.exists() && version.equals(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim())) {
                return;
            }
        } catch (IOException ignored) {
            // kopiér igen
        }
        int copied = 0;
        try (JarFile jar = new JarFile(plugin.getPluginFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                String relative = null;
                if (name.startsWith(CONFIG_PREFIX)) {
                    relative = "configs/" + name.substring(CONFIG_PREFIX.length());
                } else if (name.startsWith(ASSET_PREFIX)) {
                    relative = "resourcepack/assets/" + NAMESPACE + "/" + name.substring(ASSET_PREFIX.length());
                }
                if (entry.isDirectory() || relative == null) {
                    continue;
                }
                File out = new File(target, relative);
                File parent = out.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            Files.write(marker.toPath(), version.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke kopiere ItemsAdder-indhold: " + e.getMessage());
            return;
        }
        plugin.getLogger().info("Kopierede " + copied + " filer til " + target.getPath());
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

    // ------------------------------------------------------------------
    // Fejlfinding af ItemsAdders resourcepack (/hayday admin pakke)
    // ------------------------------------------------------------------

    private static final Pattern SHA1 = Pattern.compile("[0-9a-fA-F]{40}");

    /**
     * Tjekker ItemsAdders hosting: er adressen 'auto' (virker ikke i containere), og passer linket til
     * en ekstern host (fx mc-packs.net, hvor filnavnet er pakkens SHA-1) med den pakke serveren har nu?
     */
    public List<String> packDiagnostics() {
        List<String> lines = new ArrayList<>();
        File folder = new File(plugin.getDataFolder().getParentFile(), "ItemsAdder");
        File configFile = new File(folder, "config.yml");
        File zip = new File(folder, "output/generated.zip");
        if (!configFile.exists()) {
            lines.add("&cFandt ikke plugins/ItemsAdder/config.yml.");
            return lines;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String sha = null;
        if (zip.exists()) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] hash = digest.digest(Files.readAllBytes(zip.toPath()));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                sha = hex.toString();
            } catch (IOException | NoSuchAlgorithmException e) {
                lines.add("&cKunne ikke læse generated.zip: " + e.getMessage());
            }
            lines.add("&7Pakken på serveren: &foutput/generated.zip &8(" + (zip.length() / 1024) + " KB, SHA-1 "
                    + (sha == null ? "?" : sha.substring(0, 12)) + "...)");
        } else {
            lines.add("&cItemsAdder har ikke lavet en pakke endnu - kør &f/iazip&c.");
        }
        String base = "resource-pack.hosting.";
        if (config.getBoolean(base + "external-host.enabled", false)) {
            String url = config.getString(base + "external-host.url", "");
            lines.add("&7Hosting: &fexternal-host &8(" + url + ")");
            Matcher matcher = SHA1.matcher(url);
            if (url.isEmpty()) {
                lines.add("&cDer står intet link under external-host.url.");
            } else if (sha != null && matcher.find()) {
                String linked = matcher.group().toLowerCase(Locale.ROOT);
                if (linked.equals(sha)) {
                    lines.add("&a✔ Linket passer med pakken på serveren.");
                } else {
                    lines.add("&c✘ Linket peger på en ANDEN pakke (" + linked.substring(0, 12) + "...) end den serveren har nu!");
                    lines.add("&e→ Upload plugins/ItemsAdder/output/generated.zip igen, sæt det nye link ind og kør /iareload.");
                    lines.add("&e→ Kør IKKE /iazip bagefter - så laves en ny pakke og linket passer ikke igen.");
                }
            } else if (sha != null) {
                lines.add("&7Kan ikke se på linket om det passer (det indeholder ikke pakkens SHA-1).");
            }
        } else if (config.getBoolean(base + "simple_self_host.enabled", false)) {
            String address = config.getString(base + "simple_self_host.server_address", "auto");
            if ("auto".equalsIgnoreCase(address)) {
                address = config.getString("server.address", "auto");
            }
            lines.add("&7Hosting: &fsimple_self_host &8(adresse: " + address + ", port: "
                    + config.getString("server.port", "auto") + ")");
            if ("auto".equalsIgnoreCase(address)) {
                lines.add("&c✘ Adressen er 'auto'. På hosts med containere bliver den 127.x.x.x, og så kan ingen hente pakken.");
                lines.add("&e→ Skriv serverens rigtige IP/domæne under server: address: i ItemsAdders config.yml.");
            } else if (address.startsWith("127.") || address.equalsIgnoreCase("localhost")) {
                lines.add("&c✘ " + address + " er serverens egen interne adresse - spillerne kan ikke nå den.");
            } else {
                lines.add("&a✔ Adressen ser rigtig ud.");
            }
        } else if (config.getBoolean(base + "self-host.enabled", false)) {
            lines.add("&7Hosting: &fself-host &8(" + config.getString(base + "self-host.server-ip", "?") + ":"
                    + config.getString(base + "self-host.pack-port", "?") + ")");
        } else {
            lines.add("&cIngen hosting er slået til i ItemsAdders config.yml.");
        }
        lines.add("&8Spillernes egen fejl står i .minecraft/logs/latest.log (søg efter \"resource pack\" eller \"hash\").");
        return lines;
    }
}
