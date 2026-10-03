package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Vælg hvor mange af en vare der skal sælges.
 */
public final class SellMenu extends Menu {

    private static final int[] AMOUNTS = {1, 5, 10, 32, 64};
    private static final int[] SLOTS = {10, 11, 12, 13, 14};

    private final FarmItem item;
    private final ItemCategory returnTo;

    public SellMenu(HayDayPlugin plugin, Player player, FarmItem item, ItemCategory returnTo) {
        super(plugin, player, 3, "&6&lSælg &8» &7" + item.getPlainName(), "sell");
        this.item = item;
        this.returnTo = returnTo;
    }

    @Override
    protected void render() {
        PlayerData data = plugin.getPlayers().get(player);
        int owned = data.getAmount(item.getId());
        set(4, item.icon()
                .amount(Math.max(1, owned))
                .lore("&7Du har: &f" + owned,
                        "&7Pris pr. stk: &6" + money(item.getSellPrice()))
                .build());

        for (int i = 0; i < AMOUNTS.length; i++) {
            int amount = AMOUNTS[i];
            boolean can = owned >= amount;
            set(SLOTS[i], new ItemBuilder(can ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                    .amount(amount)
                    .name((can ? "&a" : "&c") + "Sælg " + amount)
                    .lore("&7Du får: &6" + money(item.getSellPrice() * amount))
                    .build(), click -> sell(amount));
        }
        set(16, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6&lSælg alle")
                .lore("&7Sælg alle &f" + owned + " &7stk.",
                        "&7Du får: &6" + money(item.getSellPrice() * owned))
                .glow(owned > 0)
                .build(), click -> sell(plugin.getPlayers().get(player).getAmount(item.getId())));

        set(18, backButton(), click -> openLater(new StorageMenu(plugin, player, returnTo)));
        set(26, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private void sell(int amount) {
        if (plugin.getStorage().sell(player, item, amount)
                && plugin.getPlayers().get(player).getAmount(item.getId()) <= 0) {
            openLater(new StorageMenu(plugin, player, returnTo));
            return;
        }
        update();
    }
}
