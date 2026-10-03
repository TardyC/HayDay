package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.Sounds;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Silo (afgrøder) og lade (produkter): kapacitet, opgraderinger og salg.
 */
public final class StorageManager {

    private final HayDayPlugin plugin;

    public StorageManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public int capacity(PlayerData data, ItemCategory category) {
        return plugin.getSettings().storage(category).capacity(data.getUpgrades(category));
    }

    public int used(PlayerData data, ItemCategory category) {
        int used = 0;
        for (Map.Entry<String, Integer> entry : data.getStorage().entrySet()) {
            FarmItem item = plugin.getItems().get(entry.getKey());
            if (item != null && item.getCategory() == category) {
                used += entry.getValue();
            }
        }
        return used;
    }

    public int free(PlayerData data, ItemCategory category) {
        return Math.max(0, capacity(data, category) - used(data, category));
    }

    public boolean hasSpace(PlayerData data, FarmItem item, int amount) {
        return free(data, item.getCategory()) >= amount;
    }

    public String name(ItemCategory category) {
        return plugin.getMessages().get(category == ItemCategory.CROP ? "storage.silo" : "storage.barn");
    }

    public boolean isMaxed(PlayerData data, ItemCategory category) {
        return data.getUpgrades(category) >= plugin.getSettings().storage(category).maxUpgrades;
    }

    public double upgradePrice(PlayerData data, ItemCategory category) {
        return plugin.getSettings().storage(category).upgradePrice(data.getUpgrades(category));
    }

    public void upgrade(Player player, ItemCategory category) {
        PlayerData data = plugin.getPlayers().get(player);
        Settings.StorageSettings settings = plugin.getSettings().storage(category);
        if (isMaxed(data, category)) {
            plugin.getMessages().send(player, "storage.max", "storage", name(category));
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (!plugin.getEconomy().charge(player, upgradePrice(data, category))) {
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        data.setUpgrades(category, data.getUpgrades(category) + 1);
        plugin.getMessages().send(player, "storage.upgraded", "storage", name(category),
                "capacity", settings.capacity(data.getUpgrades(category)));
        Sounds.play(player, Sounds.LEVEL_UP, 1.4f);
    }

    public boolean sell(Player player, FarmItem item, int amount) {
        PlayerData data = plugin.getPlayers().get(player);
        if (amount <= 0 || !data.removeItem(item.getId(), amount)) {
            plugin.getMessages().send(player, "storage.not-enough", "item", item.getName());
            Sounds.play(player, Sounds.ERROR);
            return false;
        }
        double price = item.getSellPrice() * amount;
        plugin.getEconomy().deposit(player, price);
        plugin.getMessages().send(player, "storage.sold", "amount", amount, "item", item.getName(),
                "price", plugin.getEconomy().format(price));
        Sounds.play(player, Sounds.COINS);
        return true;
    }
}
