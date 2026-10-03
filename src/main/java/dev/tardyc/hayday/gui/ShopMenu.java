package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.Recipe;
import dev.tardyc.hayday.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Butikken: køb marker og bygninger.
 */
public final class ShopMenu extends Menu {

    public ShopMenu(HayDayPlugin plugin, Player player) {
        super(plugin, player, 5, "&a&lButik &8» &7Marker og bygninger", "shop");
    }

    @Override
    protected void render() {
        PlayerData data = plugin.getPlayers().get(player);
        fillRow(0);
        set(4, new ItemBuilder(Material.EMERALD)
                .name("&a&lButik")
                .lore("&7Din saldo: &6" + money(plugin.getEconomy().getBalance(player)),
                        "&7Dit level: &f" + data.getLevel())
                .build());

        List<Integer> slots = innerSlots(1, 3);
        int index = 0;

        // Mark
        int owned = plugin.getService().ownedFields(player);
        int max = plugin.getService().maxFields(player);
        boolean canBuyField = owned < max;
        set(slots.get(index++), new ItemBuilder(Material.FARMLAND)
                .name("&a&lMark")
                .lore("&7En mark du kan plante afgrøder på.",
                        "",
                        "&7Pris: &6" + money(plugin.getService().fieldPrice(player)),
                        "&7Dine marker: &f" + owned + "&7/&f" + max,
                        "",
                        canBuyField ? "&e» Klik for at købe" : "&c» Stig i level for flere marker")
                .glow(canBuyField)
                .build(), click -> {
            plugin.getService().buyField(player);
            update();
        });

        // Bygninger
        for (BuildingType type : plugin.getBuildings().all()) {
            if (index >= slots.size()) {
                break;
            }
            set(slots.get(index++), buildingIcon(type, data), click -> {
                plugin.getService().buyBuilding(player, type);
                update();
            });
        }

        fillRow(4);
        set(36, backButton(), click -> openLater(new MainMenu(plugin, player)));
        set(40, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private org.bukkit.inventory.ItemStack buildingIcon(BuildingType type, PlayerData data) {
        boolean locked = data.getLevel() < type.getLevel();
        int owned = plugin.getService().ownedBuildings(player, type);
        boolean maxed = owned >= type.getMaxPerPlayer();
        List<String> lore = new ArrayList<>();
        lore.add("&7Pris: &6" + money(type.getPrice()));
        lore.add("&7Kræver level: " + (locked ? "&c" : "&a") + type.getLevel());
        if (type.getMaxPerPlayer() > 1) {
            lore.add("&7Ejer: &f" + owned + "&7/&f" + type.getMaxPerPlayer());
        }
        lore.add("");
        if (type.isRoadside()) {
            lore.add("&7Sæt varer til salg, som andre");
            lore.add("&7spillere kan købe - også når du er offline.");
        } else {
            lore.add("&7Producerer:");
            for (Recipe recipe : type.getRecipes().values()) {
                FarmItem output = plugin.getItems().get(recipe.getOutputId());
                lore.add("&8• " + output.getName() + " &8(lvl " + Math.max(type.getLevel(), recipe.getLevel()) + ")");
            }
        }
        lore.add("");
        if (locked) {
            lore.add("&c» Låst indtil level " + type.getLevel());
        } else if (maxed) {
            lore.add("&a✔ Du ejer denne bygning");
        } else {
            lore.add("&e» Klik for at købe");
        }
        return new ItemBuilder(locked ? Material.GRAY_DYE : type.getBlock())
                .name(type.getName())
                .lore(lore)
                .glow(!locked && !maxed)
                .build();
    }
}
