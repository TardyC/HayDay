package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * En spillers vejbod. Ejeren sætter varer til salg og henter penge; besøgende kan købe.
 */
public final class RoadsideMenu extends Menu {

    /** Kasse-pladserne i menuen (2 rækker á 7). */
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final UUID owner;
    private final String ownerName;

    public RoadsideMenu(HayDayPlugin plugin, Player player, UUID owner, String ownerName) {
        super(plugin, player, 4, player.getUniqueId().equals(owner) ? "&6&lMin vejbod" : "&6&l" + ownerName + "s vejbod", "roadside");
        this.owner = owner;
        this.ownerName = ownerName;
    }

    private boolean isOwner() {
        return player.getUniqueId().equals(owner);
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void render() {
        fillRow(0);
        if (isOwner()) {
            renderOwner();
        } else {
            renderVisitor();
        }
        fillRow(3);
        set(27, backButton(), click -> openLater(new MainMenu(plugin, player)));
        set(31, new ItemBuilder(Material.MAP)
                .name("&f&lAvisen")
                .lore("&7Se hvad andre sælger.", "", "&e» Klik for at læse")
                .build(), click -> openLater(new NewspaperMenu(plugin, player, 0)));
        set(35, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private void renderOwner() {
        PlayerData data = plugin.getPlayers().get(player);
        int slots = plugin.getMarket().slots(data);
        set(4, new ItemBuilder(Material.OAK_HANGING_SIGN)
                .name("&6&lMin vejbod")
                .lore("&7Sæt varer fra dit lager til salg.",
                        "&7Andre spillere kan købe dem her",
                        "&7eller finde dem i &fAvisen&7.",
                        "",
                        "&7Pladser: &f" + slots + "&7/&f" + plugin.getSettings().marketMaxSlots)
                .build());
        for (int i = 0; i < SLOTS.length; i++) {
            int slot = i;
            if (i >= plugin.getSettings().marketMaxSlots) {
                break;
            }
            if (i >= slots) {
                if (i == slots) {
                    set(SLOTS[i], new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                            .name("&8Låst kasse")
                            .lore("&7Pris: &6" + money(plugin.getMarket().slotPrice(data)), "", "&e» Klik for at købe")
                            .build(), click -> {
                        plugin.getMarket().buySlot(player);
                        update();
                    });
                }
                continue;
            }
            Listing listing = plugin.getMarket().getAt(owner, i);
            if (listing == null) {
                set(SLOTS[i], button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Tom kasse", "&e» Klik for at sætte en vare til salg"),
                        click -> openLater(new ListingMenu(plugin, player, slot)));
                continue;
            }
            set(SLOTS[i], ownerIcon(listing), click -> ownerClick(listing, click));
        }
    }

    private ItemStack ownerIcon(Listing listing) {
        FarmItem item = plugin.getItems().get(listing.getItemId());
        ItemBuilder builder = item == null ? new ItemBuilder(Material.CHEST) : item.icon();
        String name = item == null ? listing.getItemId() : item.getName();
        switch (listing.getState()) {
            case SOLD:
                return new ItemBuilder(Material.SUNFLOWER)
                        .name("&a&lSolgt! &6" + money(listing.getPrice()))
                        .lore("&f" + listing.getAmount() + "x " + name + " &7blev købt af &f" + listing.getBuyerName(),
                                "",
                                "&e» Klik for at hente pengene")
                        .glow(true)
                        .build();
            case EXPIRED:
                return builder.amount(listing.getAmount())
                        .name("&c" + listing.getAmount() + "x " + Text.strip(name) + " &7(udløbet)")
                        .lore("&7Ingen købte varen i tide.", "", "&e» Klik for at tage varerne tilbage")
                        .build();
            default:
                return builder.amount(listing.getAmount())
                        .name("&f" + listing.getAmount() + "x " + name)
                        .lore("&7Pris: &6" + money(listing.getPrice()),
                                "&7Sat til salg for &f" + Text.timeMillis(System.currentTimeMillis() - listing.getCreatedAt()) + " &7siden",
                                "",
                                "&cShift + klik for at tage den af hylden")
                        .build();
        }
    }

    private void ownerClick(Listing listing, ClickType click) {
        switch (listing.getState()) {
            case SOLD:
            case EXPIRED:
                plugin.getMarket().collect(player, listing);
                break;
            default:
                if (click.isShiftClick()) {
                    plugin.getMarket().returnItems(player, listing);
                }
                break;
        }
        update();
    }

    private void renderVisitor() {
        set(4, new ItemBuilder(Material.OAK_HANGING_SIGN)
                .name("&6&l" + ownerName + "s vejbod")
                .lore("&7Klik på en vare for at købe den.",
                        "&7Varen havner direkte i dit lager.",
                        "",
                        "&7Dine penge: &6" + money(plugin.getEconomy().getBalance(player)))
                .build());
        int index = 0;
        for (Listing listing : plugin.getMarket().getListings(owner)) {
            if (index >= SLOTS.length) {
                break;
            }
            FarmItem item = plugin.getItems().get(listing.getItemId());
            if (item == null) {
                continue;
            }
            if (listing.isActive()) {
                set(SLOTS[index++], item.icon()
                        .amount(listing.getAmount())
                        .name("&f" + listing.getAmount() + "x " + item.getName())
                        .lore("&7Pris: &6" + money(listing.getPrice()),
                                "&7Du har: &f" + plugin.getPlayers().get(player).getAmount(item.getId()),
                                "",
                                "&e» Klik for at købe")
                        .build(), click -> {
                    plugin.getMarket().buy(player, listing);
                    update();
                });
            } else {
                set(SLOTS[index++], new ItemBuilder(Material.GRAY_DYE)
                        .name("&8Udsolgt")
                        .lore("&7" + listing.getAmount() + "x " + item.getPlainName())
                        .build());
            }
        }
    }
}
