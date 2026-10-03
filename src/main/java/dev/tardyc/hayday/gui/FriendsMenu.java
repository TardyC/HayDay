package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.island.IslandManager;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dine venner på HayDay. Klik på en ven for at besøge vedkommende, shift-klik for at fjerne vennen.
 */
public final class FriendsMenu extends Menu {

    public FriendsMenu(HayDayPlugin plugin, Player player) {
        super(plugin, player, 4, "&d&lVenner", null);
    }

    @Override
    protected void render() {
        IslandManager islands = plugin.getIslands();
        Island island = islands.get(player.getUniqueId());
        List<Map.Entry<UUID, String>> friends = island == null ? new ArrayList<>() : new ArrayList<>(island.getFriends().entrySet());
        for (int i = 0; i < friends.size() && i < 27; i++) {
            UUID uuid = friends.get(i).getKey();
            String name = friends.get(i).getValue();
            Island theirs = islands.get(uuid);
            boolean online = Bukkit.getPlayer(uuid) != null;
            set(i, new ItemBuilder(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(uuid))
                    .name("&d&l" + name)
                    .lore(online ? "&a● online" : "&8● offline",
                            theirs == null ? "&7Har ingen gård endnu." : "&7Gård: &f" + islands.farmName(theirs),
                            "",
                            theirs == null ? "" : "&e» Klik for at besøge",
                            "&c» Shift-klik for at fjerne vennen")
                    .build(), click -> {
                if (click.isShiftClick()) {
                    if (island != null && islands.removeFriend(island, uuid)) {
                        plugin.getMessages().send(player, "island.friend-removed", "player", name);
                        Sounds.play(player, Sounds.CLICK);
                    }
                    update();
                    return;
                }
                if (theirs != null) {
                    closeLater();
                    Bukkit.getScheduler().runTask(plugin, () -> islands.visit(player, theirs));
                }
            });
        }
        if (friends.isEmpty()) {
            set(13, new ItemBuilder(Material.COBWEB)
                    .name("&7Du har ingen venner endnu")
                    .lore("&7Tilføj en ven med", "&f/hayday ven tilfoej <spiller>")
                    .build());
        }
        fillRow(3);
        set(27, backButton(), click -> openLater(new FarmMenu(plugin, player)));
        set(31, new ItemBuilder(Material.BOOK)
                .name("&d&lVenner")
                .lore("&7Venner kan altid besøge din gård,",
                        "&7også når den kun er åben for venner.",
                        "",
                        "&f/hayday ven tilfoej <spiller>",
                        "&f/hayday ven fjern <spiller>")
                .build());
        set(35, closeButton(), click -> closeLater());
        fillEmpty();
    }
}
