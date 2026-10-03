package dev.tardyc.hayday.hologram;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.util.Keys;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Holder styr på alle hologrammer og deres entities.
 */
public final class HologramManager {

    private static final double LINE_HEIGHT = 0.27;

    private final HayDayPlugin plugin;
    private final Set<Hologram> holograms = new LinkedHashSet<>();
    private final Map<UUID, Hologram> byEntity = new HashMap<>();
    private boolean spawning;

    public HologramManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    /** Farver, ikoner ({icon_clock} ...) og linjeskift. */
    String render(List<String> lines) {
        return plugin.getIcons().apply(Text.color(String.join("\n", lines)));
    }

    public Hologram create(Location location, List<String> lines, Consumer<Player> clickAction) {
        Hologram hologram = new Hologram(this, location, lines, clickAction);
        holograms.add(hologram);
        if (hologram.isChunkLoaded()) {
            hologram.spawn();
        }
        return hologram;
    }

    public void remove(Hologram hologram) {
        if (hologram == null) {
            return;
        }
        hologram.despawn();
        holograms.remove(hologram);
    }

    public void move(Hologram hologram, Location location) {
        hologram.despawn();
        hologram.setPosition(location);
        if (hologram.isChunkLoaded()) {
            hologram.spawn();
        }
    }

    /**
     * Kaldes hvert update-interval: spawner hologrammer i indlæste chunks og rydder op i dem der er forsvundet.
     */
    public void tick() {
        for (Hologram hologram : holograms) {
            boolean loaded = hologram.isChunkLoaded();
            if (loaded && !hologram.isSpawned()) {
                hologram.spawn();
            } else if (!loaded && hologram.isSpawned()) {
                hologram.despawn();
            }
        }
    }

    public void despawnAll() {
        for (Hologram hologram : holograms) {
            hologram.despawn();
        }
        byEntity.clear();
    }

    public void respawnAll() {
        for (Hologram hologram : holograms) {
            hologram.despawn();
            if (hologram.isChunkLoaded()) {
                hologram.spawn();
            }
        }
    }

    public void despawnWorld(World world) {
        for (Hologram hologram : holograms) {
            if (hologram.getWorldName().equals(world.getName())) {
                hologram.despawn();
            }
        }
    }

    public Hologram getByEntity(Entity entity) {
        return byEntity.get(entity.getUniqueId());
    }

    public boolean isSpawning() {
        return spawning;
    }

    /** Spawner noget mens andre plugins' spawn-blokering ignoreres (se EntityListener). */
    public <T> T spawnSafely(Supplier<T> spawner) {
        spawning = true;
        try {
            return spawner.get();
        } finally {
            spawning = false;
        }
    }

    void forget(Entity entity) {
        byEntity.remove(entity.getUniqueId());
    }

    TextDisplay spawnDisplay(Hologram hologram, Location location) {
        Settings settings = plugin.getSettings();
        Consumer<TextDisplay> setup = display -> {
            display.setPersistent(false);
            display.setText(hologram.getRendered());
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(settings.holoShadow);
            display.setSeeThrough(false);
            display.setLineWidth(400);
            if (settings.holoBackground == null) {
                display.setDefaultBackground(true);
            } else {
                display.setDefaultBackground(false);
                display.setBackgroundColor(settings.holoBackground);
            }
            display.setViewRange(settings.holoViewRange);
            display.setBrightness(new Display.Brightness(15, 15));
            float scale = settings.holoScale;
            if (scale > 0 && scale != 1.0f) {
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
            }
            display.getPersistentDataContainer().set(Keys.HOLOGRAM, PersistentDataType.BYTE, (byte) 1);
        };
        TextDisplay display = spawnSafely(() -> location.getWorld().spawn(location, TextDisplay.class, setup));
        if (display == null || !display.isValid()) {
            return null;
        }
        byEntity.put(display.getUniqueId(), hologram);
        return display;
    }

    Interaction spawnInteraction(Hologram hologram, Location location, int lineCount) {
        Consumer<Interaction> setup = interaction -> {
            interaction.setPersistent(false);
            interaction.setResponsive(true);
            interaction.getPersistentDataContainer().set(Keys.HOLOGRAM, PersistentDataType.BYTE, (byte) 1);
            applySize(interaction, lineCount);
        };
        Interaction interaction = spawnSafely(() -> location.getWorld().spawn(location, Interaction.class, setup));
        if (interaction == null || !interaction.isValid()) {
            return null;
        }
        byEntity.put(interaction.getUniqueId(), hologram);
        return interaction;
    }

    void resizeInteraction(Interaction interaction, int lineCount) {
        applySize(interaction, lineCount);
    }

    private void applySize(Interaction interaction, int lineCount) {
        float scale = plugin.getSettings().holoScale > 0 ? plugin.getSettings().holoScale : 1.0f;
        interaction.setInteractionWidth(2.0f * scale);
        interaction.setInteractionHeight((float) (Math.max(1, lineCount) * LINE_HEIGHT * scale + 0.1));
    }

    /** Fjerner HayDay-entities der ikke burde findes (fx efter et crash). */
    public void removeStray(List<Entity> entities) {
        for (Entity entity : new ArrayList<>(entities)) {
            if (entity.getPersistentDataContainer().has(Keys.HOLOGRAM, PersistentDataType.BYTE)
                    && !byEntity.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    public int size() {
        return holograms.size();
    }
}
