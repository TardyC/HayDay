package dev.tardyc.hayday.command;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Messages;
import dev.tardyc.hayday.gui.FarmMenu;
import dev.tardyc.hayday.gui.FriendsMenu;
import dev.tardyc.hayday.gui.MainMenu;
import dev.tardyc.hayday.gui.NewspaperMenu;
import dev.tardyc.hayday.gui.OrdersMenu;
import dev.tardyc.hayday.gui.RoadsideMenu;
import dev.tardyc.hayday.gui.ShipMenu;
import dev.tardyc.hayday.gui.ShopMenu;
import dev.tardyc.hayday.gui.StorageMenu;
import dev.tardyc.hayday.gui.VisitMenu;
import dev.tardyc.hayday.hologram.AdminHologramManager;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.island.IslandManager;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.pack.ResourcePackManager;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * /hayday - alle spiller-, admin- og hologram-kommandoer.
 */
public final class HayDayCommand implements TabExecutor {

    private static final Pattern HOLO_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final List<String> PLAYER_SUBS = Arrays.asList("hjælp", "silo", "lade", "ordrer", "butik",
            "vejbod", "avis", "skib", "profil", "top", "pakke", "hjem", "gaard", "besoeg", "torv", "ven", "like",
            "smidud", "forbyd", "tillad");
    /** Underkommandoer hvor 2. argument er et spillernavn. */
    private static final List<String> NAME_SUBS = Arrays.asList("profil", "vejbod", "besoeg", "besøg", "smidud", "forbyd", "tillad");
    private static final List<String> HOLO_SUBS = Arrays.asList("create", "top", "addline", "setline", "removeline",
            "command", "move", "tp", "delete", "list");

    private final HayDayPlugin plugin;
    private final AdminCommand admin;

    public HayDayCommand(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.admin = new AdminCommand(plugin);
    }

