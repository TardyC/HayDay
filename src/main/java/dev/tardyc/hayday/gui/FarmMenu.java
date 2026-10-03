package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.island.IslandManager;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Min gård: din ø, hjem, hvem der må komme på besøg, venner og besøgende.
 *
 * <pre>
 *  . . . . I . . . .     I = info om øen
 *  . H . S . A . V .     H = tag hjem, S = sæt hjem, A = adgang, V = venner
 *  . B . N . O . F .     B = besøgende, N = navn, O = besøg andre, F = forbudte
 *  . . « . T . X . .     T = torvet
 * </pre>
 */
public final class FarmMenu extends Menu {

    public FarmMenu(HayDayPlugin plugin, Player player) {
        super(plugin, player, 4, "&2&lMin gård", "island");
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void render() {
        IslandManager islands = plugin.getIslands();
        Island island = islands.isEnabled() ? islands.get(player.getUniqueId()) : null;
        if (island == null) {
            set(13, new ItemBuilder(Material.GRASS_BLOCK)
                    .name(islands.isEnabled() ? "&a&lFå din egen ø!" : "&cØerne er slået fra")
                    .lore(islands.isEnabled()
                            ? new String[]{"&7Du har ikke en ø endnu.", "", "&e» Klik for at tage derhen"}
                            : new String[]{"&7Serveren bruger ikke HayDay-øer."})
                    .build(), click -> {
                if (islands.isEnabled()) {
                    closeLater();
                    Bukkit.getScheduler().runTask(plugin, () -> islands.teleportHome(player));
                }
            });
            renderBottom(islands);
            fillEmpty();
            return;
        }

        List<Player> visitors = islands.visitors(island);
        set(4, new ItemBuilder(Material.GRASS_BLOCK)
                .name("&a&l" + islands.farmName(island))
                .lore("&7Ejer: &f" + island.getOwnerName(),
                        "&7Level: &f" + islands.level(island),
                        "&7Ø nr.: &f" + island.getGridX() + ", " + island.getGridZ(),
                        "",
                        "&c❤ &f" + island.getLikes().size() + " likes",
                        "&7Besøg i alt: &f" + island.getVisits(),
                        "&7Besøgende lige nu: &f" + visitors.size(),
                        "&7Adgang: " + islands.accessName(island.getAccess()))
                .build());

        set(10, button(Material.OAK_DOOR, "&a&lTag hjem", "&7Teleportér til din gård.", "", "&e» Klik"), click -> {
            closeLater();
            Bukkit.getScheduler().runTask(plugin, () -> islands.teleportHome(player));
        });

        set(12, button(Material.RED_BED, "&e&lSæt hjem her",
                "&7Gør stedet du står på til", "&7din (og gæsternes) ankomst.", "", "&e» Klik"), click -> {
            Location location = player.getLocation();
            if (islands.getAt(location) != island) {
                plugin.getMessages().send(player, "island.home-not-on-island");
                Sounds.play(player, Sounds.ERROR);
                return;
            }
            islands.setHome(island, location);
            plugin.getMessages().send(player, "island.home-set");
            Sounds.play(player, Sounds.SUCCESS);
        });

        Island.Access access = island.getAccess();
        Material accessIcon = access == Island.Access.ALLE ? Material.LIME_DYE
                : access == Island.Access.VENNER ? Material.YELLOW_DYE : Material.RED_DYE;
        set(14, new ItemBuilder(accessIcon)
                .name("&b&lHvem må besøge?")
                .lore("&7Nu: " + islands.accessName(access),
                        "",
                        (access == Island.Access.ALLE ? "&f» " : "&8  ") + strip(islands.accessName(Island.Access.ALLE)),
                        (access == Island.Access.VENNER ? "&f» " : "&8  ") + strip(islands.accessName(Island.Access.VENNER)),
                        (access == Island.Access.INGEN ? "&f» " : "&8  ") + strip(islands.accessName(Island.Access.INGEN)),
                        "",
                        "&e» Klik for at skifte")
                .build(), click -> {
            islands.setAccess(island, access.next());
            plugin.getMessages().send(player, "island.access-set", "access", islands.accessName(island.getAccess()));
            Sounds.play(player, Sounds.CLICK);
            update();
        });

        List<String> friendLore = new ArrayList<>();
        friendLore.add("&7Venner kan altid besøge din gård,");
        friendLore.add("&7også når den kun er åben for venner.");
        friendLore.add("");
        int shown = 0;
        for (String name : island.getFriends().values()) {
            if (shown++ >= 8) {
                friendLore.add("&8... og " + (island.getFriends().size() - 8) + " mere");
                break;
            }
            friendLore.add("&8• &f" + name);
        }
        if (island.getFriends().isEmpty()) {
            friendLore.add("&8Ingen venner endnu.");
        }
        friendLore.add("");
        friendLore.add("&e» Klik for at se dine venner");
        set(16, button(Material.PLAYER_HEAD, "&d&lVenner &7(" + island.getFriends().size() + ")",
                friendLore.toArray(new String[0])), click -> openLater(new FriendsMenu(plugin, player)));

        List<String> visitorLore = new ArrayList<>();
        for (Player visitor : visitors) {
            visitorLore.add("&8• &f" + visitor.getName());
        }
        if (visitors.isEmpty()) {
            visitorLore.add("&8Ingen besøgende lige nu.");
        } else {
            visitorLore.add("");
            visitorLore.add("&c» Shift-klik for at sende alle hjem");
        }
        set(19, button(Material.SPYGLASS, "&6&lBesøgende &7(" + visitors.size() + ")",
                visitorLore.toArray(new String[0])), click -> {
            if (!click.isShiftClick() || visitors.isEmpty()) {
                return;
            }
            for (Player visitor : visitors) {
                if (!visitor.hasPermission("hayday.bypass")) {
                    islands.kick(visitor, island, "island.kicked");
                }
            }
            plugin.getMessages().send(player, "island.kicked-all", "count", visitors.size());
            update();
        });

        set(21, button(Material.NAME_TAG, "&6&lGårdens navn",
                "&7Nu: &f" + islands.farmName(island),
                "",
                "&7Skift med:",
                "&f/hayday gaard navn <navn>"));

        set(23, button(Material.COMPASS, "&b&lBesøg andre", "&7Se alle gårde og tag på besøg.", "", "&e» Klik"),
                click -> openLater(new VisitMenu(plugin, player, 0)));

        List<String> bannedLore = new ArrayList<>();
        bannedLore.add("&7Spillere der aldrig må besøge dig.");
        bannedLore.add("");
        for (String name : island.getBanned().values()) {
            bannedLore.add("&8• &c" + name);
        }
        if (island.getBanned().isEmpty()) {
            bannedLore.add("&8Ingen.");
        }
        bannedLore.add("");
        bannedLore.add("&f/hayday forbyd <spiller>");
        bannedLore.add("&f/hayday tillad <spiller>");
        set(25, button(Material.IRON_BARS, "&c&lForbudte &7(" + island.getBanned().size() + ")",
                bannedLore.toArray(new String[0])));

        renderBottom(islands);
        fillEmpty();
    }

    private void renderBottom(IslandManager islands) {
        set(29, backButton(), click -> openLater(new MainMenu(plugin, player)));
        if (islands.isEnabled()) {
            set(31, button(Material.BELL, "&e&lTorvet", "&7Midten af HayDay-verdenen,", "&7hvor alle mødes.", "", "&e» Klik"),
                    click -> {
                        closeLater();
                        Bukkit.getScheduler().runTask(plugin, () -> islands.teleportSpawn(player));
                    });
        }
        set(33, closeButton(), click -> closeLater());
    }

    private static String strip(String colored) {
        return Text.strip(colored);
    }
}
