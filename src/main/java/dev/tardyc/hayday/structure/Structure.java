package dev.tardyc.hayday.structure;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * En bygning eller noget pynt fra structures.yml: et 3D-gitter af tegn + en palette.
 * Kan drejes i 90-graders trin (forsiden mod syd = drejning 0, med uret).
 */
public final class Structure {

    /** Rør ikke blokken. */
    public static final char KEEP = ' ';
    /** Luft. */
    public static final char AIR = '.';
    /** Ankeret (bygningens blok). */
    public static final char ANCHOR = '@';
    /** Plads til en mark (kun på gårdens mark). */
    public static final char FIELD = 'F';

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final String[] DIRECTIONS = {"north", "east", "south", "west"};

    /** En blok i strukturen, relativt til ankeret og allerede drejet. */
    public static final class Cell {
        public final int dx;
        public final int dy;
        public final int dz;
        public final char ch;

        Cell(int dx, int dy, int dz, char ch) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.ch = ch;
        }

        public boolean isAnchor() {
            return ch == ANCHOR;
        }

        /** Under ankerets lag = jorden (bliver til græs igen når bygningen fjernes). */
        public boolean isGround() {
            return dy < 0;
        }
    }

    /** En 3D-model ved huset: position relativt til ankerets midte (før drejning). */
    public static final class PropDef {
        public final String model;
        public final double dx;
        public final double dy;
        public final double dz;
        public final float yaw;
        public final float scale;
        public final float spin;

        PropDef(String model, double dx, double dy, double dz, float yaw, float scale, float spin) {
            this.model = model;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.yaw = yaw;
            this.scale = scale;
            this.spin = spin;
        }

        /** "model dx dy dz [yaw] [scale] [spin]" */
        static PropDef parse(String line) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 4) {
                return null;
            }
            try {
                return new PropDef(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        parts.length > 4 ? Float.parseFloat(parts[4]) : 0f,
                        parts.length > 5 ? Float.parseFloat(parts[5]) : 1f,
                        parts.length > 6 ? Float.parseFloat(parts[6]) : 0f);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** {x, z, yaw} efter drejning. */
        public double[] rotated(int rotation) {
            double x = dx;
            double z = dz;
            for (int i = 0; i < (rotation & 3); i++) {
                double t = x;
                x = -z;
                z = t;
            }
            return new double[]{x, z, yaw + 90.0 * (rotation & 3)};
        }
    }

    private final String id;
    private final int width;
    private final int height;
    private final int depth;
    private final char[][][] cells;
    private final Map<Character, String> palette;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private final double hologramHeight;
    private final List<PropDef> props = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private final List<Cell>[] rotated = new List[4];
    private final Map<String, BlockData> dataCache = new HashMap<>();

    private Structure(String id, char[][][] cells, Map<Character, String> palette, int[] anchor, double hologramHeight) {
        this.id = id;
        this.cells = cells;
        this.height = cells.length;
        this.depth = cells[0].length;
        this.width = cells[0][0].length;
        this.palette = palette;
        this.anchorX = anchor[0];
        this.anchorY = anchor[1];
        this.anchorZ = anchor[2];
        this.hologramHeight = hologramHeight;
    }

    /** Læser en struktur fra structures.yml. */
    public static Structure parse(String id, ConfigurationSection section) {
        List<?> layers = section.getList("layers");
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("ingen lag");
        }
        int height = layers.size();
        int depth = -1;
        int width = -1;
        char[][][] cells = new char[height][][];
        int[] anchor = null;
        for (int y = 0; y < height; y++) {
            if (!(layers.get(y) instanceof List)) {
                throw new IllegalArgumentException("lag " + y + " er ikke en liste");
            }
            List<?> rows = (List<?>) layers.get(y);
            if (depth < 0) {
                depth = rows.size();
            } else if (rows.size() != depth) {
                throw new IllegalArgumentException("lag " + y + " har " + rows.size() + " rækker (forventede " + depth + ")");
            }
            cells[y] = new char[depth][];
            for (int z = 0; z < depth; z++) {
                String row = String.valueOf(rows.get(z));
                if (width < 0) {
                    width = row.length();
                } else if (row.length() != width) {
                    throw new IllegalArgumentException("lag " + y + " række " + z + " har længden " + row.length()
                            + " (forventede " + width + ")");
                }
                cells[y][z] = row.toCharArray();
                int index = row.indexOf(ANCHOR);
                if (index >= 0) {
                    if (anchor != null) {
                        throw new IllegalArgumentException("mere end ét '@'");
                    }
                    anchor = new int[]{index, y, z};
                }
            }
        }
        if (anchor == null || depth <= 0 || width <= 0) {
            throw new IllegalArgumentException("mangler '@' (ankeret)");
        }
        Map<Character, String> palette = new LinkedHashMap<>();
        for (String entry : section.getStringList("palette")) {
            if (entry.length() >= 3 && entry.charAt(1) == '=') {
                palette.put(entry.charAt(0), entry.substring(2).trim());
            }
        }
        for (char[][] layer : cells) {
            for (char[] row : layer) {
                for (char ch : row) {
                    if (ch != KEEP && ch != AIR && !palette.containsKey(ch)) {
                        throw new IllegalArgumentException("tegnet '" + ch + "' mangler i paletten");
                    }
                }
            }
        }
        Structure structure = new Structure(id, cells, palette, anchor, section.getDouble("hologram-height", height));
        for (String line : section.getStringList("props")) {
            PropDef prop = PropDef.parse(line);
            if (prop != null) {
                structure.props.add(prop);
            }
        }
        return structure;
    }

    public List<PropDef> getProps() {
        return Collections.unmodifiableList(props);
    }

    public String getId() {
        return id;
    }

    public double getHologramHeight() {
        return hologramHeight;
    }

    // ------------------------------------------------------------------
    // Drejning
    // ------------------------------------------------------------------

    /** Drejer (dx, dz) {@code rotation} gange 90 grader med uret. */
    public static int[] rotate(int dx, int dz, int rotation) {
        int x = dx;
        int z = dz;
        for (int i = 0; i < (rotation & 3); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[]{x, z};
    }

    /** Alle blokke (undtagen ' ') relativt til ankeret, drejet. */
    public List<Cell> cells(int rotation) {
        int r = rotation & 3;
        if (rotated[r] == null) {
            List<Cell> list = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    for (int x = 0; x < width; x++) {
                        char ch = cells[y][z][x];
                        if (ch == KEEP) {
                            continue;
                        }
                        int[] xz = rotate(x - anchorX, z - anchorZ, r);
                        list.add(new Cell(xz[0], y - anchorY, xz[1], ch));
                    }
                }
            }
            rotated[r] = Collections.unmodifiableList(list);
        }
        return rotated[r];
    }

    /** Er der en (ikke-luft) blok på den relative position? */
    public boolean contains(int dx, int dy, int dz, int rotation) {
        int[] xz = rotate(dx, dz, (4 - (rotation & 3)) & 3);
        int x = xz[0] + anchorX;
        int y = dy + anchorY;
        int z = xz[1] + anchorZ;
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            return false;
        }
        char ch = cells[y][z][x];
        return ch != KEEP && ch != AIR;
    }

    /** Blokken for et tegn, drejet. */
    public BlockData data(char ch, int rotation) {
        String raw = ch == AIR ? "air" : palette.get(ch);
        if (raw == null) {
            return null;
        }
        String key = (rotation & 3) + ":" + raw;
        BlockData cached = dataCache.get(key);
        if (cached != null) {
            return cached;
        }
        String text = rotateData(raw, rotation & 3);
        BlockData data;
        try {
            data = Bukkit.createBlockData(text.contains(":") ? text : "minecraft:" + text);
        } catch (IllegalArgumentException e) {
            data = Material.AIR.createBlockData();
        }
        dataCache.put(key, data);
        return data;
    }

    /** Drejer retninger i en blok-tekst, fx "oak_stairs[facing=north]" -> "oak_stairs[facing=east]". */
    static String rotateData(String raw, int rotation) {
        int open = raw.indexOf('[');
        if (rotation == 0 || open < 0 || !raw.endsWith("]")) {
            return raw;
        }
        String name = raw.substring(0, open);
        String[] props = raw.substring(open + 1, raw.length() - 1).split(",");
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> sides = new HashMap<>();
        for (String prop : props) {
            int eq = prop.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = prop.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = prop.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
            int side = indexOf(key);
            if (side >= 0) {
                sides.put(DIRECTIONS[(side + rotation) & 3], value);
                continue;
            }
            switch (key) {
                case "facing":
                    int index = indexOf(value);
                    out.put(key, index >= 0 ? DIRECTIONS[(index + rotation) & 3] : value);
                    break;
                case "axis":
                    out.put(key, rotation % 2 == 1 && !value.equals("y") ? (value.equals("x") ? "z" : "x") : value);
                    break;
                case "rotation":
                    try {
                        out.put(key, String.valueOf((Integer.parseInt(value) + rotation * 4) % 16));
                    } catch (NumberFormatException e) {
                        out.put(key, value);
                    }
                    break;
                default:
                    out.put(key, value);
            }
        }
        out.putAll(sides);
        StringBuilder text = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : out.entrySet()) {
            if (!first) {
                text.append(',');
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return text.append(']').toString();
    }

    private static int indexOf(String direction) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i].equals(direction)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Placering
    // ------------------------------------------------------------------

    /**
     * Sætter strukturen ind med ankeret på (x, y, z). Ankeret selv sættes kun hvis {@code includeAnchor}
     * (for bygninger er det spillerens egen blok). Bagefter forbindes hegn og ruder med naboerne.
     */
    public void paste(World world, int x, int y, int z, int rotation, boolean includeAnchor) {
        List<Block> connect = new ArrayList<>();
        for (Cell cell : cells(rotation)) {
            if (cell.isAnchor() && !includeAnchor) {
                continue;
            }
            int by = y + cell.dy;
            if (by < world.getMinHeight() || by >= world.getMaxHeight()) {
                continue;
            }
            BlockData data = data(cell.ch, rotation);
            if (data == null) {
                continue;
            }
            Block block = world.getBlockAt(x + cell.dx, by, z + cell.dz);
            block.setBlockData(data, false);
            if (data instanceof MultipleFacing && isConnectable(data.getMaterial())) {
                connect.add(block);
            }
        }
        for (Block block : connect) {
            connect(block);
        }
    }

    /** Hegn og glasruder (ikke fx vinranker). */
    static boolean isConnectable(Material material) {
        String name = material.name();
        return name.endsWith("_FENCE") || name.endsWith("_PANE") || name.equals("IRON_BARS");
    }

    /** Forbinder et hegn/en rude med naboerne - som hvis en spiller havde sat den. */
    public static void connect(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof MultipleFacing)) {
            return;
        }
        MultipleFacing facing = (MultipleFacing) data;
        boolean fence = block.getType().name().endsWith("_FENCE");
        for (BlockFace side : SIDES) {
            if (!facing.getAllowedFaces().contains(side)) {
                continue;
            }
            Block neighbor = block.getRelative(side);
            String name = neighbor.getType().name();
            boolean joins = fence
                    ? name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")
                    : name.endsWith("_PANE") || name.endsWith("GLASS") || name.equals("IRON_BARS");
            facing.setFace(side, joins || neighbor.getType().isOccluding());
        }
        block.setBlockData(facing, false);
    }
}
