package dev.tardyc.hayday.hook;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.manager.LeaderboardManager;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI: %hayday_level%, %hayday_xp%, %hayday_top_name_1% osv. Indlæses kun hvis PlaceholderAPI findes.
 */
public final class HayDayExpansion extends PlaceholderExpansion {

    private final HayDayPlugin plugin;

    public HayDayExpansion(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "hayday";
    }

    @Override
    public String getAuthor() {
        return "TardyC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String key = params.toLowerCase(Locale.ROOT);

        // Top-lister virker også uden spiller: top_name_1, top_level_1, top_xp_1
        if (key.startsWith("top_")) {
            String[] parts = key.split("_");
            if (parts.length != 3) {
                return "";
            }
            int rank;
            try {
                rank = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "";
            }
            List<LeaderboardManager.Entry> top = plugin.getLeaderboard().top(rank);
            if (rank < 1 || rank > top.size()) {
                return parts[1].equals("name") ? "---" : "0";
            }
            LeaderboardManager.Entry entry = top.get(rank - 1);
            switch (parts[1]) {
                case "name":
                    return entry.getName();
                case "level":
                    return String.valueOf(entry.getLevel());
                case "xp":
                    return String.valueOf(entry.getXp());
                default:
                    return "";
            }
        }

        if (offline == null || !offline.isOnline() || offline.getPlayer() == null) {
            return "";
        }
        Player player = offline.getPlayer();
        PlayerData data = plugin.getPlayers().get(player);
        switch (key) {
            case "level":
                return String.valueOf(data.getLevel());
            case "xp":
                return String.valueOf(data.getXp());
            case "xp_needed":
                return String.valueOf(plugin.getLevels().xpForNext(data.getLevel()));
            case "xp_progress":
                return String.valueOf((int) Math.floor(plugin.getLevels().progress(data) * 100));
            case "coins":
                return plugin.getEconomy().format(plugin.getEconomy().getBalance(player));
            case "fields":
                return String.valueOf(plugin.getFarm().countFields(player.getUniqueId()));
            case "fields_max":
                return String.valueOf(plugin.getSettings().maxFields(data.getLevel()));
            case "fields_ready":
                return String.valueOf(plugin.getService().readyCounts(player.getUniqueId())[0]);
            case "products_ready":
                return String.valueOf(plugin.getService().readyCounts(player.getUniqueId())[1]);
            case "silo_used":
                return String.valueOf(plugin.getStorage().used(data, ItemCategory.CROP));
            case "silo_capacity":
                return String.valueOf(plugin.getStorage().capacity(data, ItemCategory.CROP));
            case "barn_used":
                return String.valueOf(plugin.getStorage().used(data, ItemCategory.PRODUCT));
            case "barn_capacity":
                return String.valueOf(plugin.getStorage().capacity(data, ItemCategory.PRODUCT));
            case "orders_ready":
                return String.valueOf(plugin.getOrders().countReady(data));
            case "ship_state":
                switch (plugin.getShip().state(data)) {
                    case LOCKED:
                        return "låst";
                    case AWAY:
                        return "ude at sejle";
                    default:
                        return "i havn";
                }
            case "ship_time":
                return Text.timeMillis(plugin.getShip().timeLeft(data));
            case "ship_filled":
                return plugin.getShip().filled(data) + "/" + data.getShipCrates().size();
            case "rank":
                return String.valueOf(plugin.getLeaderboard().rank(player.getUniqueId()));
            default:
                return null;
        }
    }
}
