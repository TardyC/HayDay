package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.hook.ProtocolLibHook;
import dev.tardyc.hayday.util.Effects;
import dev.tardyc.hayday.util.Keys;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Animationer med Display-entities (og ProtocolLib hvis det findes): varer der hopper op og flyver hen til
 * spilleren, svævende tekster, mønt-regn, fyrværkeri ved level up og frø der falder ned i jorden.
 */
public final class AnimationManager {

    private final HayDayPlugin plugin;
    private final Set<Entity> tracked = new HashSet<>();
    private ProtocolLibHook protocolLib;

    public AnimationManager(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        protocolLib = null;
        Settings settings = plugin.getSettings();
        if (settings.animHarvest && settings.useProtocolLib && Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                protocolLib = new ProtocolLibHook();
                plugin.getLogger().info("ProtocolLib fundet - bruger Minecrafts 'saml op'-animation.");
            } catch (Throwable t) {
                plugin.getLogger().warning("ProtocolLib kunne ikke bruges: " + t);
            }
        }
    }

    public boolean hasProtocolLib() {
        return protocolLib != null;
    }

    public void track(Entity entity) {
        tracked.add(entity);
    }

    public void untrack(Entity entity) {
        tracked.remove(entity);
    }

    public boolean isAnimationEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.ANIMATION, PersistentDataType.BYTE);
    }

    /** Fjerner alle midlertidige animations-entities (ved nedlukning/reload). */
    public void cleanup() {
        for (Entity entity : new ArrayList<>(tracked)) {
            entity.remove();
        }
        tracked.clear();
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    private void removeLater(Entity entity, long ticks) {
        later(ticks, () -> {
            tracked.remove(entity);
            entity.remove();
        });
    }

    // ------------------------------------------------------------------
    // Høst / hent: varen flyver hen til spilleren
    // ------------------------------------------------------------------

    public void collect(Player player, Location from, ItemStack icon, int amount) {
        if (!plugin.getSettings().animHarvest || icon == null || from.getWorld() == null) {
            return;
        }
        if (protocolLib != null) {
            pickupWithProtocolLib(player, from, icon, amount);
        } else {
            flyToPlayer(player, from, icon);
        }
    }

    private void pickupWithProtocolLib(Player player, Location from, ItemStack icon, int amount) {
        World world = from.getWorld();
        ItemStack stack = icon.clone();
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        Item item = plugin.getHolograms().spawnSafely(() -> world.dropItem(from, stack));
        if (item == null || !item.isValid()) {
            flyToPlayer(player, from, icon);
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        item.setPickupDelay(Short.MAX_VALUE);
        item.setPersistent(false);
        item.setInvulnerable(true);
        item.getPersistentDataContainer().set(Keys.ANIMATION, PersistentDataType.BYTE, (byte) 1);
        item.setVelocity(new Vector(random.nextDouble(-0.08, 0.08), 0.32, random.nextDouble(-0.08, 0.08)));
        tracked.add(item);
        later(12, () -> {
            if (item.isValid() && player.isOnline() && player.getWorld().equals(item.getWorld())) {
                try {
                    protocolLib.sendPickup(item, player, amount, viewers(item.getLocation()));
                } catch (Throwable t) {
                    plugin.getLogger().fine("Pickup-pakke fejlede: " + t);
                }
            }
            removeLater(item, 3);
        });
    }

    private List<Player> viewers(Location location) {
        List<Player> list = new ArrayList<>();
        for (Player online : location.getWorld().getPlayers()) {
            if (online.getLocation().distanceSquared(location) < 48 * 48) {
                list.add(online);
            }
        }
        return list;
    }

    private void flyToPlayer(Player player, Location from, ItemStack icon) {
        ItemDisplay display = spawnItemDisplay(from, icon, 0.01f);
        if (display == null) {
            return;
        }
        later(1, () -> animate(display, 6, new Vector3f(0f, 0.6f, 0f), 0.65f));
        later(9, () -> {
            if (!display.isValid() || !player.isOnline() || !player.getWorld().equals(display.getWorld())) {
                return;
            }
            display.setTeleportDuration(8);
            display.teleport(player.getLocation().add(0, 1.0, 0));
            animate(display, 8, new Vector3f(0f, 0f, 0f), 0.25f);
        });
        removeLater(display, 18);
    }

    // ------------------------------------------------------------------
    // Svævende tekst
    // ------------------------------------------------------------------

    public void floatingText(Location location, String text) {
        if (!plugin.getSettings().animFloatingText || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        String rendered = plugin.getIcons().apply(Text.color(text));
        Consumer<TextDisplay> setup = display -> {
            display.setPersistent(false);
            display.setText(rendered);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(transform(new Vector3f(), 0.9f));
            display.getPersistentDataContainer().set(Keys.ANIMATION, PersistentDataType.BYTE, (byte) 1);
        };
        TextDisplay display = plugin.getHolograms().spawnSafely(() -> world.spawn(location, TextDisplay.class, setup));
        if (display == null || !display.isValid()) {
            return;
        }
        tracked.add(display);
        later(1, () -> animate(display, 24, new Vector3f(0f, 1.1f, 0f), 0.9f));
        later(20, () -> animate(display, 6, new Vector3f(0f, 1.4f, 0f), 0.01f));
        removeLater(display, 27);
    }

    // ------------------------------------------------------------------
    // Mønt-regn, fyrværkeri og plantning
    // ------------------------------------------------------------------

    public void coinBurst(Player player) {
        Sounds.play(player, Sounds.COINS);
        if (!plugin.getSettings().animations) {
            return;
        }
        Location center = player.getLocation().add(0, 1.3, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET);
        for (int i = 0; i < 7; i++) {
            ItemDisplay display = spawnItemDisplay(center, coin, 0.05f);
            if (display == null) {
                return;
            }
            double angle = (Math.PI * 2 / 7) * i + random.nextDouble(0.4);
            Vector3f target = new Vector3f((float) Math.cos(angle) * 1.1f, (float) random.nextDouble(0.3, 0.9), (float) Math.sin(angle) * 1.1f);
            later(1, () -> animate(display, 10, target, 0.4f));
            later(12, () -> animate(display, 6, new Vector3f(target.x, target.y - 0.6f, target.z), 0.01f));
            removeLater(display, 19);
        }
    }

    public void levelUp(Player player) {
        if (!plugin.getSettings().animFireworks) {
            return;
        }
        Location location = player.getLocation().add(0, 1.5, 0);
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Consumer<Firework> setup = firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.YELLOW, Color.LIME, Color.ORANGE)
                    .withFade(Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.setPersistent(false);
            firework.getPersistentDataContainer().set(Keys.ANIMATION, PersistentDataType.BYTE, (byte) 1);
        };
        Firework firework = plugin.getHolograms().spawnSafely(() -> world.spawn(location, Firework.class, setup));
        if (firework != null && firework.isValid()) {
            tracked.add(firework);
            later(2, () -> {
                tracked.remove(firework);
                if (firework.isValid()) {
                    firework.detonate();
                }
            });
        }
    }

    public void plant(Location soilTop, ItemStack seed) {
        Effects.plant(soilTop.clone().add(0, 0.2, 0));
        if (!plugin.getSettings().animHarvest || seed == null) {
            return;
        }
        ItemDisplay display = spawnItemDisplay(soilTop.clone().add(0, 1.2, 0), seed, 0.4f);
        if (display == null) {
            return;
        }
        later(1, () -> animate(display, 6, new Vector3f(0f, -1.0f, 0f), 0.2f));
        removeLater(display, 8);
    }

    // ------------------------------------------------------------------
    // Hjælpere
    // ------------------------------------------------------------------

    private ItemDisplay spawnItemDisplay(Location location, ItemStack item, float scale) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Consumer<ItemDisplay> setup = display -> {
            display.setPersistent(false);
            display.setItemStack(item);
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(transform(new Vector3f(), scale));
            display.getPersistentDataContainer().set(Keys.ANIMATION, PersistentDataType.BYTE, (byte) 1);
        };
        ItemDisplay display = plugin.getHolograms().spawnSafely(() -> world.spawn(location, ItemDisplay.class, setup));
        if (display == null || !display.isValid()) {
            return null;
        }
        tracked.add(display);
        return display;
    }

    private static void animate(Display display, int ticks, Vector3f translation, float scale) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transform(translation, scale));
    }

    private static Transformation transform(Vector3f translation, float scale) {
        return new Transformation(translation, new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }
}
