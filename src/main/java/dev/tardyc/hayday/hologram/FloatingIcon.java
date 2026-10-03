package dev.tardyc.hayday.hologram;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.util.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * Et svævende ikon (ItemDisplay) der vipper blødt op og ned - som "boblen" over marker og bygninger i Hay Day.
 */
public final class FloatingIcon {

    private static final float BOB_HEIGHT = 0.12f;

    private final HayDayPlugin plugin;
    private ItemDisplay display;
    private ItemStack shown;
    private boolean up;

    public FloatingIcon(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    /** Viser ikonet på positionen (opretter eller opdaterer). Kaldes hvert update-interval, hvilket også driver vippe-animationen. */
    public void show(Location location, ItemStack item, float scale, boolean glow) {
        if (display == null || !display.isValid()) {
            spawn(location, item, scale);
        } else if (shown == null || !shown.isSimilar(item)) {
            display.setItemStack(item);
            shown = item;
        } else if (distanceSquared(location) > 0.01) {
            display.teleport(location);
        }
        if (display == null) {
            return;
        }
        if (display.isGlowing() != glow) {
            display.setGlowing(glow);
            if (glow) {
                display.setGlowColorOverride(Color.fromRGB(0x7CFC00));
            }
        }
        // Blød op/ned-bevægelse: klienten interpolerer mellem de to positioner
        up = !up;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(plugin.getSettings().updateInterval);
        display.setTransformation(transform(up ? BOB_HEIGHT : 0f, scale));
    }

    private double distanceSquared(Location location) {
        Location current = display.getLocation();
        if (current.getWorld() == null || !current.getWorld().equals(location.getWorld())) {
            return Double.MAX_VALUE;
        }
        return current.distanceSquared(location);
    }

    private void spawn(Location location, ItemStack item, float scale) {
        hide();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Consumer<ItemDisplay> setup = entity -> {
            entity.setPersistent(false);
            entity.setItemStack(item);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(plugin.getSettings().holoViewRange);
            entity.setTransformation(transform(0f, scale));
            entity.getPersistentDataContainer().set(Keys.ANIMATION, PersistentDataType.BYTE, (byte) 1);
        };
        display = plugin.getHolograms().spawnSafely(() -> world.spawn(location, ItemDisplay.class, setup));
        shown = item;
        if (display != null) {
            plugin.getAnimations().track(display);
        }
    }

    private static Transformation transform(float y, float scale) {
        return new Transformation(new Vector3f(0f, y, 0f), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    public void hide() {
        if (display != null) {
            plugin.getAnimations().untrack(display);
            display.remove();
            display = null;
            shown = null;
        }
    }

    public boolean isShown() {
        return display != null && display.isValid();
    }
}
