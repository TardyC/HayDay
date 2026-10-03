package dev.tardyc.hayday.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alt HayDay-data for én spiller: level, xp, mønter, lager og ordrer.
 */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private int level = 1;
    private long xp;
    private double coins;
    private int siloUpgrades;
    private int barnUpgrades;
    private int fieldsBought;
    private int roadsideSlots;
    private long firstJoin;
    private long lastSeen;
    private boolean started;
    private final Map<String, Integer> storage = new LinkedHashMap<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<ShipCrate> shipCrates = new ArrayList<>();
    private long shipArrivesAt;
    private long shipLeavesAt;

    private transient boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        dirty = true;
    }

    public long getXp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
        dirty = true;
    }

    public double getCoins() {
        return coins;
    }

    public void setCoins(double coins) {
        this.coins = Math.max(0, coins);
        dirty = true;
    }

    public int getSiloUpgrades() {
        return siloUpgrades;
    }

    public void setSiloUpgrades(int siloUpgrades) {
        this.siloUpgrades = siloUpgrades;
        dirty = true;
    }

    public int getBarnUpgrades() {
        return barnUpgrades;
    }

    public void setBarnUpgrades(int barnUpgrades) {
        this.barnUpgrades = barnUpgrades;
        dirty = true;
    }

    public int getUpgrades(ItemCategory category) {
        return category == ItemCategory.CROP ? siloUpgrades : barnUpgrades;
    }

    public void setUpgrades(ItemCategory category, int upgrades) {
        if (category == ItemCategory.CROP) {
            setSiloUpgrades(upgrades);
        } else {
            setBarnUpgrades(upgrades);
        }
    }

    public int getFieldsBought() {
        return fieldsBought;
    }

    public void setFieldsBought(int fieldsBought) {
        this.fieldsBought = fieldsBought;
        dirty = true;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
        dirty = true;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        dirty = true;
    }

    public int getRoadsideSlots() {
        return roadsideSlots;
    }

    public void setRoadsideSlots(int roadsideSlots) {
        this.roadsideSlots = Math.max(0, roadsideSlots);
        dirty = true;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
        dirty = true;
    }

    public Map<String, Integer> getStorage() {
        return storage;
    }

    public int getAmount(String itemId) {
        Integer amount = storage.get(itemId);
        return amount == null ? 0 : amount;
    }

    public void addItem(String itemId, int amount) {
        if (amount <= 0) {
            return;
        }
        storage.put(itemId, getAmount(itemId) + amount);
        dirty = true;
    }

    /** Fjerner varer. Returnerer false (og ændrer intet) hvis der ikke er nok. */
    public boolean removeItem(String itemId, int amount) {
        int current = getAmount(itemId);
        if (amount <= 0) {
            return true;
        }
        if (current < amount) {
            return false;
        }
        if (current == amount) {
            storage.remove(itemId);
        } else {
            storage.put(itemId, current - amount);
        }
        dirty = true;
        return true;
    }

    public boolean hasItems(Map<String, Integer> items) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (getAmount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public List<Order> getOrders() {
        return orders;
    }

    /** Kasserne på skibet (tom liste = skibet er ude at sejle). */
    public List<ShipCrate> getShipCrates() {
        return shipCrates;
    }

    public long getShipArrivesAt() {
        return shipArrivesAt;
    }

    public void setShipArrivesAt(long shipArrivesAt) {
        this.shipArrivesAt = shipArrivesAt;
        dirty = true;
    }

    public long getShipLeavesAt() {
        return shipLeavesAt;
    }

    public void setShipLeavesAt(long shipLeavesAt) {
        this.shipLeavesAt = shipLeavesAt;
        dirty = true;
    }

    public void reset() {
        level = 1;
        xp = 0;
        coins = 0;
        siloUpgrades = 0;
        barnUpgrades = 0;
        fieldsBought = 0;
        roadsideSlots = 0;
        started = false;
        storage.clear();
        orders.clear();
        shipCrates.clear();
        shipArrivesAt = 0;
        shipLeavesAt = 0;
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
