package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.island.IslandManager;
import dev.tardyc.hayday.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Besøg andre gårde: alle øer med ejerens hoved, level, likes og om man må komme ind.
 *
 * <pre>
 *  G G G G G G G G G     G = gårde (45 pr. side)
 *  ...
 *  « . T ‹ I › H . X     T = tilfældig gård, I = info, H = hjem
 * </pre>
 */
public final class VisitMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public VisitMenu(HayDayPlugin plugin, Player player, int page) {
        super(plugin, player, 6, "&b&lBesøg &8» &7Gårde", "visit");
        this.page = Math.max(0, page);
    }

    private List<Island> sortedIslands() {
        IslandManager islands = plugin.getIslands();
        List<Island> list = new ArrayList<>();
        for (Island island : islands.all()) {
            if (!island.isOwner(player.getUniqueId())) {
                list.add(island);
            }
        }
        // Online ejere først, så højeste level og flest likes
        list.sort(Comparator.<Island>comparingInt(island -> Bukkit.getPlayer(island.getOwner()) != null ? 0 : 1)
                .thenComparing(Comparator.<Island>comparingInt(islands::level).reversed())
                .thenComparing(Comparator.<Island>comparingInt(island -> island.getLikes().size()).reversed())
                .thenComparing(Island::getOwnerName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    protected void render() {
        IslandManager islands = plugin.getIslands();
        if (!islands.isEnabled()) {
            set(22, new ItemBuilder(Material.BARRIER)
                    .name("&cØerne er slået fra")
                    .lore("&7Du kan stadig besøge andres vejboder", "&7via &fAvisen&7.")
                    .build(), click -> openLater(new NewspaperMenu(plugin, player, 0)));
            renderBottom(1, 0);
            fillEmpty();
            return;
        }
        List<Island> list = sortedIslands();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            Island island = list.get(start + i);
            boolean online = Bukkit.getPlayer(island.getOwner()) != null;
            boolean open = islands.canEnter(player, island);
            set(i, new ItemBuilder(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(island.getOwner()))
                    .name("&a&l" + islands.farmName(island))
                    .lore("&7Ejer: &f" + island.getOwnerName() + (online ? " &a● online" : " &8● offline"),
                            "&7Level: &f" + islands.level(island),
                            "&c❤ &f" + island.getLikes().size() + "  &7Besøg: &f" + island.getVisits(),
                            "&7Adgang: " + islands.accessName(island.getAccess()),
                            "",
                            open ? "&e» Klik for at besøge" : "&c» Du kan ikke besøge denne gård")
                    .build(), click -> {
                if (!open) {
                    islands.sendDenied(player, island);
                    return;
                }
                closeLater();
                Bukkit.getScheduler().runTask(plugin, () -> islands.visit(player, island));
            });
        }
        if (list.isEmpty()) {
            set(22, new ItemBuilder(Material.COBWEB)
                    .name("&7Ingen andre gårde endnu")
                    .lore("&7Når andre spillere starter HayDay,", "&7kan du besøge dem her.")
                    .build());
        }
        renderBottom(pages, list.size());
        fillEmpty();
    }

    private void renderBottom(int pages, int total) {
        IslandManager islands = plugin.getIslands();
        fillRow(5);
        set(45, backButton(), click -> openLater(new MainMenu(plugin, player)));
        if (islands.isEnabled()) {
            set(47, button(Material.COMPASS, "&d&lTilfældig gård", "&7Tag på besøg hos en tilfældig farmer."), click -> {
                List<Island> open = new ArrayList<>();
                for (Island island : sortedIslands()) {
                    if (islands.canEnter(player, island)) {
                        open.add(island);
                    }
                }
                if (open.isEmpty()) {
                    plugin.getMessages().send(player, "island.no-random");
                    return;
                }
                Island island = open.get(ThreadLocalRandom.current().nextInt(open.size()));
                closeLater();
                Bukkit.getScheduler().runTask(plugin, () -> islands.visit(player, island));
            });
        }
        if (page > 0) {
            set(48, button(Material.ARROW, "&e« Forrige side"), click -> {
                page--;
                update();
            });
        }
        Island own = islands.get(player.getUniqueId());
        set(49, new ItemBuilder(Material.FILLED_MAP)
                .name("&b&lBesøg")
                .lore("&7Side &f" + (page + 1) + "&7/&f" + pages, "&7Gårde i alt: &f" + total,
                        own == null ? "&7Du har ikke en gård endnu."
                                : "&7Din gård: &c❤ &f" + own.getLikes().size() + "  &7Besøg: &f" + own.getVisits())
                .build());
        if (page < pages - 1) {
            set(50, button(Material.ARROW, "&eNæste side »"), click -> {
                page++;
                update();
            });
        }
        if (islands.isEnabled()) {
            set(51, button(Material.OAK_DOOR, "&a&lTag hjem", "&7Teleportér til din egen gård."), click -> {
                closeLater();
                Bukkit.getScheduler().runTask(plugin, () -> islands.teleportHome(player));
            });
        }
        set(53, closeButton(), click -> closeLater());
    }
}
