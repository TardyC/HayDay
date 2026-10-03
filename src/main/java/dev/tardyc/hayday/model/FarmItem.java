package dev.tardyc.hayday.model;

import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * En vare i HayDay (afgrøde eller produkt). Varer er virtuelle og ligger i spillerens silo/lade.
 */
public final class FarmItem {

    /** Finder ItemsAdder-items (sættes af pluginet; returnerer null hvis ItemsAdder ikke er tilgængelig). */
    private static Function<String, ItemStack> customItems = id -> null;
    /** Har resourcepacken en tekstur til varen? */
    private static Predicate<String> packTexture = id -> false;
    /** Har spilleren resourcepacken? */
    private static Predicate<Player> playerHasPack = player -> false;
    /** Har alle spillere resourcepacken (så entities i verdenen kan bruge teksturerne)? */
    private static BooleanSupplier everyoneHasPack = () -> false;

    private final String id;
    private final String name;
    private final Material icon;
    private final Color color;
    private final ItemCategory category;
    private final double sellPrice;
    private final int xp;
    private final CropType crop;
    private String itemsAdderId;
    private NamespacedKey model;
    /** Level hvor varen kan skaffes (sættes når bygningerne er indlæst). */
    private int unlockLevel;
    private ItemStack displayStack;
    private ItemStack displayModelStack;

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

    public static void setPackPolicy(Predicate<String> texture, Predicate<Player> hasPack, BooleanSupplier everyone) {
        packTexture = texture;
        playerHasPack = hasPack;
        everyoneHasPack = everyone;
    }

    /** Valgfri item-model fra items.yml, fx "hayday:kyllingefoder". */
    public void setModel(String model) {
        this.model = model == null || model.isEmpty() ? null : NamespacedKey.fromString(model);
        this.displayModelStack = null;
    }

    /** Item-modellen fra resourcepacken, eller null hvis varen bruger et almindeligt Minecraft-ikon. */
    public NamespacedKey getModel() {
        if (model != null) {
            return model;
        }
        return packTexture.test(id) ? NamespacedKey.fromString("hayday:" + id) : null;
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

    /** Ikonet som en bestemt spiller ser det (med resourcepack-tekstur hvis spilleren har pakken). */
    public ItemBuilder icon(Player viewer) {
        ItemBuilder builder = icon();
        NamespacedKey key = getModel();
        if (key != null && custom() == null && playerHasPack.test(viewer)) {
            builder.model(key);
        }
        return builder;
    }

    public ItemStack iconStack(int amount) {
        return icon().amount(amount).build();
    }

    /** Et simpelt item til animationer og svævende ikoner (genbruges). */
    public ItemStack getDisplayStack() {
        NamespacedKey key = getModel();
        if (key != null && custom() == null && everyoneHasPack.getAsBoolean()) {
            if (displayModelStack == null) {
                displayModelStack = new ItemBuilder(icon).color(color).model(key).build();
            }
            return displayModelStack;
        }
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