    private Messages msg() {
        return plugin.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hayday.use")) {
            msg().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player != null) {
                boolean isNew = !plugin.getPlayers().get(player).isStarted();
                plugin.getService().ensureStarted(player);
                if (isNew && plugin.getSettings().islandTeleportOnStart && plugin.getIslands().get(player.getUniqueId()) != null) {
                    // Nye spillere bliver først sendt til deres ø - menuen åbnes når de er landet
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            new MainMenu(plugin, player).open();
                        }
                    }, 10L);
                } else {
                    new MainMenu(plugin, player).open();
                }
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "hjælp":
            case "hjaelp":
            case "help":
                msg().sendList(sender, "help");
                if (sender.hasPermission("hayday.admin")) {
                    msg().sendList(sender, "admin-help");
                }
                return true;
            case "silo":
                openStorage(sender, ItemCategory.CROP);
                return true;
            case "lade":
                openStorage(sender, ItemCategory.PRODUCT);
                return true;
            case "ordrer":
            case "ordre":
                Player orderPlayer = requirePlayer(sender);
                if (orderPlayer != null) {
                    plugin.getService().ensureStarted(orderPlayer);
                    new OrdersMenu(plugin, orderPlayer).open();
                }
                return true;
            case "butik":
            case "shop":
                Player shopPlayer = requirePlayer(sender);
                if (shopPlayer != null) {
                    plugin.getService().ensureStarted(shopPlayer);
                    new ShopMenu(plugin, shopPlayer).open();
                }
                return true;
            case "vejbod":
            case "roadside":
                roadside(sender, args);
                return true;
            case "avis":
            case "news":
                Player newsPlayer = requirePlayer(sender);
                if (newsPlayer != null) {
                    plugin.getService().ensureStarted(newsPlayer);
                    new NewspaperMenu(plugin, newsPlayer, 0).open();
                }
                return true;
            case "skib":
            case "ship":
                Player shipPlayer = requirePlayer(sender);
                if (shipPlayer != null) {
                    plugin.getService().ensureStarted(shipPlayer);
                    new ShipMenu(plugin, shipPlayer).open();
                }
                return true;
            case "pakke":
            case "pack":
                Player packPlayer = requirePlayer(sender);
                if (packPlayer != null) {
                    if (plugin.getPack().getMode() == ResourcePackManager.Mode.OWN) {
                        plugin.getPack().send(packPlayer);
                        msg().send(packPlayer, "pack.sent");
                    } else {
                        msg().send(packPlayer, "pack.not-own");
                    }
                }
                return true;
            case "profil":
            case "profile":
                profile(sender, args);
                return true;
            case "hjem":
            case "home": {
                Player player = requireIslandPlayer(sender);
                if (player != null) {
                    boolean isNew = !plugin.getPlayers().get(player).isStarted();
                    plugin.getService().ensureStarted(player);
                    // Nye spillere bliver allerede sendt hjem af ensureStarted
                    if (!isNew || !plugin.getSettings().islandTeleportOnStart) {
                        plugin.getIslands().teleportHome(player);
                    }
                }
                return true;
            }
            case "torv":
            case "spawn": {
                Player player = requireIslandPlayer(sender);
                if (player != null) {
                    plugin.getIslands().teleportSpawn(player);
                }
                return true;
            }
            case "besoeg":
            case "besøg":
            case "visit":
                visit(sender, args);
                return true;
            case "gaard":
            case "gård":
            case "oe":
            case "ø":
                farm(sender, args);
                return true;
            case "ven":
            case "venner":
            case "friend":
                friend(sender, args);
                return true;
            case "like":
                like(sender);
                return true;
            case "smidud":
            case "kick":
                kickVisitor(sender, args);
                return true;
            case "forbyd":
            case "tillad":
                ban(sender, args, sub.equals("forbyd"));
                return true;
            case "top":
                top(sender);
                return true;
            case "reload":
                if (requirePermission(sender, "hayday.admin")) {
                    admin.execute(sender, new String[]{"reload"});
                }
                return true;
            case "admin":
                if (requirePermission(sender, "hayday.admin")) {
                    admin.execute(sender, Arrays.copyOfRange(args, 1, args.length));
                }
                return true;
            case "holo":
            case "hologram":
                if (requirePermission(sender, "hayday.hologram")) {
                    holo(sender, Arrays.copyOfRange(args, 1, args.length));
                }
                return true;
            default:
                msg().send(sender, "general.unknown-command");
                return true;
        }
    }

    // ------------------------------------------------------------------
    // Spiller
    // ------------------------------------------------------------------

    private void openStorage(CommandSender sender, ItemCategory category) {
        Player player = requirePlayer(sender);
        if (player != null) {
            plugin.getService().ensureStarted(player);
            new StorageMenu(plugin, player, category).open();
        }
    }

    private void roadside(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.getService().ensureStarted(player);
        if (args.length >= 2 && !args[1].equalsIgnoreCase(player.getName())) {
            Player target = findPlayer(sender, args[1]);
            if (target != null) {
                new RoadsideMenu(plugin, player, target.getUniqueId(), target.getName()).open();
            }
            return;
        }
        new RoadsideMenu(plugin, player, player.getUniqueId(), player.getName()).open();
    }

    private void profile(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg().send(sender, "general.player-not-found", "player", args[1]);
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        PlayerData data = plugin.getPlayers().get(target);
        boolean max = plugin.getLevels().isMaxLevel(data);
        int rank = plugin.getLeaderboard().rank(target.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add("&a&l» Profil: &f" + target.getName() + " &a&l«");
        lines.add("&7Level: &f" + data.getLevel() + (max ? " &6(MAX)" : " &8(&b" + Text.number(data.getXp()) + "&7/&b"
                + Text.number(plugin.getLevels().xpForNext(data.getLevel())) + " XP&8)"));
        lines.add("&8[" + plugin.getSettings().bar(plugin.getLevels().progress(data)) + "&8]");
        lines.add("&7Marker: &f" + plugin.getFarm().countFields(target.getUniqueId()) + "&7/&f"
                + plugin.getSettings().maxFields(data.getLevel())
                + "  &7Bygninger: &f" + plugin.getFarm().getBuildings(target.getUniqueId()).size());
        lines.add("&7Silo: &f" + plugin.getStorage().used(data, ItemCategory.CROP) + "&7/&f" + plugin.getStorage().capacity(data, ItemCategory.CROP)
                + "  &7Lade: &f" + plugin.getStorage().used(data, ItemCategory.PRODUCT) + "&7/&f" + plugin.getStorage().capacity(data, ItemCategory.PRODUCT));
        lines.add("&7Placering: &e" + (rank > 0 ? "#" + rank : "-"));
        for (String line : lines) {
            sender.sendMessage(Text.color(line));
        }
    }

    private void top(CommandSender sender) {
        sender.sendMessage(Text.color("&6&l★ Top HayDay farmere ★"));
        List<LeaderboardManager.Entry> top = plugin.getLeaderboard().top(10);
        if (top.isEmpty()) {
            sender.sendMessage(Text.color("&7Ingen farmere endnu."));
        }
        for (int i = 0; i < top.size(); i++) {
            LeaderboardManager.Entry entry = top.get(i);
            sender.sendMessage(Text.color("&e#" + (i + 1) + " &f" + entry.getName() + " &8- &aLevel " + entry.getLevel()
                    + " &7(" + Text.number(entry.getXp()) + " XP)"));
        }
    }

    // ------------------------------------------------------------------
    // Øer og besøg
    // ------------------------------------------------------------------

    /** En spiller - og øerne skal være slået til. */
    private Player requireIslandPlayer(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null && !plugin.getIslands().isEnabled()) {
            msg().send(player, "island.disabled");
            return null;
        }
        return player;
    }

    /** Spillerens egen ø (laves hvis den mangler). */
    private Island ownIsland(Player player) {
        plugin.getService().ensureStarted(player);
        return plugin.getIslands().getOrCreate(player, 0);
    }

    /** Finder en spiller (online eller med en HayDay-fil) ud fra navnet. Returnerer {uuid, navn} eller null. */
    private Object[] findAny(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Object[]{online.getUniqueId(), online.getName()};
        }
        LeaderboardManager.Entry entry = plugin.getLeaderboard().findByName(name);
        if (entry != null) {
            return new Object[]{entry.getUuid(), entry.getName()};
        }
        msg().send(sender, "general.player-not-found", "player", name);
        return null;
    }

    private void visit(CommandSender sender, String[] args) {
        Player player = requireIslandPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            new VisitMenu(plugin, player, 0).open();
            return;
        }
        Object[] target = findAny(sender, args[1]);
        if (target == null) {
            return;
        }
        Island island = plugin.getIslands().get((UUID) target[0]);
        if (island == null) {
            msg().send(sender, "island.no-island", "player", target[1]);
            return;
        }
        plugin.getIslands().visit(player, island);
    }

    private void farm(CommandSender sender, String[] args) {
        Player player = requireIslandPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.getService().ensureStarted(player);
            new FarmMenu(plugin, player).open();
            return;
        }
        IslandManager islands = plugin.getIslands();
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "navn":
            case "name": {
                String name = args.length > 2 ? Text.strip(Text.color(join(args, 2))).trim() : "";
                if (name.equalsIgnoreCase("nulstil") || name.equalsIgnoreCase("reset")) {
                    islands.setFarmName(island, null);
                } else if (name.isEmpty() || name.length() > 24) {
                    msg().send(player, "island.name-invalid");
                    return;
                } else {
                    islands.setFarmName(island, name);
                }
                msg().send(player, "island.name-set", "farm", islands.farmName(island));
                return;
            }
            case "adgang":
            case "access": {
                Island.Access access = args.length > 2 ? Island.Access.parse(args[2], null) : island.getAccess().next();
                if (access == null) {
                    usage(sender, "/hayday gaard adgang <alle|venner|ingen>");
                    return;
                }
                islands.setAccess(island, access);
                msg().send(player, "island.access-set", "access", islands.accessName(access));
                return;
            }
            case "saethjem":
            case "sæthjem":
            case "sethome":
                if (islands.getAt(player.getLocation()) != island) {
                    msg().send(player, "island.home-not-on-island");
                    return;
                }
                islands.setHome(island, player.getLocation());
                msg().send(player, "island.home-set");
                return;
            default:
                usage(sender, "/hayday gaard [navn <navn> | adgang <alle|venner|ingen> | saethjem]");
        }
    }

    private void friend(CommandSender sender, String[] args) {
        Player player = requireIslandPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.getService().ensureStarted(player);
            new FriendsMenu(plugin, player).open();
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        boolean remove = action.equals("fjern") || action.equals("remove");
        boolean add = action.equals("tilfoej") || action.equals("tilføj") || action.equals("add");
        String name = add || remove ? (args.length > 2 ? args[2] : null) : args[1];
        if (name == null) {
            usage(sender, "/hayday ven <tilfoej|fjern> <spiller>");
            return;
        }
        Object[] target = findAny(sender, name);
        if (target == null) {
            return;
        }
        UUID uuid = (UUID) target[0];
        String targetName = (String) target[1];
        if (uuid.equals(player.getUniqueId())) {
            msg().send(player, "island.friend-self");
            return;
        }
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (remove) {
            msg().send(player, plugin.getIslands().removeFriend(island, uuid) ? "island.friend-removed" : "island.friend-not",
                    "player", targetName);
            return;
        }
        if (!plugin.getIslands().addFriend(island, uuid, targetName)) {
            msg().send(player, "island.friend-already", "player", targetName);
            return;
        }
        msg().send(player, "island.friend-added", "player", targetName);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            msg().send(online, "island.friend-notify", "player", player.getName());
        }
    }

    private void like(CommandSender sender) {
        Player player = requireIslandPlayer(sender);
        if (player == null) {
            return;
        }
        Island island = plugin.getIslands().getAt(player.getLocation());
        if (island == null) {
            msg().send(player, "island.not-on-any");
            return;
        }
        plugin.getIslands().like(player, island);
    }

    private void kickVisitor(CommandSender sender, String[] args) {
        Player player = requireIslandPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            usage(sender, "/hayday smidud <spiller>");
            return;
        }
        Island island = plugin.getIslands().get(player.getUniqueId());
        Player target = findPlayer(sender, args[1]);
        if (island == null || target == null) {
            return;
        }
        if (!plugin.getIslands().visitors(island).contains(target)) {
            msg().send(player, "island.not-on-island", "player", target.getName());
            return;
        }
        if (target.hasPermission("hayday.bypass")) {
            msg().send(player, "general.no-permission");
            return;
        }
        plugin.getIslands().kick(target, island, "island.kicked");
        msg().send(player, "island.kicked-other", "player", target.getName());
    }

    private void ban(CommandSender sender, String[] args, boolean ban) {
        Player player = requireIslandPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            usage(sender, "/hayday " + (ban ? "forbyd" : "tillad") + " <spiller>");
            return;
        }
        Object[] target = findAny(sender, args[1]);
        if (target == null) {
            return;
        }
        UUID uuid = (UUID) target[0];
        if (uuid.equals(player.getUniqueId())) {
            msg().send(player, "island.friend-self");
            return;
        }
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (ban) {
            msg().send(player, plugin.getIslands().ban(island, uuid, (String) target[1]) ? "island.banned-added" : "island.already-banned",
                    "player", target[1]);
        } else {
            msg().send(player, plugin.getIslands().unban(island, uuid) ? "island.banned-removed" : "island.not-banned",
                    "player", target[1]);
        }
    }

    // ------------------------------------------------------------------
    // Hologrammer
    // ------------------------------------------------------------------

    private void holo(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg().sendList(sender, "holo-help");
            return;
        }
        AdminHologramManager holos = plugin.getAdminHolograms();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            msg().send(sender, "holo.list-header", "count", holos.all().size());
            for (AdminHologramManager.Entry entry : holos.all()) {
                msg().send(sender, "holo.list-entry", "name", entry.getName(), "type", entry.getType(), "world", entry.getWorld(),
                        "x", Math.round(entry.getX()), "y", Math.round(entry.getY()), "z", Math.round(entry.getZ()));
            }
            return;
        }
        if (args.length < 2) {
            msg().sendList(sender, "holo-help");
            return;
        }
        String name = args[1];
        switch (sub) {
            case "create":
            case "top": {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (!HOLO_NAME.matcher(name).matches()) {
                    usage(sender, "Navnet må kun indeholde bogstaver, tal, _ og - (max 32 tegn).");
                    return;
                }
                if (holos.exists(name)) {
                    msg().send(sender, "holo.exists", "name", name);
                    return;
                }
                Location location = player.getLocation().add(0, 2.0, 0);
                if (sub.equals("top")) {
                    holos.create(name, location, AdminHologramManager.TYPE_TOP, Collections.<String>emptyList());
                } else {
                    String text = args.length > 2 ? join(args, 2) : "&eNyt hologram &7(" + name + ")";
                    holos.create(name, location, AdminHologramManager.TYPE_TEXT, Collections.singletonList(text));
                }
                msg().send(sender, "holo.created", "name", name);
                return;
            }
            default:
                break;
        }
        AdminHologramManager.Entry entry = holos.get(name);
        if (entry == null) {
            msg().send(sender, "holo.not-found", "name", name);
            return;
        }
        switch (sub) {
            case "addline":
                if (entry.isTop()) {
                    msg().send(sender, "holo.top-no-lines");
                    return;
                }
                entry.getLines().add(args.length > 2 ? join(args, 2) : "");
                holos.update(entry);
                msg().send(sender, "holo.updated", "name", entry.getName());
                return;
            case "setline":
            case "removeline": {
                if (entry.isTop()) {
                    msg().send(sender, "holo.top-no-lines");
                    return;
                }
                if (args.length < 3) {
                    usage(sender, "/hayday holo " + sub + " <navn> <nr>" + (sub.equals("setline") ? " <tekst>" : ""));
                    return;
                }
                Integer line = parseInt(sender, args[2]);
                if (line == null) {
                    return;
                }
                if (line < 1 || line > entry.getLines().size()) {
                    msg().send(sender, "holo.invalid-line", "lines", entry.getLines().size());
                    return;
                }
                if (sub.equals("setline")) {
                    entry.getLines().set(line - 1, args.length > 3 ? join(args, 3) : "");
                } else {
                    entry.getLines().remove(line - 1);
                }
                holos.update(entry);
                msg().send(sender, "holo.updated", "name", entry.getName());
                return;
            }
            case "command": {
                String cmd = args.length > 2 ? join(args, 2) : "none";
                entry.setCommand(cmd.equalsIgnoreCase("none") ? "" : cmd);
                holos.update(entry);
                msg().send(sender, "holo.updated", "name", entry.getName());
                return;
            }
            case "move": {
                Player player = requirePlayer(sender);
                if (player != null) {
                    holos.move(entry, player.getLocation().add(0, 2.0, 0));
                    msg().send(sender, "holo.moved", "name", entry.getName());
                }
                return;
            }
            case "tp": {
                Player player = requirePlayer(sender);
                Location location = entry.getLocation();
                if (player != null && location != null) {
                    player.teleport(location.clone().subtract(0, 2.0, 0));
                    msg().send(sender, "holo.teleported", "name", entry.getName());
                }
                return;
            }
            case "delete":
            case "remove":
                holos.delete(entry);
                msg().send(sender, "holo.deleted", "name", entry.getName());
                return;
            default:
                msg().sendList(sender, "holo-help");
        }
    }

    // ------------------------------------------------------------------
    // Hjælpere
    // ------------------------------------------------------------------

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        msg().send(sender, "general.player-only");
        return null;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        msg().send(sender, "general.no-permission");
        return false;
    }

    private Player findPlayer(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            msg().send(sender, "general.player-not-found", "player", name);
        }
        return player;
    }

    private Integer parseInt(CommandSender sender, String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            msg().send(sender, "general.invalid-number", "input", input);
            return null;
        }
    }

    private Double parseDouble(CommandSender sender, String input) {
        try {
            return Double.parseDouble(input.replace(',', '.'));
        } catch (NumberFormatException e) {
            msg().send(sender, "general.invalid-number", "input", input);
            return null;
        }
    }

    private void usage(CommandSender sender, String usage) {
        sender.sendMessage(msg().prefix() + Text.color("&c" + usage));
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    // ------------------------------------------------------------------
    // Tab-completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (sender.hasPermission("hayday.admin")) {
                options.add("admin");
                options.add("reload");
            }
            if (sender.hasPermission("hayday.hologram")) {
                options.add("holo");
            }
        } else if (NAME_SUBS.contains(args[0].toLowerCase(Locale.ROOT)) && args.length == 2) {
            options.addAll(onlineNames());
        } else if ((args[0].equalsIgnoreCase("gaard") || args[0].equalsIgnoreCase("gård")) && args.length == 2) {
            options.addAll(Arrays.asList("navn", "adgang", "saethjem"));
        } else if ((args[0].equalsIgnoreCase("gaard") || args[0].equalsIgnoreCase("gård")) && args.length == 3
                && args[1].equalsIgnoreCase("adgang")) {
            options.addAll(Arrays.asList("alle", "venner", "ingen"));
        } else if (args[0].equalsIgnoreCase("ven") && args.length == 2) {
            options.addAll(Arrays.asList("tilfoej", "fjern"));
            options.addAll(onlineNames());
        } else if (args[0].equalsIgnoreCase("ven") && args.length == 3 && args[1].equalsIgnoreCase("fjern")) {
            if (sender instanceof Player) {
                Island island = plugin.getIslands().get(((Player) sender).getUniqueId());
                if (island != null) {
                    options.addAll(island.getFriends().values());
                }
            }
        } else if (args[0].equalsIgnoreCase("ven") && args.length == 3) {
            options.addAll(onlineNames());
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("hayday.admin")) {
            options.addAll(admin.complete(args));
        } else if ((args[0].equalsIgnoreCase("holo") || args[0].equalsIgnoreCase("hologram")) && sender.hasPermission("hayday.hologram")) {
            if (args.length == 2) {
                options.addAll(HOLO_SUBS);
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("top")) {
                for (AdminHologramManager.Entry entry : plugin.getAdminHolograms().all()) {
                    options.add(entry.getName());
                }
            } else if (args.length == 4 && args[1].equalsIgnoreCase("command")) {
                options.add("none");
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(last)) {
                result.add(option);
            }
        }
        return result;
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
