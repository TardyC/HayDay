package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.QueueEntry;
import dev.tardyc.hayday.model.Recipe;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Produktionsmenuen for en bygning: kø øverst, opskrifter nederst.
 */
public final class BuildingMenu extends Menu {

    private final Building building;

    public BuildingMenu(HayDayPlugin plugin, Player player, Building building) {
        super(plugin, player, 6, titleFor(plugin, building), "building");
        this.building = building;
    }

    private static String titleFor(HayDayPlugin plugin, Building building) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        return type == null ? "&8Bygning" : type.getName() + " &8» &7Produktion";
    }

    @Override
    public boolean isAutoRefresh() {
        return true;
    }

    @Override
    protected void render() {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (type == null || !plugin.getFarm().getBuildings().contains(building)) {
            closeLater();
            return;
        }
        PlayerData data = plugin.getPlayers().get(player);
        long now = System.currentTimeMillis();
        int done = building.countDone(now);

        fillRow(0);
        set(4, new ItemBuilder(type.getBlock())
                .name(type.getName())
                .lore("&7Ejer: &f" + building.getOwnerName(),
                        "&7Pladser: &f" + building.getSlots() + "&7/&f" + type.getMaxSlots(),
                        "&7I kø: &f" + building.getQueue().size(),
                        "",
                        "&7Vælg en vare nedenfor for at starte",
                        "&7produktionen. Varerne laves én ad gangen.")
                .build());
        if (building.getSlots() < type.getMaxSlots()) {
            set(8, new ItemBuilder(Material.EMERALD)
                    .name("&a&lKøb ekstra plads")
                    .lore("&7Pris: &6" + money(plugin.getService().slotPrice(building, type)),
                            "",
                            "&e» Klik for at købe")
                    .build(), click -> {
                plugin.getService().upgradeSlots(player, building);
                update();
            });
        }

        // Kø (række 2)
        List<QueueEntry> queue = building.getQueue();
        for (int i = 0; i < 9; i++) {
            int slot = 9 + i;
            if (i < queue.size()) {
                set(slot, queueIcon(type, queue.get(i), now), click -> {
                    if (plugin.getService().collect(player, building, false) > 0) {
                        update();
                    }
                });
            } else if (i < building.getSlots()) {
                set(slot, button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Ledig plads", "&8Vælg en vare nedenfor."));
            } else if (i < type.getMaxSlots()) {
                set(slot, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                        .name("&8Låst plads")
                        .lore("&7Pris: &6" + money(plugin.getService().slotPrice(building, type)),
                                "&e» Klik for at købe")
                        .build(), click -> {
                    plugin.getService().upgradeSlots(player, building);
                    update();
                });
            }
        }

        // Skillelinje + hent alle
        fillRow(2);
        if (done > 0) {
            set(22, new ItemBuilder(Material.HOPPER)
                    .name("&a&lHent alle (" + done + ")")
                    .lore("&7Læg de færdige varer i din lade.")
                    .glow(true)
                    .build(), click -> {
                plugin.getService().collect(player, building, false);
                update();
            });
        }

        // Opskrifter (række 4-5)
        int slot = 27;
        for (Recipe recipe : type.getRecipes().values()) {
            if (slot > 44) {
                break;
            }
            set(slot++, recipeIcon(type, recipe, data), click -> {
                if (plugin.getService().produce(player, building, recipe)) {
                    update();
                }
            });
        }

        fillRow(5);
        set(45, backButton(), click -> openLater(new MainMenu(plugin, player)));
        set(49, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private org.bukkit.inventory.ItemStack queueIcon(BuildingType type, QueueEntry entry, long now) {
        Recipe recipe = type.getRecipes().get(entry.getRecipeId());
        FarmItem output = recipe == null ? null : plugin.getItems().get(recipe.getOutputId());
        String name = output == null ? entry.getRecipeId() : output.getName();
        int amount = recipe == null ? 1 : recipe.getOutputAmount();
        if (entry.isDone(now)) {
            ItemBuilder builder = output == null ? new ItemBuilder(Material.CHEST) : output.icon();
            return builder.amount(amount)
                    .name("&a✔ " + amount + "x " + name)
                    .lore("&7Færdig!", "", "&e» Klik for at hente")
                    .glow(true)
                    .build();
        }
        if (entry.isActive(now)) {
            return new ItemBuilder(Material.CLOCK)
                    .name("&eLaver: &f" + amount + "x " + name)
                    .lore("&8[" + plugin.getSettings().bar(entry.getProgress(now)) + "&8]",
                            "&7Færdig om: &f" + Text.timeMillis(entry.getEndTime() - now))
                    .build();
        }
        return new ItemBuilder(Material.PAPER)
                .name("&7I kø: &f" + amount + "x " + name)
                .lore("&7Starter om: &f" + Text.timeMillis(entry.getStartTime() - now),
                        "&7Færdig om: &f" + Text.timeMillis(entry.getEndTime() - now))
                .build();
    }

    private org.bukkit.inventory.ItemStack recipeIcon(BuildingType type, Recipe recipe, PlayerData data) {
        FarmItem output = plugin.getItems().get(recipe.getOutputId());
        int required = Math.max(type.getLevel(), recipe.getLevel());
        if (data.getLevel() < required) {
            return new ItemBuilder(Material.GRAY_DYE)
                    .name("&7" + output.getPlainName() + " &8(låst)")
                    .lore("&cLåses op ved level " + required)
                    .build();
        }
        List<String> lore = new ArrayList<>();
        lore.add("&7Ingredienser:");
        boolean canMake = true;
        for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
            FarmItem ingredient = plugin.getItems().get(entry.getKey());
            int have = data.getAmount(entry.getKey());
            boolean enough = have >= entry.getValue();
            canMake &= enough;
            lore.add((enough ? "&a✔ " : "&c✘ ") + entry.getValue() + "x " + ingredient.getName() + " &7(" + have + ")");
        }
        lore.add("");
        lore.add("&7Tid: &f" + Text.time(recipe.getSeconds()));
        lore.add("&7XP: &b+" + recipe.getXp());
        lore.add("&7I laden: &f" + data.getAmount(output.getId()));
        lore.add("");
        if (building.isQueueFull()) {
            lore.add("&c» Køen er fuld");
        } else if (canMake) {
            lore.add("&e» Klik for at producere");
        } else {
            lore.add("&c» Du mangler ingredienser");
        }
        return output.icon()
                .amount(recipe.getOutputAmount())
                .name(output.getName() + (recipe.getOutputAmount() > 1 ? " &7x" + recipe.getOutputAmount() : ""))
                .lore(lore)
                .glow(canMake && !building.isQueueFull())
                .build();
    }
}
