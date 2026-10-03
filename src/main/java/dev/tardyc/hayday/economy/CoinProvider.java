package dev.tardyc.hayday.economy;

import org.bukkit.entity.Player;

/**
 * Penge-system: enten Vault eller HayDays egne mønter.
 */
public interface CoinProvider {

    String getName();

    double getBalance(Player player);

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    void deposit(Player player, double amount);

    String format(double amount);
}
