package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Avisen: alle varer som andre spillere har til salg. Klik for at købe, shift-klik for at besøge vejboden.
 */
public final class NewspaperMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public NewspaperMenu(HayDayPlugin plugin, Player player, int page) {
        super(plugin, player, 6, "&f&lAvisen &8» &7Tilbud", "news");
        this.page = Math.max(0, page);
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void render() {
        List<Listing> offers = plugin.getMarket().getNewspaper(player.getUniqueId());
        int pages = Math.max(1, (offers.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < offers.size(); i++) {
            Listing listing = offers.get(start + i);
            FarmItem item = plugin.getItems().get(listing.getItemId());
            set(i, item.icon()
                    .amount(listing.getAmount())
                    .name("&f" + listing.getAmount() + "x " + item.getName())
                    .lore("&7Sælger: &f" + listing.getSellerName(),
                            "&7Pris: &6" + money(listing.getPrice()),
                            "&7Sat til salg for &f" + Text.timeMillis(System.currentTimeMillis() - listing.getCreatedAt()) + " &7siden",
                            "",
                            "&e» Klik for at købe",
                            "&7» Shift-klik for at besøge vejboden")
                    .build(), click -> {
                if (click.isShiftClick()) {
                    openLater(new RoadsideMenu(plugin, player, listing.getSeller(), listing.getSellerName()));
                    return;
                }
                plugin.getMarket().buy(player, listing);
                update();
            });
        }
        if (offers.isEmpty()) {
            set(22, new ItemBuilder(Material.COBWEB)
                    .name("&7Ingen tilbud lige nu")
                    .lore("&7Kom tilbage senere, eller sæt selv", "&7varer til salg i din vejbod.")
                    .build());
        }

        fillRow(5);
        set(45, backButton(), click -> openLater(new MainMenu(plugin, player)));
        if (page > 0) {
            set(48, button(Material.ARROW, "&e« Forrige side"), click -> {
                page--;
                update();
            });
        }
        set(49, new ItemBuilder(Material.MAP)
                .name("&f&lAvisen")
                .lore("&7Side &f" + (page + 1) + "&7/&f" + pages, "&7Tilbud i alt: &f" + offers.size(),
                        "&7Dine penge: &6" + money(plugin.getEconomy().getBalance(player)))
                .build());
        if (page < pages - 1) {
            set(50, button(Material.ARROW, "&eNæste side »"), click -> {
                page++;
                update();
            });
        }
        set(53, closeButton(), click -> closeLater());
        fillEmpty();
    }
}
