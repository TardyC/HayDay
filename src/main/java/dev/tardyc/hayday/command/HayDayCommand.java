package dev.tardyc.hayday.command;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Messages;
import dev.tardyc.hayday.gui.MainMenu;
import dev.tardyc.hayday.gui.NewspaperMenu;
import dev.tardyc.hayday.gui.OrdersMenu;
import dev.tardyc.hayday.gui.RoadsideMenu;
import dev.tardyc.hayday.gui.ShopMenu;
import dev.tardyc.hayday.gui.StorageMenu;
import dev.tardyc.hayday.hologram.AdminHologramManager;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.PlaceableItems;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * /hayday - alle spiller-, admin- og hologram-kommandoer.
 */
public final class HayDayCommand implements TabExecutor {

    private static final Pattern HOLO_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final List<String> PLAYER_SUBS = Arrays.asList("hjælp", "silo", "lade", "ordrer", "butik",
            "vejbod", "avis", "profil", "top");
    private static final List<String> ADMIN_SUBS = Arrays.asList("give", "take", "item", "xp", "level", "coins", "reset",
            "info", "fjern", "faerdig", "reload");
    private static final List<String> HOLO_SUBS = Arrays.asList("create", "top", "addline", "setline", "removeline",
            "command", "move", "tp", "delete", "list");

    private final HayDayPlugin plugin;

    public HayDayCommand(HayDayPlugin plugin) {
        this.plugin = plugin;
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
                plugin.getService().ensureStarted(player);
                new MainMenu(plugin, player).open();
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
            case "profil":
            case "profile":
                profile(sender, args);
                return true;
            case "top":
                top(sender);
                return true;
            case "reload":
                if (requirePermission(sender, "hayday.admin")) {
                    reload(sender);
                }
                return true;
            case "admin":
                if (requirePermission(sender, "hayday.admin")) {
                    admin(sender, Arrays.copyOfRange(args, 1, args.length));
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

    private void reload(CommandSender sender) {
        plugin.reload();
        msg().send(sender, "general.reloaded", "items", plugin.getItems().size(), "buildings", plugin.getBuildings().size());
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    private void admin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg().sendList(sender, "admin-help");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give":
            case "take": {
                if (args.length < 4) {
                    usage(sender, "/hayday admin " + sub + " <spiller> <vare> <antal>");
                    return;
                }
                Player target = findPlayer(sender, args[1]);
                FarmItem item = plugin.getItems().get(args[2]);
                Integer amount = parseInt(sender, args[3]);
                if (target == null || amount == null) {
                    return;
                }
                if (item == null) {
                    msg().send(sender, "general.unknown-item", "item", args[2]);
                    return;
                }
                PlayerData data = plugin.getPlayers().get(target);
                if (sub.equals("give")) {
                    data.addItem(item.getId(), amount);
                    msg().send(sender, "admin.storage-given", "amount", amount, "item", item.getName(), "player", target.getName());
                } else {
                    int removed = Math.min(amount, data.getAmount(item.getId()));
                    data.removeItem(item.getId(), removed);
                    msg().send(sender, "admin.storage-taken", "amount", removed, "item", item.getName(), "player", target.getName());
                }
                return;
            }
            case "item": {
                if (args.length < 3) {
                    usage(sender, "/hayday admin item <spiller> <mark|bygning> [antal]");
                    return;
                }
                Player target = findPlayer(sender, args[1]);
                Integer amount = args.length >= 4 ? parseInt(sender, args[3]) : Integer.valueOf(1);
                if (target == null || amount == null) {
                    return;
                }
                ItemStack stack;
                String name;
                if (args[2].equalsIgnoreCase("mark") || args[2].equalsIgnoreCase("field")) {
                    stack = PlaceableItems.field(amount);
                    name = "&a&lMark";
                } else {
                    BuildingType type = plugin.getBuildings().get(args[2]);
                    if (type == null) {
                        msg().send(sender, "general.unknown-building", "building", args[2]);
                        return;
                    }
                    stack = PlaceableItems.building(type, plugin.getItems(), amount);
                    name = type.getName();
                }
                PlaceableItems.give(target, stack);
                msg().send(sender, "admin.item-given", "amount", stack.getAmount(), "item", name, "player", target.getName());
                return;
            }
            case "xp": {
                if (args.length < 3) {
                    usage(sender, "/hayday admin xp <spiller> <antal>");
                    return;
                }
                Player target = findPlayer(sender, args[1]);
                Integer amount = parseInt(sender, args[2]);
                if (target == null || amount == null) {
                    return;
                }
                plugin.getLevels().addXp(target, amount);
                msg().send(sender, "admin.xp-given", "amount", amount, "player", target.getName());
                return;
            }
            case "level": {
                if (args.length < 3) {
                    usage(sender, "/hayday admin level <spiller> <level>");
                    return;
                }
                Player target = findPlayer(sender, args[1]);
                Integer level = parseInt(sender, args[2]);
                if (target == null || level == null) {
                    return;
                }
                PlayerData data = plugin.getPlayers().get(target);
                plugin.getLevels().setLevel(data, level);
                msg().send(sender, "admin.level-set", "player", target.getName(), "level", data.getLevel());
                return;
            }
            case "coins": {
                if (args.length < 3) {
                    usage(sender, "/hayday admin coins <spiller> <antal>");
                    return;
                }
                Player target = findPlayer(sender, args[1]);
                Double amount = parseDouble(sender, args[2]);
                if (target == null || amount == null) {
                    return;
                }
                if (amount >= 0) {
                    plugin.getEconomy().deposit(target, amount);
                } else {
                    plugin.getEconomy().withdraw(target, Math.min(-amount, plugin.getEconomy().getBalance(target)));
                }
                msg().send(sender, "admin.coins-given", "amount", plugin.getEconomy().format(amount), "player", target.getName());
                return;
            }
            case "reset": {
                if (args.length < 2) {
                    usage(sender, "/hayday admin reset <spiller> [confirm]");
                    return;
                }
                Player target = findPlayer(sender, args[1]);
                if (target == null) {
                    return;
                }
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    msg().send(sender, "admin.reset-confirm", "player", target.getName());
                    return;
                }
                PlayerData data = plugin.getPlayers().get(target);
                data.reset();
                data.setCoins(plugin.getSettings().startCoins);
                plugin.getLeaderboard().update(data);
                plugin.getPlayers().save(data);
                msg().send(sender, "admin.reset", "player", target.getName());
                return;
            }
            case "info":
            case "fjern":
            case "remove":
            case "faerdig":
            case "færdig":
            case "finish":
                target(sender, sub);
                return;
            case "reload":
                reload(sender);
                return;
            default:
                msg().sendList(sender, "admin-help");
        }
    }

