package dev.tardyc.hayday.util;

import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Recipe;
import dev.tardyc.hayday.registry.ItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Items som spillere placerer i verdenen: marker og bygninger. Genkendes via et PersistentDataContainer-tag.
 */
public final class PlaceableItems {

    public static final String FIELD_TAG = "field";
    public static final String BUILDING_PREFIX = "building:";

    private PlaceableItems() {
    }

    public static ItemStack field(int amount) {
        ItemBuilder builder = new ItemBuilder(Material.FARMLAND)
                .name("&a&lMark")
                .lore("&7Placér den på jorden for at lave",
                        "&7en ny mark til din HayDay-gård.",
                        "",
                        "&eHøjreklik på marken for at plante.",
                        "&8Shift + slå på en tom mark for at samle den op.")
                .glow(true);
        return tag(builder, FIELD_TAG, amount);
    }

    public static ItemStack building(BuildingType type, ItemRegistry items, int amount) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Placér den for at bygge din");
        lore.add(type.getName() + "&7.");
        lore.add("");
        if (type.isRoadside()) {
            lore.add("&7Sælg dine varer til andre spillere.");
        } else if (type.isHarbor()) {
            lore.add("&7Her lægger skibet til kaj.");
        } else {
            lore.add("&7Producerer:");
            for (Recipe recipe : type.getRecipes().values()) {
                FarmItem output = items.get(recipe.getOutputId());
                lore.add("&8• &f" + (output == null ? recipe.getOutputId() : output.getName()));
            }
        }
        lore.add("");
        lore.add("&8Shift + slå på bygningen for at samle den op.");
        ItemBuilder builder = new ItemBuilder(type.getBlock()).name(type.getName()).lore(lore).glow(true);
        return tag(builder, BUILDING_PREFIX + type.getId(), amount);
    }

    private static ItemStack tag(ItemBuilder builder, String tag, int amount) {
        ItemMeta meta = builder.meta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, tag);
        }
        ItemStack item = builder.build();
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }

    /** Tag'et på et item ("field" / "building:<id>"), eller null hvis det ikke er et HayDay-item. */
    public static String getTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public static boolean isField(ItemStack item) {
        return FIELD_TAG.equals(getTag(item));
    }

    public static String getBuildingId(ItemStack item) {
        String tag = getTag(item);
        return tag != null && tag.startsWith(BUILDING_PREFIX) ? tag.substring(BUILDING_PREFIX.length()) : null;
    }

    /** Antal items med et bestemt tag i spillerens inventory. */
    public static int count(Player player, String tag) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (tag.equals(getTag(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Giver et item, og smider resten på jorden hvis inventory er fuldt. Returnerer true hvis noget blev smidt. */
    public static boolean give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        return !leftover.isEmpty();
    }
}
