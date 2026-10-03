package dev.tardyc.hayday.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

/**
 * En blokposition der ikke holder fast i World-objektet (sikkert at gemme og bruge som nøgle).
 */
public final class BlockPos {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public BlockPos(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos parse(String input) {
        if (input == null) {
            return null;
        }
        String[] parts = input.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new BlockPos(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public String getWorldName() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    /** Blokken hvis verdenen er indlæst, ellers null. */
    public Block getBlock() {
        World w = getWorld();
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    public boolean isLoaded() {
        World w = getWorld();
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    public BlockPos up() {
        return new BlockPos(world, x, y + 1, z);
    }

    public BlockPos down() {
        return new BlockPos(world, x, y - 1, z);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(world, x + dx, y + dy, z + dz);
    }

    /** Midten af blokken i bunden (x+0.5, y, z+0.5). */
    public Location toCenter() {
        return new Location(getWorld(), x + 0.5, y, z + 0.5);
    }

    public String chunkKey() {
        return chunkKey(world, x >> 4, z >> 4);
    }

    public static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public double distanceSquared(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return Double.MAX_VALUE;
        }
        double dx = location.getX() - (x + 0.5);
        double dy = location.getY() - (y + 0.5);
        double dz = location.getZ() - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockPos)) {
            return false;
        }
        BlockPos other = (BlockPos) o;
        return x == other.x && y == other.y && z == other.z && world.equals(other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return world + " " + x + ", " + y + ", " + z;
    }
}
