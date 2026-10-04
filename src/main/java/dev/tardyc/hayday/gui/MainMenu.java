package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.manager.ShipManager;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hovedmenuen (/hayday).
 *
 * <pre>
 *  . . . . P . . . .     P = profil
 *  . S . L . O . B .     S = silo, L = lade, O = ordretavle, B = butik
 *  . V . A . K . G .     V = vejbod, A = avisen, K = skibet, G = min gård (øen)
 *  . E . T . H . X .     E = besøg andre, T = top, H = hjælp, X = luk
 * </pre>
 */
public final class MainMenu extends Menu {

    public MainMenu(HayDayPlugin plugin, Player player) {
        super(plugin, player, 4, "&2&lHay&6&lDay &8» &7Din gård", "main");
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

        // Profil
        long needed = plugin.getLevels().xpForNext(data.getLevel());
        boolean max = plugin.getLevels().isMaxLevel(data);
        int rank = plugin.getLeaderboard().rank(player.getUniqueId());
        set(4, new ItemBuilder(Material.PLAYER_HEAD)
                .skull(player)
                .name("&a&l" + player.getName())
                .lore("&7Level: &f" + data.getLevel() + (max ? " &6(MAX)" : ""),
                        "&7XP: &b" + Text.number(data.getXp()) + (max ? "" : "&7/&b" + Text.number(needed)),
                        "&8[" + plugin.getSettings().bar(plugin.getLevels().progress(data)) + "&8]",
                        "",
                        "&7Penge: &6" + money(plugin.getEconomy().getBalance(player)),
                        "&7Placering: &e" + (rank > 0 ? "#" + rank : "-"))
                .lore(eventLore())
                .glow(!plugin.getEvents().getActive().isEmpty())
                .build());

        // Silo og lade
        set(10, storageIcon(Material.HAY_BLOCK, "&e&lSilo", data, ItemCategory.CROP, "afgrøder"),
                click -> openLater(new StorageMenu(plugin, player, ItemCategory.CROP)));
        set(12, storageIcon(Material.CHEST, "&6&lLade", data, ItemCategory.PRODUCT, "produkter"),
                click -> openLater(new StorageMenu(plugin, player, ItemCategory.PRODUCT)));

        // Ordrer
        int ready = plugin.getOrders().countReady(data);
        set(14, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&b&lOrdretavle")
                .lore("&7Lever varer og tjen penge og XP.",
                        "",
                        "&7Ordrer klar til levering: " + (ready > 0 ? "&a" : "&7") + ready,
                        "",
                        "&e» Klik for at åbne")
                .glow(ready > 0)
                .build(), click -> openLater(new OrdersMenu(plugin, player)));

        // Butik
        set(16, new ItemBuilder(Material.EMERALD)
                .name("&a&lButik")
                .lore("&7Køb nye marker og bygninger.",
                        "",
                        "&e» Klik for at åbne")
                .build(), click -> openLater(new ShopMenu(plugin, player)));

        // Vejbod
        int sold = plugin.getMarket().count(player.getUniqueId(), Listing.State.SOLD);
        int active = plugin.getMarket().count(player.getUniqueId(), Listing.State.ACTIVE);
        set(19, new ItemBuilder(Material.BARREL)
                .name("&6&lVejbod")
                .lore("&7Sæt dine varer til salg til",
                        "&7andre spillere.",
                        "",
                        "&7Til salg: &f" + active,
                        "&7Solgt (klar til at hente): " + (sold > 0 ? "&a" : "&7") + sold,
                        "",
                        "&e» Klik for at åbne")
                .glow(sold > 0)
                .build(), click -> openLater(new RoadsideMenu(plugin, player, player.getUniqueId(), player.getName())));

        // Avisen
        int offers = plugin.getMarket().getNewspaper(player.getUniqueId()).size();
        set(21, new ItemBuilder(Material.MAP)
                .name("&f&lAvisen")
                .lore("&7Se hvad andre spillere sælger",
                        "&7i deres vejboder.",
                        "",
                        "&7Tilbud lige nu: &f" + offers,
                        "",
                        "&e» Klik for at læse")
                .build(), click -> openLater(new NewspaperMenu(plugin, player, 0)));

        // Skibet
        set(23, shipIcon(data), click -> openLater(new ShipMenu(plugin, player)));

        // Min gård (øen)
        if (plugin.getIslands().isEnabled()) {
            set(25, farmOverview(now), click -> openLater(new FarmMenu(plugin, player)));
        } else {
            set(25, farmOverview(now));
        }

        // Besøg andre
        set(28, visitIcon(), click -> openLater(plugin.getIslands().isEnabled()
                ? new VisitMenu(plugin, player, 0) : new NewspaperMenu(plugin, player, 0)));

        // Top
        List<String> topLore = new ArrayList<>();
        List<LeaderboardManager.Entry> top = plugin.getLeaderboard().top(10);
        for (int i = 0; i < top.size(); i++) {
            LeaderboardManager.Entry entry = top.get(i);
            topLore.add("&e#" + (i + 1) + " &f" + entry.getName() + " &8- &aLevel " + entry.getLevel());
        }
        if (topLore.isEmpty()) {
            topLore.add("&7Ingen farmere endnu.");
        }
        set(30, new ItemBuilder(Material.GOLD_INGOT).name("&6&lTop farmere").lore(topLore).build());

        // Hjælp
        set(32, new ItemBuilder(Material.BOOK)
                .name("&f&lHjælp")
                .lore("&7Sådan spiller du HayDay.", "", "&e» Klik for at læse")
                .build(), click -> {
            closeLater();
            plugin.getMessages().sendList(player, "help");
        });

        set(34, closeButton(), click -> closeLater());
        fillEmpty();
    }

