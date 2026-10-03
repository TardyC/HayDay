package dev.tardyc.hayday.economy;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.Text;
import org.bukkit.entity.Player;

/**
 * HayDays egne mønter, gemt i spillerens data.
 */
public final class InternalCoinProvider implements CoinProvider {

    private final HayDayPlugin plugin;

    public InternalCoinProvider(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "HayDay-mønter";
    }

    @Override
    public double getBalance(Player player) {
        return plugin.getPlayers().get(player).getCoins();
    }

    @Override
    public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        PlayerData data = plugin.getPlayers().get(player);
        if (amount < 0 || data.getCoins() < amount) {
            return false;
        }
        data.setCoins(data.getCoins() - amount);
        return true;
    }

    @Override
    public void deposit(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        PlayerData data = plugin.getPlayers().get(player);
        data.setCoins(data.getCoins() + amount);
    }

    @Override
    public String format(double amount) {
        return Text.number(amount) + " " + plugin.getSettings().currencyName;
    }
}
