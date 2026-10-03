package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Silo (afgrøder) eller lade (produkter). Klik på en vare for at sælge den.
 */
public final class StorageMenu extends Menu {

    private final ItemCategory category;

    public StorageMenu(HayDayPlugin plugin, Player player, ItemCategory category) {
        super(plugin, player, 6, category == ItemCategory.CROP ? "&e&lSilo &8» &7Afgrøder" : "&6&lLade &8» &7Produkter",
                category == ItemCategory.CROP ? "silo" : "barn");
        this.category = category;
    }

    @Override
    protected void render() {
        PlayerData data = plugin.getPlayers().get(player);
        int slot = 0;
        for (FarmItem item : plugin.getItems().byCategory(category)) {
            int amount = data.getAmount(item.getId());
            if (amount <= 0 || slot >= 45) {
                continue;
            }
            set(slot++, item.icon()
                    .amount(amount)
                    .lore("&7Antal: &f" + amount,
                            "&7Salgspris: &6" + money(item.getSellPrice()) + " &7pr. stk.",
                            "&7Værdi i alt: &6" + money(item.getSellPrice() * amount),
                            "",
                            "&e» Klik for at sælge")
                    .build(), click -> openLater(new SellMenu(plugin, player, item, category)));
        }
        if (slot == 0) {
            set(22, new ItemBuilder(Material.COBWEB)
                    .name("&7Tomt")
                    .lore(category == ItemCategory.CROP
                            ? "&7Høst dine marker for at fylde siloen."
                            : "&7Lav varer i dine bygninger for at fylde laden.")
                    .build());
        }

        fillRow(5);
        set(45, backButton(), click -> openLater(new MainMenu(plugin, player)));
        ItemCategory other = category == ItemCategory.CROP ? ItemCategory.PRODUCT : ItemCategory.CROP;
        set(47, new ItemBuilder(other == ItemCategory.CROP ? Material.HAY_BLOCK : Material.CHEST)
                .name(other == ItemCategory.CROP ? "&e&lGå til silo" : "&6&lGå til lade")
                .build(), click -> openLater(new StorageMenu(plugin, player, other)));

        int used = plugin.getStorage().used(data, category);
        int capacity = plugin.getStorage().capacity(data, category);
        String name = plugin.getStorage().name(category);
        set(49, new ItemBuilder(category == ItemCategory.CROP ? Material.HAY_BLOCK : Material.CHEST)
                .name("&f&l" + name)
                .lore("&7Fyldt: &f" + used + "&7/&f" + capacity,
                        "&8[" + plugin.getSettings().bar(used / (double) Math.max(1, capacity)) + "&8]")
                .build());

        if (plugin.getStorage().isMaxed(data, category)) {
            set(53, new ItemBuilder(Material.GRAY_DYE).name("&7Fuldt opgraderet").build());
        } else {
            int next = plugin.getSettings().storage(category).upgradeCapacity;
            set(53, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                    .name("&a&lOpgradér " + name.toLowerCase())
                    .lore("&7Giver &f+" + next + " &7plads.",
                            "&7Pris: &6" + money(plugin.getStorage().upgradePrice(data, category)),
                            "",
                            "&e» Klik for at opgradere")
                    .build(), click -> {
                plugin.getStorage().upgrade(player, category);
                update();
            });
        }
        fillEmpty();
    }
}
