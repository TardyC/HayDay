package dev.tardyc.hayday.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Partikeleffekter. Partiklerne slås op via deres konstant-navn ved kørsel, så det virker uanset om
 * Particle er en enum eller et registry-interface i den aktuelle Minecraft-version.
 */
public final class Effects {

    private static final Particle HAPPY = find("HAPPY_VILLAGER", "VILLAGER_HAPPY");
    private static final Particle SPARKLE = find("WAX_ON", "END_ROD");
    private static final Particle GLOW = find("END_ROD", "FIREWORK", "FIREWORKS_SPARK");
    private static final Particle POOF = find("CLOUD", "POOF");

    private Effects() {
    }

    private static Particle find(String... names) {
        for (String name : names) {
            try {
                Object value = Particle.class.getField(name).get(null);
                if (value instanceof Particle) {
                    return (Particle) value;
                }
            } catch (Throwable ignored) {
                // prøv næste navn
            }
        }
        return null;
    }

    public static void harvest(Location location) {
        spawn(HAPPY, location, 8, 0.3, 0);
    }

    public static void produce(Location location) {
        spawn(SPARKLE, location, 10, 0.4, 0);
    }

    /** Lille glimt over en mark der er klar til høst. */
    public static void readySparkle(Location location) {
        spawn(GLOW, location, 2, 0.25, 0.01);
    }

    public static void plant(Location location) {
        spawn(POOF, location, 5, 0.2, 0.02);
    }

    private static void spawn(Particle particle, Location location, int count, double spread, double speed) {
        World world = location.getWorld();
        if (particle == null || world == null) {
            return;
        }
        try {
            world.spawnParticle(particle, location, count, spread, spread, spread, speed);
        } catch (Throwable ignored) {
            // partikler er kun pynt
        }
    }
}