    /** Aktive events (dobbelt penge osv.) under profilen. */
    private List<String> eventLore() {
        List<String> lore = new ArrayList<>();
        List<String> events = plugin.getEvents().describe();
        if (!events.isEmpty()) {
            lore.add("");
            lore.add("&6&l★ Events lige nu:");
            lore.addAll(events);
        }
        return lore;
    }

    private ItemStack visitIcon() {
        if (!plugin.getIslands().isEnabled()) {
            return new ItemBuilder(Material.COMPASS)
                    .name("&b&lBesøg andre")
                    .lore("&7Find andre spilleres vejboder", "&7i avisen.", "", "&e» Klik for at åbne")
                    .build();
        }
        Island own = plugin.getIslands().get(player.getUniqueId());
        int farms = plugin.getIslands().count() - (own == null ? 0 : 1);
        return new ItemBuilder(Material.COMPASS)
                .name("&b&lBesøg andre")
                .lore("&7Tag på besøg på andre spilleres",
                        "&7gårde, køb i deres vejbod og",
                        "&7hjælp med deres skib.",
                        "",
                        "&7Gårde: &f" + farms,
                        own == null ? "" : "&7Din gård: &c❤ &f" + own.getLikes().size() + " &7· &f" + own.getVisits() + " &7besøg",
                        "",
                        "&e» Klik for at åbne")
                .build();
    }

    private ItemStack storageIcon(Material material, String name, PlayerData data, ItemCategory category, String what) {
        int used = plugin.getStorage().used(data, category);
        int capacity = plugin.getStorage().capacity(data, category);
        return new ItemBuilder(material)
                .name(name)
                .lore("&7Her ligger dine " + what + ".",
                        "",
                        "&7Fyldt: &f" + used + "&7/&f" + capacity,
                        "&8[" + plugin.getSettings().bar(used / (double) Math.max(1, capacity)) + "&8]",
                        "",
                        "&e» Klik for at åbne og sælge")
                .build();
    }

    private ItemStack shipIcon(PlayerData data) {
        ShipManager ship = plugin.getShip();
        ship.update(player, data);
        List<String> lore = new ArrayList<>();
        boolean glow = false;
        switch (ship.state(data)) {
            case LOCKED:
                lore.add("&cLåses op ved level " + plugin.getSettings().shipLevel);
                break;
            case AWAY:
                lore.add("&7Skibet er ude at sejle.");
                lore.add("&7Tilbage om &f" + Text.timeMillis(ship.timeLeft(data)));
                break;
            default:
                lore.add("&7Skibet er i havn!");
                lore.add("&7Fyldt: &f" + ship.filled(data) + "&7/&f" + data.getShipCrates().size());
                lore.add("&7Sejler om &f" + Text.timeMillis(ship.timeLeft(data)));
                glow = true;
                break;
        }
        lore.add("");
        lore.add("&e» Klik for at åbne");
        return new ItemBuilder(Material.OAK_BOAT).name("&9&lSkibet").lore(lore).glow(glow).build();
    }

    private ItemStack farmOverview(long now) {
        List<Field> fields = plugin.getFarm().getFields(player.getUniqueId());
        int empty = 0;
        int growing = 0;
        int ready = 0;
        for (Field field : fields) {
            if (field.isEmpty()) {
                empty++;
            } else if (field.isReady(now)) {
                ready++;
            } else {
                growing++;
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add("&7Marker: &f" + fields.size() + "&7/&f" + plugin.getService().maxFields(player));
        lore.add("&8• &aKlar: &f" + ready + "  &eGror: &f" + growing + "  &7Tomme: &f" + empty);
        lore.add("");
        List<Building> buildings = plugin.getFarm().getBuildings(player.getUniqueId());
        if (buildings.isEmpty()) {
            lore.add("&7Du har ingen bygninger endnu.");
        } else {
            lore.add("&7Bygninger:");
            for (Building building : buildings) {
                BuildingType type = plugin.getBuildings().get(building.getTypeId());
                if (type == null) {
                    continue;
                }
                if (!type.isProduction()) {
                    lore.add("&8• " + type.getName());
                    continue;
                }
                int done = building.countDone(now);
                String status = done > 0 ? "&a" + done + " klar" : building.getQueue().isEmpty() ? "&7inaktiv" : "&eproducerer";
                lore.add("&8• " + type.getName() + " &8- " + status);
            }
        }
        if (plugin.getIslands().isEnabled()) {
            lore.add("");
            lore.add("&e» Klik for at åbne din gård");
        }
        return new ItemBuilder(Material.GRASS_BLOCK).name("&2&lMin gård").lore(lore).glow(ready > 0).build();
    }
}
