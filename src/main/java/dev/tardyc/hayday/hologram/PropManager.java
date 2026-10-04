package dev.tardyc.hayday.hologram;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 3D-modeller fra resourcepacken ("props"): lastbilen, skibet, vindmøllevinger, høballer, sække ...
 * De vises som ItemDisplay-entities, er ikke-persistente og kan kun ses af spillere der har pakken
 * (alle andre ville se en lilla/sort klods).
 */
public final class PropManager {

    /** Én model i verdenen. */
    public static final class Prop {
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float scale;
        private final String model;
        private final float spin;
        private boolean enabled = true;
        private ItemDisplay entity;
        private float angle;

        Prop(String world, double x, double y, double z, float yaw, float scale, String model, float spin) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.scale = scale;
            this.model = model;
            this.spin = spin;
        }

        /** Slå modellen til/fra (fx skibet der kun ligger ved bryggen når det er i havn). */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        boolean isSpawned() {
            return entity != null && entity.isValid();
        }

        boolean shouldShow() {
            World w = Bukkit.getWorld(world);
            return enabled && w != null && w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        }
    }

    private final HayDayPlugin plugin;
    private final Set<Prop> props = new LinkedHashSet<>();
    private final Set<UUID> entities = new HashSet<>();

    public PropManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getSettings().propsEnabled;
    }

    /**
     * Opretter en model. {@code y} er hvor modellens bund står; {@code spin} er grader pr. sekund
     * (vindmøllevinger drejer om modellens midte).
     */
    public Prop create(String world, double x, double y, double z, float yaw, float scale, String model, float spin) {
        Prop prop = new Prop(world, x, y, z, yaw, scale, model, spin);
        props.add(prop);
        return prop;
    }

    public void remove(Prop prop) {
        if (prop != null) {
            despawn(prop);
            props.remove(prop);
        }
    }

    public void removeAll(List<Prop> list) {
        for (Prop prop : list) {
            remove(prop);
        }
        list.clear();
    }

    /** Kaldes hvert update-interval: spawner/fjerner modeller og drejer vindmøllerne. */
    public void tick(int intervalTicks) {
        boolean on = enabled();
        for (Prop prop : props) {
            boolean show = on && prop.shouldShow();
            if (show && !prop.isSpawned()) {
                spawn(prop);
            } else if (!show && prop.entity != null) {
                despawn(prop);
            } else if (show && prop.spin != 0) {
                prop.angle = (prop.angle + prop.spin * intervalTicks / 20f) % 360f;
                prop.entity.setInterpolationDelay(0);
                prop.entity.setInterpolationDuration(intervalTicks);
                prop.entity.setTransformation(transformation(prop));
            }
        }
    }

    private static Transformation transformation(Prop prop) {
        return new Transformation(new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(prop.angle), 0f, 0f, 1f),
                new Vector3f(prop.scale, prop.scale, prop.scale),
                new AxisAngle4f());
    }

    private void spawn(Prop prop) {
        despawn(prop);
        World world = Bukkit.getWorld(prop.world);
        if (world == null) {
            return;
        }
        // ItemDisplay viser modellen centreret om entity'en - løft den så bunden står på (x, y, z)
        Location location = new Location(world, prop.x, prop.y + 0.5 * prop.scale, prop.z, prop.yaw, 0f);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(NamespacedKey.fromString("hayday:prop_" + prop.model));
            item.setItemMeta(meta);
        }
        Consumer<ItemDisplay> setup = display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(transformation(prop));
            display.setViewRange(0.8f);
            display.setShadowRadius(0.45f * prop.scale);
            display.setShadowStrength(0.55f);
            display.getPersistentDataContainer().set(Keys.PROP, PersistentDataType.BYTE, (byte) 1);
        };
        ItemDisplay display = plugin.getHolograms().spawnSafely(() -> world.spawn(location, ItemDisplay.class, setup));
        if (display == null || !display.isValid()) {
            return;
        }
        prop.entity = display;
        entities.add(display.getUniqueId());
        for (Player player : world.getPlayers()) {
            if (plugin.getPack().hasPack(player)) {
                player.showEntity(plugin, display);
            }
        }
    }

    private void despawn(Prop prop) {
        if (prop.entity != null) {
            entities.remove(prop.entity.getUniqueId());
            prop.entity.remove();
            prop.entity = null;
        }
    }

    /** Spilleren har fået resourcepacken: vis alle modeller. */
    public void showAll(Player player) {
        if (!plugin.getPack().hasPack(player)) {
            return;
        }
        for (Prop prop : props) {
            if (prop.isSpawned()) {
                player.showEntity(plugin, prop.entity);
            }
        }
    }

    public void despawnAll() {
        for (Prop prop : props) {
            despawn(prop);
        }
    }

    /** Fjerner model-entities der ikke burde findes (fx efter et crash). */
    public void removeStray(List<Entity> list) {
        for (Entity entity : new ArrayList<>(list)) {
            if (entity.getPersistentDataContainer().has(Keys.PROP, PersistentDataType.BYTE)
                    && !entities.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    public int size() {
        return props.size();
    }
}
