package dev.tardyc.hayday.model;

import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;

/**
 * En vare i HayDay (afgrøde eller produkt). Varer er virtuelle og ligger i spillerens silo/lade.
 */
public final class FarmItem {

    /** Finder ItemsAdder-items (sættes af pluginet; returnerer null hvis ItemsAdder ikke er tilgængelig). */
    private static Function<String, ItemStack> customItems = id -> null;

    private final String id;
    private final String name;
    private final Material icon;
    private final Color color;
    private final ItemCategory category;
    private final double sellPrice;
    private final int xp;
    private final CropType crop;
    private String itemsAdderId;
    /** Level hvor varen kan skaffes (sættes når bygningerne er indlæst). */
    private int unlockLevel;
    private ItemStack displayStack;

    public FarmItem(String id, String name, Material icon, Color color, ItemCategory category, double sellPrice, int xp, CropType crop) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.category = category;
        this.sellPrice = sellPrice;
        this.xp = xp;
        this.crop = crop;
        this.unlockLevel = crop != null ? crop.getLevel() : Integer.MAX_VALUE;
    }

    public String getId() {
        return id;
    }

    /** Navnet med farvekoder. */
    public String getName() {
        return Text.color(name);
    }

    public String getPlainName() {
        return Text.strip(name);
    }

    public Material getIcon() {
        return icon;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public int getXp() {
        return xp;
    }

    public CropType getCrop() {
        return crop;
    }

    public boolean isCrop() {
        return crop != null;
    }

    public int getUnlockLevel() {
        return unlockLevel;
    }

    public void setUnlockLevel(int unlockLevel) {
        this.unlockLevel = unlockLevel;
    }

    public static void setCustomItemResolver(Function<String, ItemStack> resolver) {
        customItems = resolver;
    }

    /** Valgfrit ItemsAdder-ikon, fx "hayday:hvede". */
    public void setItemsAdderId(String itemsAdderId) {
        this.itemsAdderId = itemsAdderId == null || itemsAdderId.isEmpty() ? null : itemsAdderId;
        this.displayStack = null;
    }

    private ItemStack custom() {
        return itemsAdderId == null ? null : customItems.apply(itemsAdderId);
    }

    public ItemBuilder icon() {
        ItemStack custom = custom();
        if (custom != null) {
            return new ItemBuilder(custom).name(getName());
        }
        return new ItemBuilder(icon).name(getName()).color(color);
    }

    public ItemStack iconStack(int amount) {
        return icon().amount(amount).build();
    }

    /** Et simpelt item til animationer og svævende ikoner (genbruges). */
    public ItemStack getDisplayStack() {
        if (displayStack != null) {
            return displayStack;
        }
        ItemStack custom = custom();
        if (custom != null) {
            displayStack = custom;
            return custom;
        }
        ItemStack vanilla = new ItemBuilder(icon).color(color).build();
        if (itemsAdderId == null) {
            // ItemsAdder-ikoner caches først når ItemsAdder har indlæst dem
            displayStack = vanilla;
        }
        return vanilla;
    }
}
