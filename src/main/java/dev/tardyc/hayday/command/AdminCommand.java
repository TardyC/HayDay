package dev.tardyc.hayday.command;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Messages;
import dev.tardyc.hayday.events.EventManager;
import dev.tardyc.hayday.events.EventType;
import dev.tardyc.hayday.island.Island;
import dev.tardyc.hayday.island.IslandLayout;
import dev.tardyc.hayday.island.IslandManager;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.manager.ShipManager;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.pack.ResourcePackManager;
import dev.tardyc.hayday.util.PlaceableItems;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /hayday admin - de fleste kommandoer virker også på spillere der er offline (deres fil indlæses og gemmes).
 */
final class AdminCommand {

    static final List<String> SUBS = Arrays.asList("spiller", "lager", "give", "take", "item", "xp", "level", "coins",
            "skib", "ordrer", "faerdigalle", "tp", "oe", "fjernalt", "reset", "info", "fjern", "faerdig", "pakke", "event", "torv", "reload");
    private static final List<String> WITH_PLAYER = Arrays.asList("spiller", "lager", "give", "take", "item", "xp", "level",
            "coins", "skib", "ordrer", "faerdigalle", "tp", "oe", "fjernalt", "reset");

    private final HayDayPlugin plugin;

    AdminCommand(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.getMessages();
    }

    /** En spiller som admin-kommandoen virker på - online eller offline. */
    private static final class Target {
        private final UUID uuid;
        private final String name;
        private final Player online;
        private final PlayerData data;

