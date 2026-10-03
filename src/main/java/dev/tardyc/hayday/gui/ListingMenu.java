package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.manager.MarketManager;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Sæt en vare til salg i vejboden: vælg vare, antal og pris.
 *
 * <pre>
 *  rækker 0-3: varer fra silo og lade
 *  række 4:    . -5 -1 . [vare] . +1 +5 .
 *  række 5:    « -10 -1 . [OK] . +1 +10 max
 * </pre>
 */
public final class ListingMenu extends Menu {

    private final int slot;
    private FarmItem selected;
    private int amount = 1;
    private double price;

    public ListingMenu(HayDayPlugin plugin, Player player, int slot) {
        super(plugin, player, 6, "&6&lSæt til salg", "listing");
        this.slot = slot;
    }

    @Override
    protected void render() {
        PlayerData data = plugin.getPlayers().get(player);
        MarketManager market = plugin.getMarket();

        // Lageret
        List<FarmItem> owned = new ArrayList<>();
        for (FarmItem item : plugin.getItems().byCategory(ItemCategory.CROP)) {
            if (data.getAmount(item.getId()) > 0) {
                owned.add(item);
            }
        }
        for (FarmItem item : plugin.getItems().byCategory(ItemCategory.PRODUCT)) {
            if (data.getAmount(item.getId()) > 0) {
                owned.add(item);
            }
        }
        for (int i = 0; i < owned.size() && i < 36; i++) {
            FarmItem item = owned.get(i);
            boolean isSelected = item == selected;
            set(i, item.icon(player)
                    .amount(data.getAmount(item.getId()))
                    .lore("&7Du har: &f" + data.getAmount(item.getId()),
                            "",
                            isSelected ? "&a✔ Valgt" : "&e» Klik for at vælge")
                    .glow(isSelected)
                    .build(), click -> select(item));
        }
        if (owned.isEmpty()) {
            set(13, new ItemBuilder(Material.COBWEB).name("&7Dit lager er tomt").build());
        }

        fillRow(4);
        fillRow(5);
        if (selected != null) {
            int have = data.getAmount(selected.getId());
            int maxAmount = Math.min(have, plugin.getSettings().marketMaxAmount);
            amount = Math.max(1, Math.min(amount, maxAmount));
            double min = market.minPrice(selected, amount);
            double max = market.maxPrice(selected, amount);
            price = Math.max(min, Math.min(max, price));

            set(37, adjust("&c-5 stk", Material.RED_STAINED_GLASS_PANE), click -> changeAmount(-5));
            set(38, adjust("&c-1 stk", Material.RED_STAINED_GLASS_PANE), click -> changeAmount(-1));
            set(40, selected.icon(player)
                    .amount(amount)
                    .name("&f" + amount + "x " + selected.getName())
                    .lore("&7Pris: &6" + money(price),
                            "&7Tilladt pris: &f" + money(min) + " &7- &f" + money(max),
                            "&7Normal værdi: &f" + money(market.basePrice(selected, amount)))
                    .build());
            set(42, adjust("&a+1 stk", Material.LIME_STAINED_GLASS_PANE), click -> changeAmount(1));
            set(43, adjust("&a+5 stk", Material.LIME_STAINED_GLASS_PANE), click -> changeAmount(5));

            set(46, adjust("&c-10 pris", Material.ORANGE_STAINED_GLASS_PANE), click -> changePrice(-10));
            set(47, adjust("&c-1 pris", Material.ORANGE_STAINED_GLASS_PANE), click -> changePrice(-1));
            set(49, new ItemBuilder(Material.LIME_CONCRETE)
                    .name("&a&lSæt til salg")
                    .lore("&f" + amount + "x " + selected.getName() + " &7for &6" + money(price))
                    .glow(true)
                    .build(), click -> confirm());
            set(51, adjust("&a+1 pris", Material.YELLOW_STAINED_GLASS_PANE), click -> changePrice(1));
            set(52, adjust("&a+10 pris", Material.YELLOW_STAINED_GLASS_PANE), click -> changePrice(10));
            set(53, adjust("&6Maks pris", Material.GOLD_NUGGET), click -> {
                price = market.maxPrice(selected, amount);
                Sounds.play(player, Sounds.CLICK);
                update();
            });
        } else {
            set(40, new ItemBuilder(Material.PAPER)
                    .name("&eVælg en vare ovenfor")
                    .lore("&7Klik på en vare fra dit lager.")
                    .build());
        }
        set(45, backButton(), click -> openLater(new RoadsideMenu(plugin, player, player.getUniqueId(), player.getName())));
        fillEmpty();
    }

    private org.bukkit.inventory.ItemStack adjust(String name, Material fallback) {
        return button(fallback, name);
    }

    private void select(FarmItem item) {
        selected = item;
        amount = Math.min(plugin.getPlayers().get(player).getAmount(item.getId()), plugin.getSettings().marketMaxAmount);
        amount = Math.max(1, Math.min(amount, 10));
        price = plugin.getMarket().defaultPrice(item, amount);
        Sounds.play(player, Sounds.CLICK);
        update();
    }

    private void changeAmount(int delta) {
        if (selected == null) {
            return;
        }
        amount += delta;
        int have = plugin.getPlayers().get(player).getAmount(selected.getId());
        amount = Math.max(1, Math.min(amount, Math.min(have, plugin.getSettings().marketMaxAmount)));
        price = plugin.getMarket().defaultPrice(selected, amount);
        Sounds.play(player, Sounds.CLICK);
        update();
    }

    private void changePrice(int delta) {
        if (selected == null) {
            return;
        }
        price += delta;
        Sounds.play(player, Sounds.CLICK);
        update();
    }

    private void confirm() {
        if (selected == null) {
            plugin.getMessages().send(player, "market.nothing-selected");
            return;
        }
        if (plugin.getMarket().create(player, slot, selected, amount, price)) {
            openLater(new RoadsideMenu(plugin, player, player.getUniqueId(), player.getName()));
        } else {
            update();
        }
    }
}
