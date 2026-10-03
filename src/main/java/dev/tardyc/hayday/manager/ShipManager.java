package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.ItemCategory;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.ShipCrate;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skibet: lægger til kaj med kasser der skal fyldes. Hver kasse giver penge og XP, og sender man skibet
 * afsted med alle kasser fyldt, får man en bonus. Bagefter er skibet væk et stykke tid.
 */
public final class ShipManager {

    /** Hvor skibet er. */
    public enum State {
        LOCKED,
        AWAY,
        DOCKED
    }

    private final HayDayPlugin plugin;

    public ShipManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public State state(PlayerData data) {
        Settings settings = plugin.getSettings();
        if (!settings.shipEnabled || data.getLevel() < settings.shipLevel) {
            return State.LOCKED;
        }
        return data.getShipCrates().isEmpty() ? State.AWAY : State.DOCKED;
    }

    /** Millisekunder til skibet sejler (i havn) eller kommer tilbage (væk). */
    public long timeLeft(PlayerData data) {
        long now = System.currentTimeMillis();
        return Math.max(0, (data.getShipCrates().isEmpty() ? data.getShipArrivesAt() : data.getShipLeavesAt()) - now);
    }

    public int filled(PlayerData data) {
        int filled = 0;
        for (ShipCrate crate : data.getShipCrates()) {
            if (crate.isFilled()) {
                filled++;
            }
        }
        return filled;
    }

    public boolean allFilled(PlayerData data) {
        return !data.getShipCrates().isEmpty() && filled(data) == data.getShipCrates().size();
    }