        Target(UUID uuid, String name, Player online, PlayerData data) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
            this.data = data;
        }
    }

    private Target resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName(), online, plugin.getPlayers().get(online));
        }
        LeaderboardManager.Entry entry = plugin.getLeaderboard().findByName(name);
        if (entry == null || !plugin.getPlayers().hasFile(entry.getUuid())) {
            msg().send(sender, "general.player-not-found", "player", name);
            return null;
        }
        return new Target(entry.getUuid(), entry.getName(), null, plugin.getPlayers().getOrLoad(entry.getUuid(), entry.getName()));
    }

    /** Gemmer en offline spillers fil efter en ændring. */
    private void finish(CommandSender sender, Target target) {
        plugin.getLeaderboard().update(target.data);
        if (target.online == null) {
            plugin.getPlayers().save(target.data);
            msg().send(sender, "admin.offline-note", "player", target.name);
        }
    }

    void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg().sendList(sender, "admin-help");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info":
            case "fjern":
            case "remove":
            case "faerdig":
            case "færdig":
            case "finish":
                lookingAt(sender, sub);
                return;
            case "reload":
                plugin.reload();
                msg().send(sender, "general.reloaded", "items", plugin.getItems().size(), "buildings", plugin.getBuildings().size());
                return;
            case "pakke":
            case "pack":
                pack(sender, args);
                return;
            case "event":
            case "events":
                event(sender, args);
                return;
            case "torv":
                if (!plugin.getIslands().isEnabled()) {
                    msg().send(sender, "island.disabled");
                    return;
                }
                plugin.getIslands().rebuildSpawn();
                msg().send(sender, "admin.spawn-rebuilt");
                return;
            default:
                break;
        }
        if (!WITH_PLAYER.contains(sub)) {
            msg().sendList(sender, "admin-help");
            return;
        }
        if (args.length < 2) {
            usage(sender, "/hayday admin " + sub + " <spiller> ...");
            return;
        }
        Target target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }
        switch (sub) {
            case "spiller":
                info(sender, target);
                return;
            case "lager":
                storage(sender, target);
                return;
            case "give":
            case "take":
                giveTake(sender, target, sub, args);
                return;
            case "item":
                item(sender, target, args);
                return;
            case "xp":
                xp(sender, target, args);
                return;
            case "level":
                level(sender, target, args);
                return;
            case "coins":
                coins(sender, target, args);
                return;
            case "skib":
                ship(sender, target, args);
                return;
            case "ordrer":
                target.data.getOrders().clear();
                plugin.getOrders().ensure(target.data);
                msg().send(sender, "admin.orders-reset", "player", target.name);
                finish(sender, target);
                return;
            case "faerdigalle":
                finishAll(sender, target);
                return;
            case "tp":
                teleport(sender, target);
                return;
            case "oe":
                island(sender, target, args);
                return;
            case "fjernalt":
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    msg().send(sender, "admin.removed-all-confirm", "player", target.name);
                    return;
                }
                int[] removed = removeFarm(target.uuid);
                msg().send(sender, "admin.removed-all", "player", target.name, "fields", removed[0], "buildings", removed[1]);
                return;
            case "reset":
                reset(sender, target, args);
                return;
            default:
                msg().sendList(sender, "admin-help");
        }
    }

    // ------------------------------------------------------------------
    // Kommandoerne
    // ------------------------------------------------------------------

    private void info(CommandSender sender, Target target) {
        PlayerData data = target.data;
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ROOT);
        long now = System.currentTimeMillis();
        List<Field> fields = plugin.getFarm().getFields(target.uuid);
        int ready = 0;
        for (Field field : fields) {
            if (field.isReady(now)) {
                ready++;
            }
        }
        List<String> buildingNames = new ArrayList<>();
        for (Building building : plugin.getFarm().getBuildings(target.uuid)) {
            BuildingType type = plugin.getBuildings().get(building.getTypeId());
            buildingNames.add(type == null ? building.getTypeId() : type.getPlainName());
        }
        String money;
        if (target.online != null) {
            money = plugin.getEconomy().format(plugin.getEconomy().getBalance(target.online));
        } else {
            money = plugin.getEconomy().isInternal() ? plugin.getEconomy().format(data.getCoins()) : "&8(offline)";
        }
        ShipManager.State ship = plugin.getShip().state(data);
        String shipText = ship == ShipManager.State.LOCKED ? "låst" : ship == ShipManager.State.AWAY
                ? "ude at sejle (" + Text.timeMillis(plugin.getShip().timeLeft(data)) + ")"
                : "i havn - " + plugin.getShip().filled(data) + "/" + data.getShipCrates().size() + " kasser";
        List<String> lines = new ArrayList<>();
        lines.add("&a&l» HayDay: &f" + target.name + " " + (target.online != null ? "&a(online)" : "&7(offline)"));
        lines.add("&7UUID: &f" + target.uuid);
        lines.add("&7Level: &f" + data.getLevel() + " &8(&b" + data.getXp() + "&7/&b" + plugin.getLevels().xpForNext(data.getLevel())
                + " XP&8)  &7Penge: &6" + money + "  &7Placering: &e#" + plugin.getLeaderboard().rank(target.uuid));
        lines.add("&7Første login: &f" + (data.getFirstJoin() > 0 ? format.format(new Date(data.getFirstJoin())) : "-")
                + "  &7Sidst set: &f" + (target.online != null ? "nu" : data.getLastSeen() > 0 ? format.format(new Date(data.getLastSeen())) : "-"));
        lines.add("&7Silo: &f" + plugin.getStorage().used(data, ItemCategory.CROP) + "/" + plugin.getStorage().capacity(data, ItemCategory.CROP)
                + "  &7Lade: &f" + plugin.getStorage().used(data, ItemCategory.PRODUCT) + "/" + plugin.getStorage().capacity(data, ItemCategory.PRODUCT));
        lines.add("&7Marker: &f" + fields.size() + "/" + plugin.getSettings().maxFields(data.getLevel()) + " &8(&a" + ready + " klar&8)"
                + "  &7Bygninger: &f" + (buildingNames.isEmpty() ? "ingen" : String.join(", ", buildingNames)));
        lines.add("&7Ordrer klar: &f" + plugin.getOrders().countReady(data) + "/" + data.getOrders().size() + "  &7Skib: &f" + shipText);
        lines.add("&7Vejbod: &f" + plugin.getMarket().count(target.uuid, Listing.State.ACTIVE) + " til salg, "
                + plugin.getMarket().count(target.uuid, Listing.State.SOLD) + " solgt");
        lines.add("&8Fil: plugins/HayDay/players/" + target.uuid + ".yml");
        for (String line : lines) {
            sender.sendMessage(Text.color(line));
        }
    }

    private void storage(CommandSender sender, Target target) {
        sender.sendMessage(Text.color("&a&l» Lager: &f" + target.name));
        for (ItemCategory category : ItemCategory.values()) {
            List<String> parts = new ArrayList<>();
            for (FarmItem item : plugin.getItems().byCategory(category)) {
                int amount = target.data.getAmount(item.getId());
                if (amount > 0) {
                    parts.add("&f" + amount + "x " + item.getName());
                }
            }
            sender.sendMessage(Text.color("&7" + plugin.getStorage().name(category) + " &8("
                    + plugin.getStorage().used(target.data, category) + "/" + plugin.getStorage().capacity(target.data, category) + ")&7: "
                    + (parts.isEmpty() ? "&8tom" : String.join("&7, ", parts))));
        }
    }

    private void giveTake(CommandSender sender, Target target, String sub, String[] args) {
        if (args.length < 4) {
            usage(sender, "/hayday admin " + sub + " <spiller> <vare> <antal>");
            return;
        }
        FarmItem item = plugin.getItems().get(args[2]);
        Integer amount = parseInt(sender, args[3]);
        if (amount == null) {
            return;
        }
        if (item == null) {
            msg().send(sender, "general.unknown-item", "item", args[2]);
            return;
        }
        if (sub.equals("give")) {
            target.data.addItem(item.getId(), amount);
            msg().send(sender, "admin.storage-given", "amount", amount, "item", item.getName(), "player", target.name);
        } else {
            int removed = Math.min(amount, target.data.getAmount(item.getId()));
            target.data.removeItem(item.getId(), removed);
            msg().send(sender, "admin.storage-taken", "amount", removed, "item", item.getName(), "player", target.name);
        }
        finish(sender, target);
    }

    private void item(CommandSender sender, Target target, String[] args) {
        if (target.online == null) {
            msg().send(sender, "admin.must-be-online", "player", target.name);
            return;
        }
        if (args.length < 3) {
            usage(sender, "/hayday admin item <spiller> <mark|bygning> [antal]");
            return;
        }
        Integer amount = args.length >= 4 ? parseInt(sender, args[3]) : Integer.valueOf(1);
        if (amount == null) {
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
        PlaceableItems.give(target.online, stack);
        msg().send(sender, "admin.item-given", "amount", stack.getAmount(), "item", name, "player", target.name);
    }

    private void xp(CommandSender sender, Target target, String[] args) {
        Integer amount = args.length >= 3 ? parseInt(sender, args[2]) : null;
        if (amount == null) {
            usage(sender, "/hayday admin xp <spiller> <antal>");
            return;
        }
        if (target.online != null) {
            plugin.getLevels().addXp(target.online, amount);
        } else {
            plugin.getLevels().addXpQuietly(target.data, amount);
        }
        msg().send(sender, "admin.xp-given", "amount", amount, "player", target.name);
        finish(sender, target);
    }

    private void level(CommandSender sender, Target target, String[] args) {
        Integer level = args.length >= 3 ? parseInt(sender, args[2]) : null;
        if (level == null) {
            usage(sender, "/hayday admin level <spiller> <level>");
            return;
        }
        plugin.getLevels().setLevel(target.data, level);
        msg().send(sender, "admin.level-set", "player", target.name, "level", target.data.getLevel());
        finish(sender, target);
    }

    private void coins(CommandSender sender, Target target, String[] args) {
        Double amount = args.length >= 3 ? parseDouble(sender, args[2]) : null;
        if (amount == null) {
            usage(sender, "/hayday admin coins <spiller> <antal>");
            return;
        }
        if (target.online != null) {
            if (amount >= 0) {
                plugin.getEconomy().deposit(target.online, amount);
            } else {
                plugin.getEconomy().withdraw(target.online, Math.min(-amount, plugin.getEconomy().getBalance(target.online)));
            }
        } else if (plugin.getEconomy().isInternal()) {
            target.data.setCoins(target.data.getCoins() + amount);
        } else {
            msg().send(sender, "admin.must-be-online", "player", target.name);
            return;
        }
        msg().send(sender, "admin.coins-given", "amount", plugin.getEconomy().format(amount), "player", target.name);
        finish(sender, target);
    }

    private void ship(CommandSender sender, Target target, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        if (action.equals("ankom") || action.equals("arrive")) {
            plugin.getShip().forceArrive(target.data);
            msg().send(sender, "admin.ship", "player", target.name, "state", "skibet er lagt til kaj");
        } else if (action.equals("afsted") || action.equals("depart")) {
            plugin.getShip().forceDepart(target.data);
            msg().send(sender, "admin.ship", "player", target.name, "state", "skibet er sejlet");
        } else {
            usage(sender, "/hayday admin skib <spiller> <ankom|afsted>");
            return;
        }
        finish(sender, target);
    }

    private void finishAll(CommandSender sender, Target target) {
        long now = System.currentTimeMillis();
        List<Field> fields = plugin.getFarm().getFields(target.uuid);
        List<Building> buildings = plugin.getFarm().getBuildings(target.uuid);
        for (Field field : fields) {
            field.finish(now);
            plugin.getFarm().refresh(field);
        }
        for (Building building : buildings) {
            building.finishAll(now);
            plugin.getFarm().refresh(building);
        }
        plugin.getFarm().markDirty();
        msg().send(sender, "admin.finished-all", "player", target.name, "fields", fields.size(), "buildings", buildings.size());
    }

    private void teleport(CommandSender sender, Target target) {
        if (!(sender instanceof Player)) {
            msg().send(sender, "general.player-only");
            return;
        }
        Location location = null;
        List<Field> fields = plugin.getFarm().getFields(target.uuid);
        List<Building> buildings = plugin.getFarm().getBuildings(target.uuid);
        Island island = plugin.getIslands().get(target.uuid);
        if (island != null) {
            location = plugin.getIslands().home(island);
        } else if (!fields.isEmpty() && fields.get(0).getSoil().getWorld() != null) {
            location = fields.get(0).getSoil().toCenter().add(0, 1, 0);
        } else if (!buildings.isEmpty() && buildings.get(0).getPos().getWorld() != null) {
            location = buildings.get(0).getPos().toCenter().add(1.5, 0, 0);
        }
        if (location == null) {
            msg().send(sender, "admin.no-farm", "player", target.name);
            return;
        }
        ((Player) sender).teleport(location);
        msg().send(sender, "admin.teleported", "player", target.name);
    }

    private void island(CommandSender sender, Target target, String[] args) {
        IslandManager islands = plugin.getIslands();
        if (!islands.isEnabled()) {
            msg().send(sender, "island.disabled");
            return;
        }
        Island island = islands.get(target.uuid);
        if (island == null) {
            msg().send(sender, "island.no-island", "player", target.name);
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "info";
        boolean confirmed = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
        switch (action) {
            case "tp":
                if (!(sender instanceof Player)) {
                    msg().send(sender, "general.player-only");
                    return;
                }
                ((Player) sender).teleport(islands.home(island));
                msg().send(sender, "admin.teleported", "player", target.name);
                return;
            case "nulstil":
            case "reset":
                if (!confirmed) {
                    msg().send(sender, "admin.island-reset-confirm", "player", target.name);
                    return;
                }
                msg().send(sender, "admin.island-working", "player", target.name);
                islands.reset(island, false, () -> msg().send(sender, "admin.island-reset", "player", target.name));
                return;
            case "slet":
            case "delete":
                if (!confirmed) {
                    msg().send(sender, "admin.island-delete-confirm", "player", target.name);
                    return;
                }
                msg().send(sender, "admin.island-working", "player", target.name);
                islands.reset(island, true, () -> msg().send(sender, "admin.island-deleted", "player", target.name));
                return;
            default:
                IslandLayout layout = islands.getLayout();
                SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ROOT);
                List<String> lines = new ArrayList<>();
                lines.add("&a&l» Ø: &f" + islands.farmName(island) + " &7(" + target.name + ")");
                lines.add("&7Plads: &f" + island.getGridX() + ", " + island.getGridZ() + " &8(x " + layout.minX(island.getGridX())
                        + ".." + layout.maxX(island.getGridX()) + ", z " + layout.minZ(island.getGridZ()) + ".."
                        + layout.maxZ(island.getGridZ()) + ")");
                lines.add("&7Adgang: " + islands.accessName(island.getAccess()) + "  &c❤ &f" + island.getLikes().size()
                        + "  &7Besøg: &f" + island.getVisits());
                lines.add("&7Venner: &f" + (island.getFriends().isEmpty() ? "ingen" : String.join(", ", island.getFriends().values())));
                lines.add("&7Forbudte: &f" + (island.getBanned().isEmpty() ? "ingen" : String.join(", ", island.getBanned().values())));
                lines.add("&7Oprettet: &f" + (island.getCreated() > 0 ? format.format(new Date(island.getCreated())) : "-")
                        + "  &7Besøgende nu: &f" + islands.visitors(island).size());
                for (String line : lines) {
                    sender.sendMessage(Text.color(line));
                }
        }
    }

    private int[] removeFarm(UUID uuid) {
        List<Field> fields = plugin.getFarm().getFields(uuid);
        List<Building> buildings = plugin.getFarm().getBuildings(uuid);
        for (Field field : fields) {
            plugin.getFarm().removeField(field);
        }
        for (Building building : buildings) {
            plugin.getFarm().removeBuilding(building);
        }
        return new int[]{fields.size(), buildings.size()};
    }

    private void reset(CommandSender sender, Target target, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            msg().send(sender, "admin.reset-confirm", "player", target.name);
            return;
        }
        removeFarm(target.uuid);
        plugin.getMarket().removeAll(target.uuid);
        Island island = plugin.getIslands().get(target.uuid);
        if (island != null) {
            plugin.getIslands().reset(island, true, () -> { });
        }
        target.data.reset();
        target.data.setCoins(plugin.getSettings().startCoins);
        plugin.getPlayers().save(target.data);
        plugin.getLeaderboard().update(target.data);
        msg().send(sender, "admin.reset", "player", target.name);
    }

    private void pack(CommandSender sender, String[] args) {
        ResourcePackManager pack = plugin.getPack();
        boolean itemsAdder = pack.getMode() == ResourcePackManager.Mode.ITEMSADDER;
        if (args.length >= 2 && args[1].equalsIgnoreCase("send")) {
            if (itemsAdder) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iatexture all");
                msg().send(sender, "admin.pack-sent", "count", Bukkit.getOnlinePlayers().size());
                return;
            }
            int sent = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                pack.send(player);
                sent++;
            }
            msg().send(sender, "admin.pack-sent", "count", sent);
            return;
        }
        if (itemsAdder) {
            msg().sendList(sender, "admin.pack-status-itemsadder");
            for (String line : plugin.getItemsAdder().packDiagnostics()) {
                sender.sendMessage(Text.color(line));
            }
            return;
        }
        msg().sendList(sender, "admin.pack-status", "mode", pack.getMode().name(),
                "url", pack.getMode() == ResourcePackManager.Mode.OWN ? pack.url() : "-",
                "loaded", pack.getLoaded().size(), "online", Bukkit.getOnlinePlayers().size(),
                "required", plugin.getSettings().packRequired ? "ja" : "nej");
        for (String line : ownPackDiagnostics(pack)) {
            sender.sendMessage(Text.color(line));
        }
    }

    /** /hayday admin event start <type|alle> [minutter] [gange] | stop <type|alle> | liste */
    private void event(CommandSender sender, String[] args) {
        EventManager events = plugin.getEvents();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "liste";
        switch (action) {
            case "start": {
                if (args.length < 3) {
                    usage(sender, "/hayday admin event start <penge|xp|vaekst|hoest|produktion|alle> [minutter] [gange]");
                    return;
                }
                List<EventType> types = new ArrayList<>();
                if (args[2].equalsIgnoreCase("alle") || args[2].equalsIgnoreCase("all")) {
                    types.addAll(Arrays.asList(EventType.values()));
                } else {
                    EventType type = EventType.parse(args[2]);
                    if (type == null) {
                        msg().send(sender, "events.unknown", "type", args[2]);
                        return;
                    }
                    types.add(type);
                }
                Integer minutes = args.length >= 4 ? parseInt(sender, args[3]) : Integer.valueOf(plugin.getSettings().eventDefaultMinutes);
                Double multiplier = args.length >= 5 ? parseDouble(sender, args[4]) : Double.valueOf(plugin.getSettings().eventDefaultMultiplier);
                if (minutes == null || multiplier == null) {
                    return;
                }
                if (minutes < 1 || multiplier <= 0 || multiplier > 100) {
                    usage(sender, "Minutter skal være mindst 1 og gange mellem 0.1 og 100.");
                    return;
                }
                for (EventType type : types) {
                    events.start(type, multiplier, minutes * 60_000L);
                    msg().send(sender, "events.admin-started", "name", events.name(type),
                            "x", EventManager.formatMultiplier(multiplier), "time", Text.time(minutes * 60L));
                }
                return;
            }
            case "stop": {
                if (args.length < 3 || args[2].equalsIgnoreCase("alle") || args[2].equalsIgnoreCase("all")) {
                    msg().send(sender, "events.admin-stopped-all", "count", events.stopAll());
                    return;
                }
                EventType type = EventType.parse(args[2]);
                if (type == null) {
                    msg().send(sender, "events.unknown", "type", args[2]);
                    return;
                }
                msg().send(sender, events.stop(type, true) ? "events.admin-stopped" : "events.not-active", "name", events.name(type));
                return;
            }
            default: {
                List<String> lines = events.describe();
                if (lines.isEmpty()) {
                    msg().send(sender, "events.none");
                } else {
                    msg().send(sender, "events.list-header");
                    for (String line : lines) {
                        sender.sendMessage(line);
                    }
                }
                msg().sendList(sender, "events.admin-help");
            }
        }
    }

    /** Fejlfinding af HayDays egen pakke: lokal adresse, forkert mc-packs-link og ItemsAdder der ikke bruges. */
    private List<String> ownPackDiagnostics(ResourcePackManager pack) {
        List<String> lines = new ArrayList<>();
        if (plugin.getItemsAdder().getProblem() != null) {
            lines.add("&eItemsAdder er installeret, men bruges ikke: &f" + plugin.getItemsAdder().getProblem());
        }
        if (pack.getMode() != ResourcePackManager.Mode.OWN) {
            return lines;
        }
        String sha = pack.sha1();
        lines.add("&7Pakken: &fplugins/HayDay/HayDay-resourcepack.zip &8(SHA-1 " + (sha.length() >= 12 ? sha.substring(0, 12) : sha) + "...)");
        String url = plugin.getSettings().packUrl;
        if (url.isEmpty()) {
            if (pack.isLocalOnly()) {
                lines.add("&c✘ Adressen er lokal - kun spillere på selve server-computeren kan hente pakken.");
                lines.add("&e→ Upload plugins/HayDay/HayDay-resourcepack.zip til mc-packs.net, skriv linket under");
                lines.add("&e   resource-pack.url i plugins/HayDay/config.yml og kør /hayday admin reload.");
                lines.add("&e→ Eller skriv serverens IP under resource-pack.host.address og åbn port "
                        + plugin.getSettings().packHostPort + " hos din host.");
            } else {
                lines.add("&7Pakken hostes af HayDay selv - husk at port " + plugin.getSettings().packHostPort + " skal være åben.");
            }
            return lines;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[0-9a-fA-F]{40}").matcher(url);
        if (matcher.find()) {
            if (matcher.group().equalsIgnoreCase(sha)) {
                lines.add("&a✔ Linket passer med pakken.");
            } else {
                lines.add("&c✘ Linket peger på en ANDEN pakke end den HayDay har nu (fx efter en opdatering).");
                lines.add("&e→ Upload den nye plugins/HayDay/HayDay-resourcepack.zip og skift linket i resource-pack.url.");
            }
        }
        return lines;
    }

    private void lookingAt(CommandSender sender, String action) {
        if (!(sender instanceof Player)) {
            msg().send(sender, "general.player-only");
            return;
        }
        Player player = (Player) sender;
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
    // Tab-completion og hjælpere
    // ------------------------------------------------------------------

    List<String> complete(String[] args) {
        if (args.length == 2) {
            return SUBS;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("pakke")) {
            return args.length == 3 ? Collections.singletonList("send") : Collections.<String>emptyList();
        }
        if (sub.equals("event")) {
            if (args.length == 3) {
                return Arrays.asList("start", "stop", "liste");
            }
            if (args.length == 4 && (args[2].equalsIgnoreCase("start") || args[2].equalsIgnoreCase("stop"))) {
                List<String> types = new ArrayList<>();
                for (EventType type : EventType.values()) {
                    types.add(type.id());
                }
                types.add("alle");
                return types;
            }
            if (args.length == 5 && args[2].equalsIgnoreCase("start")) {
                return Arrays.asList("15", "30", "60", "120");
            }
            if (args.length == 6 && args[2].equalsIgnoreCase("start")) {
                return Arrays.asList("1.5", "2", "3");
            }
            return Collections.emptyList();
        }
        if (!WITH_PLAYER.contains(sub)) {
            return Collections.emptyList();
        }
        if (args.length == 3) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            for (String name : plugin.getLeaderboard().names()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
            return names;
        }
        if (args.length == 5 && sub.equals("oe")) {
            return Collections.singletonList("confirm");
        }
        if (args.length == 4) {
            List<String> options = new ArrayList<>();
            switch (sub) {
                case "give":
                case "take":
                    for (FarmItem item : plugin.getItems().all()) {
                        options.add(item.getId());
                    }
                    break;
                case "item":
                    options.add("mark");
                    for (BuildingType type : plugin.getBuildings().all()) {
                        options.add(type.getId());
                    }
                    break;
                case "skib":
                    options.add("ankom");
                    options.add("afsted");
                    break;
                case "reset":
                case "fjernalt":
                    options.add("confirm");
                    break;
                case "oe":
                    options.addAll(Arrays.asList("info", "tp", "nulstil", "slet"));
                    break;
                default:
                    break;
            }
            return options;
        }
        return Collections.emptyList();
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
}
