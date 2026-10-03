package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.Order;
import dev.tardyc.hayday.model.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indlæser og gemmer spillerdata i plugins/HayDay/players/<uuid>.yml.
 */
public final class PlayerManager {

    private final HayDayPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerData> loaded = new HashMap<>();

    public PlayerManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Kunne ikke oprette mappen " + folder.getPath());
        }
    }

    public File getFolder() {
        return folder;
    }

    /** Henter data for en online spiller (indlæses hvis nødvendigt). */
    public PlayerData get(Player player) {
        PlayerData data = loaded.get(player.getUniqueId());
        if (data == null) {
            data = load(player.getUniqueId(), player.getName());
            loaded.put(player.getUniqueId(), data);
        }
        data.setName(player.getName());
        return data;
    }

    public PlayerData getIfLoaded(UUID uuid) {
        return loaded.get(uuid);
    }

    public Collection<PlayerData> getLoaded() {
        return loaded.values();
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    private PlayerData load(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        File file = file(uuid);
        if (!file.exists()) {
            data.setCoins(plugin.getSettings().startCoins);
            data.setDirty(true);
            return data;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.setLevel(config.getInt("level", 1));
        data.setXp(config.getLong("xp", 0));
        data.setCoins(config.getDouble("coins", 0));
        data.setSiloUpgrades(config.getInt("silo-upgrades", 0));
        data.setBarnUpgrades(config.getInt("barn-upgrades", 0));
        data.setFieldsBought(config.getInt("fields-bought", 0));
        data.setRoadsideSlots(config.getInt("roadside-slots", 0));
        data.setStarted(config.getBoolean("started", false));
        ConfigurationSection storage = config.getConfigurationSection("storage");
        if (storage != null) {
            for (String key : storage.getKeys(false)) {
                data.addItem(key, storage.getInt(key));
            }
        }
        ConfigurationSection orders = config.getConfigurationSection("orders");
        if (orders != null) {
            for (String key : orders.getKeys(false)) {
                ConfigurationSection section = orders.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Map<String, Integer> items = new LinkedHashMap<>();
                ConfigurationSection itemSection = section.getConfigurationSection("items");
                if (itemSection != null) {
                    for (String item : itemSection.getKeys(false)) {
                        if (plugin.getItems().get(item) != null) {
                            items.put(item, itemSection.getInt(item));
                        }
                    }
                }
                data.getOrders().add(new Order(items, section.getDouble("coins"), section.getInt("xp"),
                        section.getLong("available-at")));
            }
        }
        data.setDirty(false);
        return data;
    }

    public void save(PlayerData data) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", data.getName());
        config.set("level", data.getLevel());
        config.set("xp", data.getXp());
        config.set("coins", data.getCoins());
        config.set("silo-upgrades", data.getSiloUpgrades());
        config.set("barn-upgrades", data.getBarnUpgrades());
        config.set("fields-bought", data.getFieldsBought());
        config.set("roadside-slots", data.getRoadsideSlots());
        config.set("started", data.isStarted());
        for (Map.Entry<String, Integer> entry : data.getStorage().entrySet()) {
            config.set("storage." + entry.getKey(), entry.getValue());
        }
        List<Order> orders = new ArrayList<>(data.getOrders());
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            String path = "orders." + i;
            for (Map.Entry<String, Integer> item : order.getItems().entrySet()) {
                config.set(path + ".items." + item.getKey(), item.getValue());
            }
            config.set(path + ".coins", order.getCoins());
            config.set(path + ".xp", order.getXp());
            config.set(path + ".available-at", order.getAvailableAt());
        }
        try {
            config.save(file(data.getUuid()));
            data.setDirty(false);
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke gemme data for " + data.getName() + ": " + e.getMessage());
        }
    }

    public void unload(Player player) {
        PlayerData data = loaded.remove(player.getUniqueId());
        if (data != null) {
            save(data);
        }
    }

    public void saveDirty() {
        for (PlayerData data : loaded.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
    }

    public void saveAll() {
        for (PlayerData data : loaded.values()) {
            save(data);
        }
    }
}
