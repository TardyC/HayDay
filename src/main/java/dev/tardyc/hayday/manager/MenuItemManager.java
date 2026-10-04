package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * HayDay-itemet i hotbaren: åbner menuen, og kan ikke smides, flyttes, lægges i kister eller mistes ved død.
 * Det gives igen automatisk hvis det på en eller anden måde forsvinder (fx /clear).
 */
public final class MenuItemManager {

    /** Item-modellen fra HayDays resourcepack. */
    private static final NamespacedKey MODEL = NamespacedKey.fromString("hayday:hayday_menu");

    private final HayDayPlugin plugin;

    public MenuItemManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(Keys.MENU_ITEM, PersistentDataType.BYTE);
    }

    /** Skal spilleren have itemet her (slået til og i en tilladt verden)? */
    public boolean shouldHave(Player player) {
        Settings settings = plugin.getSettings();
        if (!settings.menuItemEnabled || !player.hasPermission("hayday.use")) {
            return false;
        }
        return settings.menuItemWorlds.isEmpty()
                || settings.menuItemWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public ItemStack create(Player player) {
        Settings settings = plugin.getSettings();
        Material material = Material.matchMaterial(settings.menuItemMaterial);
        if (material == null || material.isAir() || !material.isItem()) {
            material = Material.WHEAT;
        }
        ItemBuilder builder = new ItemBuilder(material)
                .name(settings.menuItemName)
                .lore(settings.menuItemLore)
                .glow(settings.menuItemGlow);
        if (plugin.getPack().hasPack(player) && plugin.getPack().getGlyphs().hasItemTexture("hayday_menu")) {
            builder.model(MODEL);
        }
        ItemStack item = builder.build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.MENU_ITEM, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Sørger for at spilleren har præcis ét HayDay-item på den rigtige plads (eller intet, hvis det er slået fra).
     * Ligger der allerede noget på pladsen, flyttes det til en ledig plads i inventoryet.
     */
    public void update(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (!shouldHave(player)) {
            remove(player);
            return;
        }
        ItemStack wanted = create(player);
        int slot = Math.max(0, Math.min(8, plugin.getSettings().menuItemSlot));
        int found = -1;
        for (int i = 0; i < 36; i++) {
            if (!isMenuItem(inventory.getItem(i))) {
                continue;
            }
            if (found == -1) {
                found = i;
            } else {
                inventory.setItem(i, null);
            }
        }
        if (isMenuItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        if (found >= 0) {
            if (!wanted.isSimilar(inventory.getItem(found))) {
                inventory.setItem(found, wanted);
            }
            return;
        }
        ItemStack current = inventory.getItem(slot);
        if (current != null && !current.getType().isAir()) {
            int free = inventory.firstEmpty();
            if (free < 0) {
                return;
            }
            inventory.setItem(free, current);
        }
        inventory.setItem(slot, wanted);
    }

    public void remove(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (isMenuItem(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
        if (isMenuItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
    }
}
