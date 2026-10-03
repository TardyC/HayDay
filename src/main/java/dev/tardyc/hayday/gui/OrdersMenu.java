package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Order;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ordretavlen. Venstreklik leverer en ordre, shift-højreklik kasserer den.
 */
public final class OrdersMenu extends Menu {

    public OrdersMenu(HayDayPlugin plugin, Player player) {
        super(plugin, player, 3, "&b&lOrdretavle", "orders");
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void render() {
        PlayerData data = plugin.getPlayers().get(player);
        plugin.getOrders().ensure(data);
        long now = System.currentTimeMillis();

        fillRow(0);
        set(4, new ItemBuilder(Material.OAK_SIGN)
                .name("&b&lOrdretavle")
                .lore("&7Kunderne vil gerne købe dine varer!",
                        "&7Lever en ordre for at få penge og XP.",
                        "",
                        "&eVenstreklik &7= lever ordre",
                        "&cShift + højreklik &7= kassér ordre")
                .build());

        List<Order> orders = data.getOrders();
        for (int i = 0; i < orders.size() && i < 9; i++) {
            Order order = orders.get(i);
            int index = i;
            if (order.isWaiting()) {
                set(9 + i, new ItemBuilder(Material.CLOCK)
                        .name("&7Venter på ny ordre...")
                        .lore("&7Ny ordre om &f" + Text.timeMillis(order.getAvailableAt() - now))
                        .build());
                continue;
            }
            set(9 + i, orderIcon(data, order, i + 1), click -> click(index, click));
        }

        fillRow(2);
        set(18, backButton(), click -> openLater(new MainMenu(plugin, player)));
        set(26, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private org.bukkit.inventory.ItemStack orderIcon(PlayerData data, Order order, int number) {
        boolean ready = plugin.getOrders().canComplete(data, order);
        List<String> lore = new ArrayList<>();
        lore.add("&7Kunden ønsker:");
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            FarmItem item = plugin.getItems().get(entry.getKey());
            int have = data.getAmount(entry.getKey());
            boolean enough = have >= entry.getValue();
            lore.add((enough ? "&a✔ " : "&c✘ ") + entry.getValue() + "x " + (item == null ? entry.getKey() : item.getName())
                    + " &7(" + have + "/" + entry.getValue() + ")");
        }
        lore.add("");
        lore.add("&7Belønning:");
        lore.add("&6+" + money(order.getCoins()));
        lore.add("&b+" + order.getXp() + " XP");
        lore.add("");
        lore.add(ready ? "&a» Klik for at levere «" : "&c» Du mangler varer «");
        lore.add("&8Shift + højreklik for at kassere");
        return new ItemBuilder(ready ? Material.FILLED_MAP : Material.PAPER)
                .name((ready ? "&a&l" : "&6&l") + "Ordre #" + number)
                .lore(lore)
                .glow(ready)
                .build();
    }

    private void click(int index, ClickType click) {
        if (click == ClickType.SHIFT_RIGHT) {
            plugin.getOrders().discard(player, index);
        } else if (click.isLeftClick()) {
            plugin.getOrders().complete(player, index);
        }
        update();
    }
}
