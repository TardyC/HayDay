package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.Order;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.ShipCrate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hver spiller har sin egen fil: plugins/HayDay/players/<uuid>.yml med profil, lager, ordrer, skib
 * og en oversigt over gården. Filerne kan også indlæses for spillere der er offline (admin-kommandoer).
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

    public boolean hasFile(UUID uuid) {
        return file(uuid).exists();
    }

    /**
     * Data for en spiller der måske er offline. Er spilleren online, returneres de levende data;
     * ellers læses filen (husk at kalde {@link #save(PlayerData)} efter ændringer).
     */
    public PlayerData getOrLoad(UUID uuid, String name) {
        PlayerData data = loaded.get(uuid);
        return data != null ? data : load(uuid, name);
    }

    /** Sletter en spillers fil (bruges af admin reset). */
    public void deleteFile(UUID uuid) {
        File file = file(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Kunne ikke slette " + file.getPath());
        }
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
            data.setFirstJoin(System.currentTimeMillis());
            data.setDirty(true);
            return data;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (name == null || name.isEmpty()) {
            data.setName(config.getString("name", "?"));
        }
        data.setFirstJoin(config.getLong("first-join", 0));
        data.setLastSeen(config.getLong("last-seen", 0));
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
        data.setShipArrivesAt(config.getLong("ship.arrives", 0));
        data.setShipLeavesAt(config.getLong("ship.leaves", 0));
        ConfigurationSection crates = config.getConfigurationSection("ship.crates");
        if (crates != null) {
            for (String key : crates.getKeys(false)) {
                ConfigurationSection section = crates.getConfigurationSection(key);
                if (section != null && plugin.getItems().get(section.getString("item")) != null) {
                    ShipCrate crate = new ShipCrate(section.getString("item"), section.getInt("amount", 1),
                            section.getDouble("coins"), section.getInt("xp"), section.getBoolean("filled"));
                    crate.setHelper(section.getString("helper", null));
                    data.getShipCrates().add(crate);
                }
            }
        }
        data.setDirty(false);
        return data;
    }

    public void save(PlayerData data) {
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(Arrays.asList(
                "HayDay - spillerfil for " + data.getName(),
                "Alt om spilleren: profil, penge (kun HayDay-mønter), lager, ordrer, skib, vejbod-pladser og øen.",
                "Sektionen 'gaard' er kun en oversigt - marker, bygninger og øer ligger i data/fields.yml, data/buildings.yml og data/islands.yml.",
                "Ret kun i filen mens spilleren er offline - eller brug /hayday admin."));
        config.set("name", data.getName());
        config.set("uuid", data.getUuid().toString());
        config.set("first-join", data.getFirstJoin());
        config.set("last-seen", data.getLastSeen());
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
        config.set("ship.arrives", data.getShipArrivesAt());
        config.set("ship.leaves", data.getShipLeavesAt());
        List<ShipCrate> crates = new ArrayList<>(data.getShipCrates());
        for (int i = 0; i < crates.size(); i++) {
            ShipCrate crate = crates.get(i);
            String path = "ship.crates." + i;
            config.set(path + ".item", crate.getItemId());
            config.set(path + ".amount", crate.getAmount());
            config.set(path + ".coins", crate.getCoins());
            config.set(path + ".xp", crate.getXp());
            config.set(path + ".filled", crate.isFilled());
            config.set(path + ".helper", crate.getHelper());
        }
        // Oversigt over gården (kun til info)
        List<String> fieldLines = new ArrayList<>();
        int ready = 0;
        long now = System.currentTimeMillis();
        for (Field field : plugin.getFarm().getFields(data.getUuid())) {
            fieldLines.add(field.getSoil().toString() + (field.getCropId() == null ? " (tom)" : " - " + field.getCropId()));
            if (field.isReady(now)) {
                ready++;
            }
        }
        List<String> buildingLines = new ArrayList<>();
        for (Building building : plugin.getFarm().getBuildings(data.getUuid())) {
            buildingLines.add(building.getTypeId() + " @ " + building.getPos() + " (kø: " + building.getQueue().size() + ")");
        }
        config.set("gaard.marker", fieldLines.size());
        config.set("gaard.klar-til-hoest", ready);
        config.set("gaard.mark-liste", fieldLines);
        config.set("gaard.bygninger", buildingLines);
        Island island = plugin.getIslands().get(data.getUuid());
        if (island != null) {
            config.set("gaard.oe.navn", plugin.getIslands().farmName(island));
            config.set("gaard.oe.plads", island.getGridX() + ", " + island.getGridZ());
            config.set("gaard.oe.adgang", island.getAccess().id());
            config.set("gaard.oe.besoeg", island.getVisits());
            config.set("gaard.oe.likes", island.getLikes().size());
            config.set("gaard.oe.venner", new ArrayList<>(island.getFriends().values()));
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
