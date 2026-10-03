package dev.tardyc.hayday.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Et hologram bygget på en TextDisplay-entity (og en Interaction-entity hvis det kan klikkes på).
 * Entities er ikke-persistente: de gemmes aldrig i verdenen og genskabes automatisk når chunken indlæses.
 */
public final class Hologram {

    private final HologramManager manager;
    private String world;
    private double x;
    private double y;
    private double z;
    private List<String> lines;
    private String rendered;
    private Consumer<Player> clickAction;

    private TextDisplay display;
    private Interaction interaction;

    Hologram(HologramManager manager, Location location, List<String> lines, Consumer<Player> clickAction) {
        this.manager = manager;
        setPosition(location);
        this.lines = new ArrayList<>(lines);
        this.rendered = manager.render(this.lines);
        this.clickAction = clickAction;
    }

    void setPosition(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public String getWorldName() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    public String getRendered() {
        return rendered;
    }

    /** Opdaterer teksten. Entity'en opdateres kun hvis teksten faktisk har ændret sig. */
    public void setLines(List<String> newLines) {
        int oldSize = lines.size();
        this.lines = new ArrayList<>(newLines);
        String text = manager.render(lines);
        if (text.equals(rendered)) {
            return;
        }
        rendered = text;
        if (display != null && display.isValid()) {
            display.setText(text);
        }
        if (interaction != null && interaction.isValid() && oldSize != lines.size()) {
            manager.resizeInteraction(interaction, lines.size());
        }
    }

    public Consumer<Player> getClickAction() {
        return clickAction;
    }

    public void setClickAction(Consumer<Player> clickAction) {
        boolean changed = (this.clickAction == null) != (clickAction == null);
        this.clickAction = clickAction;
        if (changed && isSpawned()) {
            despawn();
            spawn();
        }
    }

    public void click(Player player) {
        if (clickAction != null) {
            clickAction.accept(player);
        }
    }

    public boolean isChunkLoaded() {
        World w = Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    public boolean isSpawned() {
        boolean displayOk = display != null && display.isValid();
        boolean interactionOk = clickAction == null || (interaction != null && interaction.isValid());
        return displayOk && interactionOk;
    }

    void spawn() {
        if (isSpawned()) {
            return;
        }
        despawn();
        Location location = getLocation();
        if (location == null) {
            return;
        }
        display = manager.spawnDisplay(this, location);
        if (clickAction != null && display != null) {
            interaction = manager.spawnInteraction(this, location, lines.size());
        }
    }

    void despawn() {
        if (display != null) {
            manager.forget(display);
            display.remove();
            display = null;
        }
        if (interaction != null) {
            manager.forget(interaction);
            interaction.remove();
            interaction = null;
        }
    }
}
