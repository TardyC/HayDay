package dev.tardyc.hayday.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Bruger serverens økonomi via Vault. Klassen indlæses kun hvis Vault er installeret.
 */
public final class VaultCoinProvider implements CoinProvider {

    private final Economy economy;

    private VaultCoinProvider(Economy economy) {
        this.economy = economy;
    }

    /** Finder Vault-økonomien, eller null hvis intet økonomi-plugin er registreret endnu. */
    public static CoinProvider lookup() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            return null;
        }
        return new VaultCoinProvider(rsp.getProvider());
    }

    @Override
    public String getName() {
        return "Vault (" + economy.getName() + ")";
    }

    @Override
    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0) {
            return true;
        }
        return economy.has(player, amount) && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void deposit(Player player, double amount) {
        if (amount > 0) {
            economy.depositPlayer(player, amount);
        }
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