    /** Kører skibets "køreplan": ankomst og afgang. Kaldes hvert sekund for online spillere. */
    public void update(Player player, PlayerData data) {
        Settings settings = plugin.getSettings();
        if (state(data) == State.LOCKED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (data.getShipCrates().isEmpty()) {
            if (now >= data.getShipArrivesAt()) {
                arrive(data, now);
                if (player != null) {
                    plugin.getMessages().send(player, "ship.arrived", "time", Text.timeMillis(settings.shipStayTime));
                    Sounds.play(player, "block.bell.use", 1.2f);
                }
            }
        } else if (now >= data.getShipLeavesAt()) {
            int filled = filled(data);
            int total = data.getShipCrates().size();
            depart(data, now);
            if (player != null) {
                plugin.getMessages().send(player, "ship.departed", "filled", filled, "total", total);
            }
        }
    }

    private void arrive(PlayerData data, long now) {
        Settings settings = plugin.getSettings();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<FarmItem> candidates = new ArrayList<>();
        for (FarmItem item : plugin.getItems().all()) {
            if (item.getUnlockLevel() <= data.getLevel()) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) {
            data.setShipArrivesAt(now + settings.shipAwayTime);
            return;
        }
        Collections.shuffle(candidates, random);
        // Skibet vil helst have produkter, ligesom i Hay Day
        candidates.sort((a, b) -> Boolean.compare(a.getCategory() == ItemCategory.CROP, b.getCategory() == ItemCategory.CROP));
        data.getShipCrates().clear();
        for (int type = 0; type < settings.shipTypes && type < candidates.size(); type++) {
            FarmItem item = candidates.get(type);
            int amount = item.getCategory() == ItemCategory.CROP
                    ? random.nextInt(settings.shipCropMin, settings.shipCropMax + 1)
                    : random.nextInt(settings.shipProductMin, settings.shipProductMax + 1);
            double coins = Math.max(1, Math.ceil(item.getSellPrice() * amount * settings.shipRewardMultiplier));
            int xp = (int) Math.max(1, Math.ceil(item.getXp() * amount * settings.shipXpMultiplier));
            for (int i = 0; i < settings.shipCratesPerType; i++) {
                data.getShipCrates().add(new ShipCrate(item.getId(), amount, coins, xp, false));
            }
        }
        data.setShipLeavesAt(now + settings.shipStayTime);
        data.setDirty(true);
    }

    private void depart(PlayerData data, long now) {
        data.getShipCrates().clear();
        data.setShipArrivesAt(now + plugin.getSettings().shipAwayTime);
        data.setShipLeavesAt(0);
        data.setDirty(true);
    }

    /** Admin: skibet lægger til kaj med det samme (nye kasser). */
    public void forceArrive(PlayerData data) {
        arrive(data, System.currentTimeMillis());
    }

    /** Admin: skibet sejler med det samme (uden bonus). */
    public void forceDepart(PlayerData data) {
        depart(data, System.currentTimeMillis());
    }

    /** Ejeren fylder selv en kasse. */
    public void fill(Player player, int index) {
        fill(player, plugin.getPlayers().get(player), index);
    }

    /**
     * Fylder en kasse på {@code owner}s skib med {@code player}s egne varer. Er det en gæst der hjælper,
     * får gæsten belønningen og ejeren får kassen fyldt - ligesom at hjælpe en nabo i Hay Day.
     */
    public void fill(Player player, PlayerData owner, int index) {
        PlayerData filler = plugin.getPlayers().get(player);
        boolean helping = !owner.getUuid().equals(player.getUniqueId());
        if (state(owner) != State.DOCKED || index < 0 || index >= owner.getShipCrates().size()) {
            return;
        }
        ShipCrate crate = owner.getShipCrates().get(index);
        FarmItem item = plugin.getItems().get(crate.getItemId());
        if (crate.isFilled() || item == null) {
            plugin.getMessages().send(player, "ship.already-filled");
            return;
        }
        if (!filler.removeItem(item.getId(), crate.getAmount())) {
            int missing = crate.getAmount() - filler.getAmount(item.getId());
            plugin.getMessages().send(player, "orders.missing", "missing", missing + "x " + item.getName());
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        crate.setFilled(true);
        crate.setHelper(helping ? player.getName() : null);
        owner.setDirty(true);
        plugin.getEconomy().deposit(player, crate.getCoins());
        String coins = plugin.getEconomy().format(crate.getCoins());
        if (helping) {
            plugin.getMessages().send(player, "island.helped-ship", "owner", owner.getName(), "coins", coins, "xp", crate.getXp());
        } else {
            plugin.getMessages().send(player, "ship.filled", "amount", crate.getAmount(), "item", item.getName(),
                    "coins", coins, "xp", crate.getXp());
        }
        Sounds.play(player, Sounds.PLACE, 1.2f);
        plugin.getAnimations().floatingText(player.getLocation().add(0, 2.3, 0), "&6+" + coins + " &b+" + crate.getXp() + " XP");
        plugin.getLevels().addXp(player, crate.getXp());

        Player ownerPlayer = Bukkit.getPlayer(owner.getUuid());
        if (helping) {
            if (ownerPlayer != null) {
                plugin.getMessages().send(ownerPlayer, "island.helped-ship-notify", "player", player.getName(),
                        "amount", crate.getAmount(), "item", item.getName());
                Sounds.play(ownerPlayer, Sounds.SUCCESS, 1.3f);
            } else {
                plugin.getPlayers().save(owner);
            }
        }
        if (allFilled(owner) && ownerPlayer != null) {
            plugin.getMessages().send(ownerPlayer, "ship.all-filled");
            Sounds.play(ownerPlayer, Sounds.LEVEL_UP, 1.6f);
        }
    }

    public double bonusCoins(PlayerData data) {
        double total = 0;
        for (ShipCrate crate : data.getShipCrates()) {
            total += crate.getCoins();
        }
        return Math.ceil(total * plugin.getSettings().shipBonusPercent / 100.0);
    }

    public int bonusXp(PlayerData data) {
        int total = 0;
        for (ShipCrate crate : data.getShipCrates()) {
            total += crate.getXp();
        }
        return (int) Math.ceil(total * plugin.getSettings().shipBonusPercent / 100.0);
    }

    /** Sender skibet afsted med alle kasser fyldt og giver bonussen. */
    public void send(Player player) {
        PlayerData data = plugin.getPlayers().get(player);
        if (!allFilled(data)) {
            plugin.getMessages().send(player, "ship.not-all-filled");
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        double coins = bonusCoins(data);
        int xp = bonusXp(data);
        depart(data, System.currentTimeMillis());
        plugin.getEconomy().deposit(player, coins);
        plugin.getMessages().send(player, "ship.sent", "coins", plugin.getEconomy().format(coins), "xp", xp);
        plugin.getAnimations().coinBurst(player);
        plugin.getAnimations().levelUp(player);
        plugin.getLevels().addXp(player, xp);
    }

    /** Opdaterer skibet for alle online spillere. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayers().getIfLoaded(player.getUniqueId());
            if (data != null && data.isStarted()) {
                update(player, data);
            }
        }
    }
}
