package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.Recipe;
import dev.tardyc.hayday.util.Sounds;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * XP og levels.
 */
public final class LevelManager {

    private final HayDayPlugin plugin;

    public LevelManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public long xpForNext(int level) {
        return plugin.getSettings().xpForNext(level);
    }

    public boolean isMaxLevel(PlayerData data) {
        return data.getLevel() >= plugin.getSettings().maxLevel;
    }

    public double progress(PlayerData data) {
        if (isMaxLevel(data)) {
            return 1;
        }
        return Math.min(1, data.getXp() / (double) xpForNext(data.getLevel()));
    }

    public void addXp(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerData data = plugin.getPlayers().get(player);
        Settings settings = plugin.getSettings();
        data.setXp(data.getXp() + amount);
        while (data.getLevel() < settings.maxLevel && data.getXp() >= xpForNext(data.getLevel())) {
            data.setXp(data.getXp() - xpForNext(data.getLevel()));
            data.setLevel(data.getLevel() + 1);
            levelUp(player, data);
        }
        plugin.getLeaderboard().update(data);
        plugin.getMessages().actionBar(player, "level.xp-actionbar", "xp", amount, "level", data.getLevel(),
                "bar", settings.bar(progress(data)));
    }

    /** Giver XP uden beskeder og belønninger (til spillere der er offline). */
    public void addXpQuietly(PlayerData data, long amount) {
        if (amount <= 0) {
            return;
        }
        data.setXp(data.getXp() + amount);
        while (data.getLevel() < plugin.getSettings().maxLevel && data.getXp() >= xpForNext(data.getLevel())) {
            data.setXp(data.getXp() - xpForNext(data.getLevel()));
            data.setLevel(data.getLevel() + 1);
        }
        plugin.getOrders().ensure(data);
    }

    public void setLevel(PlayerData data, int level) {
        data.setLevel(Math.max(1, Math.min(plugin.getSettings().maxLevel, level)));
        data.setXp(0);
        plugin.getLeaderboard().update(data);
        plugin.getOrders().ensure(data);
    }

    private void levelUp(Player player, PlayerData data) {
        int level = data.getLevel();
        double reward = level * plugin.getSettings().coinsPerLevel;
        plugin.getEconomy().deposit(player, reward);
        plugin.getMessages().send(player, "level.up-chat", "level", level, "coins", plugin.getEconomy().format(reward));
        player.sendTitle(plugin.getMessages().get("level.up-title", "level", level),
                plugin.getMessages().get("level.up-subtitle", "level", level), 10, 50, 15);
        Sounds.play(player, Sounds.LEVEL_UP);
        plugin.getAnimations().levelUp(player);
        List<String> unlocked = unlockedAt(level);
        if (!unlocked.isEmpty()) {
            plugin.getMessages().send(player, "level.unlocked", "list", String.join("&f, ", unlocked));
        }
        plugin.getOrders().ensure(data);
    }

    /** Navne på alt der låses op på et bestemt level. */
    public List<String> unlockedAt(int level) {
        List<String> names = new ArrayList<>();
        for (FarmItem item : plugin.getItems().crops()) {
            if (item.getCrop().getLevel() == level) {
                names.add(item.getName());
            }
        }
        for (BuildingType type : plugin.getBuildings().all()) {
            if (type.getLevel() == level) {
                names.add(type.getName());
            }
            for (Recipe recipe : type.getRecipes().values()) {
                if (recipe.getLevel() == level && type.getLevel() < level) {
                    FarmItem output = plugin.getItems().get(recipe.getOutputId());
                    names.add(output.getName());
                }
            }
        }
        if (plugin.getSettings().maxFields(level) > plugin.getSettings().maxFields(level - 1)) {
            names.add("&a+1 mark");
        }
        return names;
    }
}
