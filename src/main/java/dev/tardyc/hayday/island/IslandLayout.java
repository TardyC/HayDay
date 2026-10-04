package dev.tardyc.hayday.island;

import org.bukkit.Material;

/**
 * Geometrien i HayDay-verdenen: et gitter af øer i havet.
 *
 * <p>Hver ø er et kvadrat på {@code size x size} blokke med runde hjørner og en sandstrand, og der er
 * {@code gap} blokke hav mellem øerne. Ø (gx, gz) dækker x = gx*cell .. gx*cell+size-1 (tilsvarende for z),
 * hvor cell = size + gap. Ø (0, 0) er torvet, hvor verdenens spawn ligger.</p>
 */
public final class IslandLayout {

    /** Radius på øernes runde hjørner. */
    private static final int CORNER = 6;

    private final int size;
    private final int gap;
    private final int cell;
    private final int height;

    public IslandLayout(int size, int gap, int height) {
        this.size = Math.max(24, Math.min(256, size));
        this.gap = Math.max(8, Math.min(128, gap));
        this.cell = this.size + this.gap;
        this.height = Math.max(-40, Math.min(250, height));
    }

    public int getSize() {
        return size;
    }

    public int getGap() {
        return gap;
    }

    public int getHeight() {
        return height;
    }

    /** Laveste blok (bedrock). Under den er der tomt. */
    public int getBottom() {
        return height - 20;
    }

    public int gridX(int blockX) {
        return Math.floorDiv(blockX, cell);
    }

    public int gridZ(int blockZ) {
        return Math.floorDiv(blockZ, cell);
    }

    public int minX(int gridX) {
        return gridX * cell;
    }

    public int minZ(int gridZ) {
        return gridZ * cell;
    }

    public int maxX(int gridX) {
        return gridX * cell + size - 1;
    }

    public int maxZ(int gridZ) {
        return gridZ * cell + size - 1;
    }

    public int centerX(int gridX) {
        return gridX * cell + size / 2;
    }

    public int centerZ(int gridZ) {
        return gridZ * cell + size / 2;
    }

    /** Ligger punktet inden for en øs kvadrat (inkl. hjørnerne)? */
    public boolean inSquare(int x, int z) {
        return Math.floorMod(x, cell) < size && Math.floorMod(z, cell) < size;
    }

    public static long key(int gridX, int gridZ) {
        return ((long) gridX << 32) | (gridZ & 0xFFFFFFFFL);
    }

    /** Afstand fra øens kant (0 = yderste række), eller -1 hvis punktet er hav. */
    public int edgeDistance(int x, int z) {
        int lx = Math.floorMod(x, cell);
        int lz = Math.floorMod(z, cell);
        if (lx >= size || lz >= size) {
            return -1;
        }
        int dx = Math.min(lx, size - 1 - lx);
        int dz = Math.min(lz, size - 1 - lz);
        if (dx < CORNER && dz < CORNER) {
            double ox = CORNER - dx - 0.5;
            double oz = CORNER - dz - 0.5;
            double distance = CORNER - Math.sqrt(ox * ox + oz * oz);
            return distance < 0 ? -1 : (int) distance;
        }
        return Math.min(dx, dz);
    }

    /** Stien på torvet: et kryds gennem midten af ø (0, 0). */
    private boolean isSpawnPath(int x, int z) {
        if (gridX(x) != 0 || gridZ(z) != 0) {
            return false;
        }
        return Math.abs(x - centerX(0)) <= 1 || Math.abs(z - centerZ(0)) <= 1;
    }

    /** Blokken som verdenen genereres med på (x, y, z), eller null for luft. */
    public Material material(int x, int y, int z) {
        return material(x, z, edgeDistance(x, z), isSpawnPath(x, z), y);
    }

    /** Fast "tilfældigt" tal 0-1023 for en kolonne, så havbunden ser naturlig ud (og altid ens). */
    private static int noise(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L;
        h ^= h >>> 29;
        h *= 0x5DEECE66DL;
        h ^= h >>> 17;
        return (int) (h & 1023);
    }

    Material material(int x, int z, int edge, boolean spawnPath, int y) {
        int bottom = getBottom();
        if (y < bottom || y > height) {
            return null;
        }
        if (y == bottom) {
            return Material.BEDROCK;
        }
        if (y <= height - 9) {
            return Material.STONE;
        }
        if (edge < 0) {
            // Havet: sandbund med grus og ler, søgræs og tang - og 5 blokke vand
            int n = noise(x, z);
            if (y <= height - 8) {
                return Material.SAND;
            }
            if (y == height - 7) {
                return n < 90 ? Material.GRAVEL : n < 130 ? Material.CLAY : Material.SAND;
            }
            if (y > height - 2) {
                return null;
            }
            if (n >= 300 && n < 430 && y == height - 6) {
                return Material.SEAGRASS;
            }
            if (n >= 430 && n < 452) {
                int top = height - 6 + n % 3;
                if (y < top) {
                    return Material.KELP_PLANT;
                }
                if (y == top) {
                    return Material.KELP;
                }
            }
            return Material.WATER;
        }
        if (edge == 0) {
            return y <= height - 1 ? Material.SAND : null;
        }
        if (edge <= 2) {
            return Material.SAND;
        }
        if (y == height) {
            return spawnPath && edge >= 3 ? Material.DIRT_PATH : Material.GRASS_BLOCK;
        }
        return Material.DIRT;
    }

    /** Kolonne-version til generatoren (beregner kant og sti én gang pr. kolonne). */
    void fillColumn(int x, int z, int minY, int maxY, ColumnSink sink) {
        int edge = edgeDistance(x, z);
        boolean path = isSpawnPath(x, z);
        for (int y = minY; y <= maxY; y++) {
            Material material = material(x, z, edge, path, y);
            if (material != null) {
                sink.set(y, material);
            }
        }
    }

    /** Modtager blokkene i en kolonne. */
    @FunctionalInterface
    interface ColumnSink {
        void set(int y, Material material);
    }
}
