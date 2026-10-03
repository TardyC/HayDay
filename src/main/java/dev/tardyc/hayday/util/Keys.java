package dev.tardyc.hayday.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * NamespacedKeys til PersistentDataContainer (items og entities).
 */
public final class Keys {

    /** Tag på placerbare items: "field" eller "building:<id>". */
    public static NamespacedKey ITEM;
    /** Markerer hologram-entities. */
    public static NamespacedKey HOLOGRAM;
    /** Markerer dyr der står på bygninger. */
    public static NamespacedKey ANIMAL;
    /** Markerer midlertidige animations-entities (svævende ikoner, varer der flyver osv.). */
    public static NamespacedKey ANIMATION;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM = new NamespacedKey(plugin, "item");
        HOLOGRAM = new NamespacedKey(plugin, "hologram");
        ANIMAL = new NamespacedKey(plugin, "animal");
        ANIMATION = new NamespacedKey(plugin, "animation");
    }
}
