package dev.tardyc.hayday.util;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lille builder til menu-ikoner og items.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material material) {
        this(new ItemStack(material == null || material.isAir() ? Material.BARRIER : material));
    }

    public ItemBuilder(ItemStack base) {
        this.item = base.clone();
        this.meta = item.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            lore.addAll(meta.getLore());
        }
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        for (String line : lines) {
            lore.add(Text.color(line));
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        return this;
    }

    public ItemBuilder color(Color color) {
        if (color == null || meta == null) {
            return this;
        }
        if (meta instanceof PotionMeta) {
            ((PotionMeta) meta).setColor(color);
        } else if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
        }
        return this;
    }

    /** Item-model fra resourcepacken (Minecraft 1.21.4+). */
    public ItemBuilder model(NamespacedKey model) {
        if (meta != null && model != null) {
            meta.setItemModel(model);
        }
        return this;
    }

    public ItemBuilder skull(OfflinePlayer owner) {
        if (meta instanceof SkullMeta && owner != null) {
            ((SkullMeta) meta).setOwningPlayer(owner);
        }
        return this;
    }

    public ItemMeta meta() {
        return meta;
    }

    public ItemStack build() {
        if (meta != null) {
            meta.setLore(lore.isEmpty() ? null : lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
