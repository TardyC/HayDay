package dev.tardyc.hayday.economy;

import dev.tardyc.hayday.HayDayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Vælger penge-system. Vault slås op igen ved første brug, hvis økonomi-pluginet starter efter HayDay.
 * Klassen refererer ikke direkte til Vault, så den virker også uden Vault installeret.
 */
public final class EconomyManager {

    private final HayDayPlugin plugin;
    private final InternalCoinProvider internal;
    private CoinProvider provider;

    public EconomyManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        this.internal = new InternalCoinProvider(plugin);
    }

    /** Nulstiller valget ud fra config.yml. Selve Vault-opslaget sker først når der er brug for det. */
    public void setup() {
        provider = null;
        if (!plugin.getSettings().economyType.equals("vault")) {
            provider = internal;
            plugin.getLogger().info("Bruger økonomi: " + internal.getName());
        } else if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("economy.type er 'vault', men Vault er ikke installeret - bruger HayDay-mønter.");
            provider = internal;
        }
    }

    /** Finder økonomien nu (kaldes når serveren er færdig med at starte). */
    public void resolve() {
        provider();
    }

    private CoinProvider provider() {
        if (provider == null) {
            CoinProvider vault = VaultCoinProvider.lookup();
            if (vault != null) {
                provider = vault;
                plugin.getLogger().info("Bruger økonomi: " + provider.getName());
            } else {
                plugin.getLogger().warning("Vault fandt ingen økonomi (mangler fx EssentialsX?) - bruger HayDay-mønter.");
                provider = internal;
            }
        }
        return provider;
    }

    public boolean isInternal() {
        return provider() == internal;
    }

    public String getProviderName() {
        return provider().getName();
    }

    public double getBalance(Player player) {
        return provider().getBalance(player);
    }

    public boolean has(Player player, double amount) {
        return amount <= 0 || provider().has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        return amount <= 0 || provider().withdraw(player, amount);
    }

    public void deposit(Player player, double amount) {
        provider().deposit(player, amount);
    }

    public String format(double amount) {
        return provider().format(amount);
    }

    /** Trækker penge og sender en fejlbesked hvis spilleren ikke har råd. */
    public boolean charge(Player player, double price) {
        if (price <= 0) {
            return true;
        }
        if (!has(player, price) || !withdraw(player, price)) {
            plugin.getMessages().send(player, "general.not-enough-money",
                    "price", format(price), "balance", format(getBalance(player)));
            return false;
        }
        return true;
    }
}
