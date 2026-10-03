package dev.tardyc.hayday.util;

import org.bukkit.entity.Player;

/**
 * Lyde spilles via deres Minecraft-nøgle, så de virker på tværs af versioner.
 */
public final class Sounds {

    public static final String CLICK = "ui.button.click";
    public static final String SUCCESS = "entity.experience_orb.pickup";
    public static final String ERROR = "entity.villager.no";
    public static final String LEVEL_UP = "entity.player.levelup";
    public static final String HARVEST = "block.crop.break";
    public static final String PLANT = "item.crop.plant";
    public static final String PRODUCE = "block.note_block.pling";
    public static final String COLLECT = "entity.item.pickup";
    public static final String COINS = "block.amethyst_block.chime";
    public static final String PLACE = "block.composter.fill_success";

    private Sounds() {
    }

    public static void play(Player player, String sound) {
        play(player, sound, 1.0f);
    }

    public static void play(Player player, String sound, float pitch) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, 0.8f, pitch);
        }
    }
}
