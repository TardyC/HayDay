package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.manager.ShipManager;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.ShipCrate;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Skibets kasser.
 *
 * <pre>
 *  . . . . I . . . .     I = status
 *  . V . K K K . B .     V = vare, K = kasser, B = belønning   (én række pr. vare)
 *  . V . K K K . B .
 *  . V . K K K . B .
 *  « . . . S . . . X     S = send skibet afsted
 * </pre>
 */
public final class ShipMenu extends Menu {

    private final UUID owner;
    private final String ownerName;

    public ShipMenu(HayDayPlugin plugin, Player player) {
        this(plugin, player, player.getUniqueId(), player.getName());
    }

    /** En andens skib: gæsten kan fylde kasserne med sine egne varer og få belønningen. */
    public ShipMenu(HayDayPlugin plugin, Player player, UUID owner, String ownerName) {
        super(plugin, player, 5, owner.equals(player.getUniqueId()) ? "&9&lSkibet" : "&9&l" + ownerName + "s skib", "ship");
        this.owner = owner;
        this.ownerName = ownerName;
    }

    private boolean helping() {
        return !owner.equals(player.getUniqueId());
    }

    /** Ejerens data - for en offline ejer læses filen frisk hver gang (og gemmes når en kasse fyldes). */
    private PlayerData ownerData() {
        return helping() ? plugin.getPlayers().getOrLoad(owner, ownerName) : plugin.getPlayers().get(player);
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void render() {
        ShipManager ship = plugin.getShip();
        PlayerData data = ownerData();
        Player ownerPlayer = helping() ? Bukkit.getPlayer(owner) : player;
        ship.update(ownerPlayer, data);
        if (ownerPlayer == null && data.isDirty()) {
            plugin.getPlayers().save(data);
        }
        ShipManager.State state = ship.state(data);
        String time = Text.timeMillis(ship.timeLeft(data));

        fillRow(0);
        switch (state) {
            case LOCKED:
                set(4, new ItemBuilder(Material.OAK_BOAT)
                        .name("&9&lSkibet")
                        .lore("&cLåses op ved level " + plugin.getSettings().shipLevel)
                        .build());
                break;
            case AWAY:
                set(4, new ItemBuilder(Material.OAK_BOAT)
                        .name("&9&lSkibet er ude at sejle")
                        .lore("&7Det er tilbage om &f" + time, "", "&7Imens kan du lave varer klar!")
                        .build());
                break;
            default:
                set(4, new ItemBuilder(Material.OAK_BOAT)
                        .name("&9&lSkibet er i havn")
                        .lore(helping() ? "&7Hjælp &f" + ownerName + " &7med at fylde kasserne." : "&7Fyld kasserne med varer.",
                                helping() ? "&7Du bruger dine egne varer og får belønningen." : "&7Hver kasse giver penge og XP med det samme.",
                                "",
                                "&7Fyldt: &f" + ship.filled(data) + "&7/&f" + data.getShipCrates().size(),
                                "&7Sejler om: &f" + time)
                        .glow(ship.allFilled(data))
                        .build());
                renderCrates(data);
                break;
        }

        fillRow(4);
        if (helping()) {
            set(36, backButton(), click -> closeLater());
        } else {
            set(36, backButton(), click -> openLater(new MainMenu(plugin, player)));
        }
        if (helping()) {
            if (state == ShipManager.State.DOCKED) {
                set(40, button(Material.GRAY_DYE, "&7Kun " + ownerName + " kan sende skibet afsted"));
            }
        } else if (state == ShipManager.State.DOCKED && ship.allFilled(data)) {
            set(40, new ItemBuilder(Material.LIME_CONCRETE)
                    .name("&a&lSend skibet afsted!")
                    .lore("&7Bonus: &6+" + money(ship.bonusCoins(data)) + " &b+" + ship.bonusXp(data) + " XP")
                    .glow(true)
                    .build(), click -> {
                ship.send(player);
                update();
            });
        } else if (state == ShipManager.State.DOCKED) {
            set(40, button(Material.GRAY_DYE, "&7Send skibet afsted",
                    "&7Fyld alle kasser først.",
                    "&7Bonus: &6+" + money(ship.bonusCoins(data)) + " &b+" + ship.bonusXp(data) + " XP"));
        }
        set(44, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private void renderCrates(PlayerData data) {
        List<ShipCrate> crates = data.getShipCrates();
        int perType = plugin.getSettings().shipCratesPerType;
        for (int i = 0; i < crates.size(); i++) {
            int type = i / perType;
            int column = i % perType;
            if (type > 2 || column > 2) {
                continue;
            }
            int row = type + 1;
            ShipCrate crate = crates.get(i);
            FarmItem item = plugin.getItems().get(crate.getItemId());
            if (item == null) {
                continue;
            }
            int have = plugin.getPlayers().get(player).getAmount(item.getId());
            if (column == 0) {
                set(row * 9 + 1, item.icon(player)
                        .amount(crate.getAmount())
                        .lore("&7Hver kasse skal have &f" + crate.getAmount() + "x",
                                "&7Du har: " + (have >= crate.getAmount() ? "&a" : "&c") + have)
                        .build());
                set(row * 9 + 7, new ItemBuilder(Material.GOLD_NUGGET)
                        .name("&6Belønning pr. kasse")
                        .lore("&6+" + money(crate.getCoins()), "&b+" + crate.getXp() + " XP")
                        .build());
            }
            int index = i;
            int slot = row * 9 + 3 + column;
            if (crate.isFilled()) {
                set(slot, new ItemBuilder(Material.BARREL)
                        .name("&a✔ Kassen er fyldt")
                        .lore("&7" + crate.getAmount() + "x " + item.getPlainName(),
                                crate.getHelper() == null ? "" : "&dHjulpet af &f" + crate.getHelper())
                        .glow(true)
                        .build());
            } else {
                set(slot, item.icon(player)
                        .amount(crate.getAmount())
                        .name("&eTom kasse: &f" + crate.getAmount() + "x " + item.getName())
                        .lore("&7Du har: " + (have >= crate.getAmount() ? "&a" : "&c") + have,
                                "&7Belønning: &6+" + money(crate.getCoins()) + " &b+" + crate.getXp() + " XP",
                                "",
                                have >= crate.getAmount() ? "&e» Klik for at fylde kassen" : "&c» Du mangler varer")
                        .build(), click -> {
                    plugin.getShip().fill(player, ownerData(), index);
                    update();
                });
            }
        }
    }
}
