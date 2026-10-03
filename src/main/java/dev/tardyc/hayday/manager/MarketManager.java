package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vejboder og avisen: spillere sælger varer til hinanden.
 */
public final class MarketManager {

    private final HayDayPlugin plugin;
    private final File file;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private boolean dirty;

    public MarketManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(new File(plugin.getDataFolder(), "data"), "market.yml");
    }

    // ------------------------------------------------------------------
    // Gemning
    // ------------------------------------------------------------------

    public void load() {
        listings.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("listings");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Listing.State state = Listing.State.valueOf(section.getString("state", "ACTIVE"));
                Listing listing = new Listing(UUID.fromString(key), UUID.fromString(section.getString("seller", "")),
                        section.getString("seller-name", "?"), section.getInt("slot"), section.getString("item", ""),
                        section.getInt("amount", 1), section.getDouble("price"), section.getLong("created"),
                        state, section.getString("buyer"));
                listings.put(listing.getId(), listing);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ugyldig vare i market.yml: " + key);
            }
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void save(boolean sync) {
        if (!dirty && !sync) {
            return;
        }
        dirty = false;
        YamlConfiguration config = new YamlConfiguration();
        for (Listing listing : listings.values()) {
            String path = "listings." + listing.getId();
            config.set(path + ".seller", listing.getSeller().toString());
            config.set(path + ".seller-name", listing.getSellerName());
            config.set(path + ".slot", listing.getSlot());
            config.set(path + ".item", listing.getItemId());
            config.set(path + ".amount", listing.getAmount());
            config.set(path + ".price", listing.getPrice());
            config.set(path + ".created", listing.getCreatedAt());
            config.set(path + ".state", listing.getState().name());
            config.set(path + ".buyer", listing.getBuyerName());
        }
        String data = config.saveToString();
        Runnable write = () -> {
            synchronized (this) {
                try {
                    File parent = file.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        return;
                    }
                    File temp = new File(parent, file.getName() + ".tmp");
                    Files.write(temp.toPath(), data.getBytes(StandardCharsets.UTF_8));
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().warning("Kunne ikke gemme market.yml: " + e.getMessage());
                }
            }
        };
        if (sync) {
            write.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        }
    }

    // ------------------------------------------------------------------
    // Opslag
    // ------------------------------------------------------------------

    public List<Listing> getListings(UUID seller) {
        List<Listing> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.getSeller().equals(seller)) {
                list.add(listing);
            }
        }
        list.sort(Comparator.comparingInt(Listing::getSlot));
        return list;
    }

    public Listing getAt(UUID seller, int slot) {
        for (Listing listing : listings.values()) {
            if (listing.getSeller().equals(seller) && listing.getSlot() == slot) {
                return listing;
            }
        }
        return null;
    }

    /** Alle aktive varer fra andre spillere, nyeste først (til avisen). */
    public List<Listing> getNewspaper(UUID viewer) {
        List<Listing> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.isActive() && !listing.getSeller().equals(viewer) && plugin.getItems().get(listing.getItemId()) != null) {
                list.add(listing);
            }
        }
        list.sort(Comparator.<Listing>comparingLong(Listing::getCreatedAt).reversed());
        return list;
    }

    public int count(UUID seller, Listing.State state) {
        int count = 0;
        for (Listing listing : listings.values()) {
            if (listing.getSeller().equals(seller) && listing.getState() == state) {
                count++;
            }
        }
        return count;
    }

    public int slots(PlayerData data) {
        Settings settings = plugin.getSettings();
        return Math.min(settings.marketMaxSlots, settings.marketBaseSlots + data.getRoadsideSlots());
    }

    public double slotPrice(PlayerData data) {
        return plugin.getSettings().marketSlotPrice * (data.getRoadsideSlots() + 1);
    }

    public double basePrice(FarmItem item, int amount) {
        return Math.max(1, item.getSellPrice() * amount);
    }

    public double minPrice(FarmItem item, int amount) {
        return Math.max(1, Math.floor(basePrice(item, amount) * plugin.getSettings().marketMinMultiplier));
    }

    public double maxPrice(FarmItem item, int amount) {
        return Math.max(minPrice(item, amount), Math.ceil(basePrice(item, amount) * plugin.getSettings().marketMaxMultiplier));
    }

    public double defaultPrice(FarmItem item, int amount) {
        double price = Math.round(basePrice(item, amount) * plugin.getSettings().marketDefaultMultiplier);
        return Math.max(minPrice(item, amount), Math.min(maxPrice(item, amount), price));
    }

    // ------------------------------------------------------------------
    // Handlinger
    // ------------------------------------------------------------------

    public boolean create(Player player, int slot, FarmItem item, int amount, double price) {
        PlayerData data = plugin.getPlayers().get(player);
        if (slot < 0 || slot >= slots(data) || getAt(player.getUniqueId(), slot) != null) {
            return false;
        }
        if (amount <= 0 || data.getAmount(item.getId()) < amount) {
            plugin.getMessages().send(player, "storage.not-enough", "item", item.getName());
            Sounds.play(player, Sounds.ERROR);
            return false;
        }
        double clamped = Math.max(minPrice(item, amount), Math.min(maxPrice(item, amount), price));
        data.removeItem(item.getId(), amount);
        Listing listing = new Listing(UUID.randomUUID(), player.getUniqueId(), player.getName(), slot, item.getId(),
                amount, clamped, System.currentTimeMillis(), Listing.State.ACTIVE, null);
        listings.put(listing.getId(), listing);
        dirty = true;
        plugin.getMessages().send(player, "market.listed", "amount", amount, "item", item.getName(),
                "price", plugin.getEconomy().format(clamped));
        Sounds.play(player, Sounds.PLACE, 1.3f);
        return true;
    }

    public boolean buy(Player buyer, Listing listing) {
        FarmItem item = plugin.getItems().get(listing.getItemId());
        if (item == null || !listing.isActive() || !listings.containsKey(listing.getId())) {
            plugin.getMessages().send(buyer, "market.gone");
            Sounds.play(buyer, Sounds.ERROR);
            return false;
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            plugin.getMessages().send(buyer, "market.own");
            return false;
        }
        PlayerData data = plugin.getPlayers().get(buyer);
        if (!plugin.getStorage().hasSpace(data, item, listing.getAmount())) {
            plugin.getMessages().send(buyer, item.isCrop() ? "field.silo-full" : "building.barn-full");
            Sounds.play(buyer, Sounds.ERROR);
            return false;
        }
        if (!plugin.getEconomy().charge(buyer, listing.getPrice())) {
            Sounds.play(buyer, Sounds.ERROR);
            return false;
        }
        data.addItem(item.getId(), listing.getAmount());
        listing.setState(Listing.State.SOLD);
        listing.setBuyerName(buyer.getName());
        dirty = true;
        plugin.getMessages().send(buyer, "market.bought", "amount", listing.getAmount(), "item", item.getName(),
                "seller", listing.getSellerName(), "price", plugin.getEconomy().format(listing.getPrice()));
        Sounds.play(buyer, Sounds.COINS);
        plugin.getAnimations().floatingText(buyer.getLocation().add(0, 2.2, 0), "&a+" + listing.getAmount() + " " + item.getName());
        Player seller = Bukkit.getPlayer(listing.getSeller());
        if (seller != null) {
            plugin.getMessages().send(seller, "market.sold-notify", "buyer", buyer.getName(), "amount", listing.getAmount(),
                    "item", item.getName(), "price", plugin.getEconomy().format(listing.getPrice()));
            Sounds.play(seller, Sounds.COINS, 1.4f);
        }
        return true;
    }

    /** Sælgeren henter penge for en solgt vare, eller varerne tilbage fra en udløbet. */
    public void collect(Player player, Listing listing) {
        if (!listing.getSeller().equals(player.getUniqueId()) || !listings.containsKey(listing.getId())) {
            return;
        }
        if (listing.getState() == Listing.State.SOLD) {
            listings.remove(listing.getId());
            dirty = true;
            plugin.getEconomy().deposit(player, listing.getPrice());
            plugin.getMessages().send(player, "market.collected", "price", plugin.getEconomy().format(listing.getPrice()));
            plugin.getAnimations().coinBurst(player);
            return;
        }
        returnItems(player, listing);
    }

    /** Fjerner en aktiv (eller udløbet) vare og lægger varerne tilbage på lageret. */
    public void returnItems(Player player, Listing listing) {
        if (!listing.getSeller().equals(player.getUniqueId()) || listing.getState() == Listing.State.SOLD) {
            return;
        }
        FarmItem item = plugin.getItems().get(listing.getItemId());
        PlayerData data = plugin.getPlayers().get(player);
        if (item != null) {
            if (!plugin.getStorage().hasSpace(data, item, listing.getAmount())) {
                plugin.getMessages().send(player, item.isCrop() ? "field.silo-full" : "building.barn-full");
                Sounds.play(player, Sounds.ERROR);
                return;
            }
            data.addItem(item.getId(), listing.getAmount());
        }
        listings.remove(listing.getId());
        dirty = true;
        plugin.getMessages().send(player, "market.cancelled");
        Sounds.play(player, Sounds.COLLECT);
    }

    public void buySlot(Player player) {
        PlayerData data = plugin.getPlayers().get(player);
        if (slots(data) >= plugin.getSettings().marketMaxSlots) {
            plugin.getMessages().send(player, "market.slot-max");
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (!plugin.getEconomy().charge(player, slotPrice(data))) {
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        data.setRoadsideSlots(data.getRoadsideSlots() + 1);
        plugin.getMessages().send(player, "market.slot-bought", "slots", slots(data));
        Sounds.play(player, Sounds.LEVEL_UP, 1.5f);
    }

    /** Markerer gamle varer som udløbne. Kaldes jævnligt. */
    public void tick() {
        long hours = plugin.getSettings().marketExpireHours;
        if (hours <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - hours * 3_600_000L;
        for (Listing listing : listings.values()) {
            if (listing.isActive() && listing.getCreatedAt() < cutoff) {
                listing.setState(Listing.State.EXPIRED);
                dirty = true;
            }
        }
    }

    /** Admin reset: fjerner alle en spillers varer i vejboden (varerne går tabt). */
    public void removeAll(UUID seller) {
        if (listings.values().removeIf(listing -> listing.getSeller().equals(seller))) {
            dirty = true;
        }
    }

    public void updateSellerName(Player player) {
        for (Listing listing : listings.values()) {
            if (listing.getSeller().equals(player.getUniqueId()) && !player.getName().equals(listing.getSellerName())) {
                listing.setSellerName(player.getName());
                dirty = true;
            }
        }
    }
}
