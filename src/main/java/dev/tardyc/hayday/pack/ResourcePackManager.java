package dev.tardyc.hayday.pack;

import com.sun.net.httpserver.HttpServer;
import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * HayDays egen resourcepack: bygges ud fra jar'en, kan hostes af pluginet selv og sendes til spillerne.
 * Er ItemsAdder installeret, bruges ItemsAdders resourcepack i stedet (indholdet kopieres derover).
 */
public final class ResourcePackManager {

    /** Hvilken resourcepack der bruges. */
    public enum Mode {
        /** HayDays egen resourcepack. */
        OWN,
        /** Indholdet ligger i ItemsAdders resourcepack. */
        ITEMSADDER,
        /** Ingen resourcepack - almindelige Minecraft-menuer og -items. */
        NONE
    }

    private static final String PREFIX = "resourcepack/";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("hayday-resourcepack".getBytes(StandardCharsets.UTF_8));

    private final HayDayPlugin plugin;
    private final PackGlyphs glyphs = new PackGlyphs();
    private final Set<UUID> loaded = new HashSet<>();
    private Mode mode = Mode.NONE;
    private byte[] zip;
    private byte[] hash;
    private String hashHex = "";
    private HttpServer server;
    private ExecutorService executor;

    public ResourcePackManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        shutdown();
        glyphs.load(plugin.getResource("pack-glyphs.yml"), plugin.getLogger());
        Settings settings = plugin.getSettings();
        boolean itemsAdder = plugin.getItemsAdder().isAvailable();
        switch (settings.packMode) {
            case "none":
                mode = Mode.NONE;
                break;
            case "itemsadder":
                mode = itemsAdder ? Mode.ITEMSADDER : Mode.NONE;
                break;
            case "own":
                mode = Mode.OWN;
                break;
            default:
                mode = itemsAdder ? Mode.ITEMSADDER : Mode.OWN;
                break;
        }
        if (mode == Mode.OWN) {
            try {
                build();
                startServer();
                plugin.getLogger().info("Resourcepack klar (" + (zip.length / 1024) + " KB) - " + url());
            } catch (IOException | NoSuchAlgorithmException e) {
                plugin.getLogger().warning("Kunne ikke bygge resourcepacken: " + e.getMessage());
                mode = Mode.NONE;
            }
        }
        plugin.getLogger().info("Resourcepack-tilstand: " + mode);
    }

    public Mode getMode() {
        return mode;
    }

    public PackGlyphs getGlyphs() {
        return glyphs;
    }

    // ------------------------------------------------------------------
    // Byg og host
    // ------------------------------------------------------------------

    private void build() throws IOException, NoSuchAlgorithmException {
        List<JarEntry> entries = new ArrayList<>();
        try (JarFile jar = new JarFile(plugin.getPluginFile())) {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(PREFIX)) {
                    entries.add(entry);
                }
            }
            entries.sort((a, b) -> a.getName().compareTo(b.getName()));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                for (JarEntry entry : entries) {
                    ZipEntry zipEntry = new ZipEntry(entry.getName().substring(PREFIX.length()));
                    // Fast tidsstempel: samme indhold giver samme hash, så klienten kan genbruge sin cache
                    zipEntry.setTime(315532800000L);
                    out.putNextEntry(zipEntry);
                    try (InputStream in = jar.getInputStream(entry)) {
                        in.transferTo(out);
                    }
                    out.closeEntry();
                }
            }
            zip = bytes.toByteArray();
        }
        hash = MessageDigest.getInstance("SHA-1").digest(zip);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        hashHex = hex.toString();
        File file = new File(plugin.getDataFolder(), "HayDay-resourcepack.zip");
        Files.write(file.toPath(), zip);
    }

    private void startServer() {
        Settings settings = plugin.getSettings();
        if (!settings.packHostEnabled || !settings.packUrl.isEmpty()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(settings.packHostPort), 0);
            server.createContext("/", exchange -> {
                try {
                    String method = exchange.getRequestMethod();
                    if (!method.equals("GET") && !method.equals("HEAD")) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    byte[] data = zip;
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    if (method.equals("HEAD")) {
                        exchange.sendResponseHeaders(200, -1);
                        return;
                    }
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream body = exchange.getResponseBody()) {
                        body.write(data);
                    }
                } finally {
                    exchange.close();
                }
            });
            executor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "HayDay-resourcepack");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
        } catch (IOException | LinkageError e) {
            plugin.getLogger().warning("Kunne ikke starte resourcepack-webserveren på port " + settings.packHostPort
                    + ": " + e.getMessage() + " - sæt resource-pack.url til din egen download-adresse.");
            server = null;
        }
    }

    public void shutdown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public String url() {
        Settings settings = plugin.getSettings();
        if (!settings.packUrl.isEmpty()) {
            return settings.packUrl;
        }
        String address = settings.packHostAddress;
        if (address.isEmpty()) {
            address = Bukkit.getIp();
        }
        if (address == null || address.isEmpty()) {
            address = "127.0.0.1";
        }
        return "http://" + address + ":" + settings.packHostPort + "/HayDay-" + hashHex.substring(0, Math.min(10, hashHex.length())) + ".zip";
    }

    // ------------------------------------------------------------------
    // Spillere
    // ------------------------------------------------------------------

    public void send(Player player) {
        if (mode != Mode.OWN || zip == null || !plugin.getSettings().packSendOnJoin) {
            return;
        }
        Settings settings = plugin.getSettings();
        player.addResourcePack(PACK_ID, url(), hash, settings.packPrompt, settings.packRequired);
    }

    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                loaded.add(uuid);
                break;
            case ACCEPTED:
            case DOWNLOADED:
                break;
            default:
                loaded.remove(uuid);
                break;
        }
    }

    public void forget(Player player) {
        loaded.remove(player.getUniqueId());
    }

    /** Kan spilleren se HayDays teksturer (menu-baggrunde og egne items)? */
    public boolean hasPack(Player player) {
        switch (mode) {
            case ITEMSADDER:
                return true;
            case OWN:
                return player != null && loaded.contains(player.getUniqueId());
            default:
                return false;
        }
    }

    /**
     * Må ting som alle kan se (hologrammer, svævende ikoner) bruge teksturerne? Kun hvis alle spillere
     * med sikkerhed har pakken (ItemsAdder, eller HayDays pakke er påkrævet).
     */
    public boolean globalTextures() {
        return mode == Mode.ITEMSADDER || (mode == Mode.OWN && plugin.getSettings().packRequired);
    }

    public Set<UUID> getLoaded() {
        return Collections.unmodifiableSet(loaded);
    }
}
