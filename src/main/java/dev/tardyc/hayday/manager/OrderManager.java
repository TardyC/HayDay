package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.Order;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ordretavlen: tilfældige ordrer baseret på hvad spilleren har låst op.
 */
public final class OrderManager {

    private final HayDayPlugin plugin;

    public OrderManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sørger for at spilleren har det rigtige antal ordrer, og at ventende ordrer bliver til nye. */
    public void ensure(PlayerData data) {
        int slots = plugin.getSettings().orderSlots(data.getLevel());
        List<Order> orders = data.getOrders();
        long now = System.currentTimeMillis();
        boolean changed = false;
        while (orders.size() < slots) {
            orders.add(generate(data));
            changed = true;
        }
        while (orders.size() > slots) {
            orders.remove(orders.size() - 1);
            changed = true;
        }
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            if (order.isWaiting() && now >= order.getAvailableAt()) {
                orders.set(i, generate(data));
                changed = true;
            }
        }
        if (changed) {
            data.setDirty(true);
        }
    }

    public Order generate(PlayerData data) {
        Settings settings = plugin.getSettings();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<FarmItem> candidates = new ArrayList<>();
        for (FarmItem item : plugin.getItems().all()) {
            if (item.getUnlockLevel() <= data.getLevel()) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) {
            return Order.waiting(System.currentTimeMillis() + settings.orderNewDelay);
        }
        Collections.shuffle(candidates, random);
        int maxCount = Math.min(settings.orderMaxItems, candidates.size());
        int minCount = Math.min(settings.orderMinItems, maxCount);
        int count = random.nextInt(minCount, maxCount + 1);
        Map<String, Integer> items = new LinkedHashMap<>();
        double value = 0;
        double xp = 0;
        for (int i = 0; i < count && i < candidates.size(); i++) {
            FarmItem item = candidates.get(i);
            int amount = item.getCategory() == ItemCategory.CROP
                    ? random.nextInt(settings.orderCropMin, settings.orderCropMax + 1)
                    : random.nextInt(settings.orderProductMin, settings.orderProductMax + 1);
            items.put(item.getId(), amount);
            value += item.getSellPrice() * amount;
            xp += item.getXp() * amount;
        }
        double multiplier = settings.orderRewardMin + random.nextDouble() * (settings.orderRewardMax - settings.orderRewardMin);
        double coins = Math.max(1, Math.ceil(value * multiplier));
        int rewardXp = (int) Math.max(1, Math.ceil(xp * settings.orderXpMultiplier));
        return new Order(items, coins, rewardXp, 0);
    }

    public boolean canComplete(PlayerData data, Order order) {
        return !order.isWaiting() && data.hasItems(order.getItems());
    }

    public int countReady(PlayerData data) {
        int ready = 0;
        for (Order order : data.getOrders()) {
            if (canComplete(data, order)) {
                ready++;
            }
        }
        return ready;
    }

    public void complete(Player player, int index) {
        PlayerData data = plugin.getPlayers().get(player);
        ensure(data);
        if (index < 0 || index >= data.getOrders().size()) {
            return;
        }
        Order order = data.getOrders().get(index);
        if (order.isWaiting()) {
            return;
        }
        if (!data.hasItems(order.getItems())) {
            plugin.getMessages().send(player, "orders.missing", "missing", missing(data, order.getItems()));
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            data.removeItem(entry.getKey(), entry.getValue());
        }
        data.getOrders().set(index, Order.waiting(System.currentTimeMillis() + plugin.getSettings().orderNewDelay));
        data.setDirty(true);
        double coins = plugin.getEvents().money(order.getCoins());
        int xp = plugin.getEvents().xp(order.getXp());
        plugin.getEconomy().deposit(player, coins);
        plugin.getMessages().send(player, "orders.completed", "coins", plugin.getEconomy().format(coins), "xp", xp);
        plugin.getAnimations().coinBurst(player);
        plugin.getAnimations().floatingText(player.getLocation().add(0, 2.3, 0),
                "&6+" + plugin.getEconomy().format(coins) + " &b+" + xp + " XP");
        plugin.getLevels().addXp(player, xp);
    }

    public void discard(Player player, int index) {
        PlayerData data = plugin.getPlayers().get(player);
        if (index < 0 || index >= data.getOrders().size() || data.getOrders().get(index).isWaiting()) {
            return;
        }
        long delay = plugin.getSettings().orderDiscardDelay;
        data.getOrders().set(index, Order.waiting(System.currentTimeMillis() + delay));
        data.setDirty(true);
        plugin.getMessages().send(player, "orders.discarded", "time", Text.timeMillis(delay));
        Sounds.play(player, Sounds.CLICK, 0.6f);
    }

    /** Tekst som "2x Hvede, 1x Brød" for de varer spilleren mangler. */
    public String missing(PlayerData data, Map<String, Integer> required) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int missing = entry.getValue() - data.getAmount(entry.getKey());
            if (missing > 0) {
                FarmItem item = plugin.getItems().get(entry.getKey());
                parts.add(missing + "x " + (item == null ? entry.getKey() : item.getName()) + "&f");
            }
        }
        return Text.color(String.join(", ", parts));
    }
}