    private void target(CommandSender sender, String action) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Block block = player.getTargetBlockExact(8);
        Field field = block == null ? null : plugin.getFarm().getFieldAt(block);
        Building building = block == null || field != null ? null : plugin.getFarm().getBuildingAt(block);
        if (field == null && building == null) {
            msg().send(sender, "admin.no-target");
            return;
        }
        long now = System.currentTimeMillis();
        switch (action) {
            case "info":
                if (field != null) {
                    FarmItem crop = plugin.getItems().get(field.getCropId());
                    String status = field.isEmpty() ? "tom" : field.isReady(now) ? "klar"
                            : "gror (" + Text.timeMillis(field.getReadyAt() - now) + ")";
                    msg().send(sender, "admin.info-field", "owner", field.getOwnerName(),
                            "crop", crop == null ? "-" : crop.getName(), "status", status);
                } else {
                    BuildingType type = plugin.getBuildings().get(building.getTypeId());
                    msg().send(sender, "admin.info-building", "building", type == null ? building.getTypeId() : type.getName(),
                            "owner", building.getOwnerName(), "slots", building.getSlots(), "queue", building.getQueue().size());
                }
                return;
            case "faerdig":
            case "færdig":
            case "finish":
                if (field != null) {
                    field.finish(now);
                    plugin.getFarm().refresh(field);
                    msg().send(sender, "admin.finished", "what", "marken");
                } else {
                    building.finishAll(now);
                    plugin.getFarm().refresh(building);
                    msg().send(sender, "admin.finished", "what", "bygningens kø");
                }
                plugin.getFarm().markDirty();
                return;
            default:
                if (field != null) {
                    plugin.getFarm().removeField(field);
                    msg().send(sender, "admin.removed", "what", "marken", "owner", field.getOwnerName());
                } else {
                    plugin.getFarm().removeBuilding(building);
                    msg().send(sender, "admin.removed", "what", "bygningen", "owner", building.getOwnerName());
                }
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
        } else if ((args[0].equalsIgnoreCase("profil") || args[0].equalsIgnoreCase("vejbod")) && args.length == 2) {
            options.addAll(onlineNames());
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("hayday.admin")) {
            options.addAll(adminCompletions(args));
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

    private List<String> adminCompletions(String[] args) {
        if (args.length == 2) {
            return ADMIN_SUBS;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        List<String> withPlayer = Arrays.asList("give", "take", "item", "xp", "level", "coins", "reset");
        if (!withPlayer.contains(sub)) {
            return Collections.emptyList();
        }
        if (args.length == 3) {
            return onlineNames();
        }
        if (args.length == 4) {
            List<String> options = new ArrayList<>();
            if (sub.equals("give") || sub.equals("take")) {
                for (FarmItem item : plugin.getItems().all()) {
                    options.add(item.getId());
                }
            } else if (sub.equals("item")) {
                options.add("mark");
                for (BuildingType type : plugin.getBuildings().all()) {
                    options.add(type.getId());
                }
            } else if (sub.equals("reset")) {
                options.add("confirm");
            }
            return options;
        }
        return Collections.emptyList();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
