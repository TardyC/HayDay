package dev.tardyc.hayday.island;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.BlockPos;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.structure.Structure;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Bygger på øerne: stuehuset, stien, træer, blomster og startmarker på nye øer, pynt på torvet -
 * og tegner en ø helt om (når en admin nulstiller eller sletter den).
 */
final class IslandBuilder {

    /** Strukturerne den realistiske gård er bygget af. */
    private static final String[] FARM_STRUCTURES = {"farmhouse", "barn", "silo", "orderboard", "mailbox", "fieldpatch",
            "pond", "dock"};
    private static final String[] FLOWERS = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet",
            "red_tulip", "orange_tulip", "pink_tulip", "white_tulip", "short_grass", "short_grass", "short_grass"};
    /** Så mange blokke tjekkes pr. tick når en ø tegnes om. */
    private static final int BLOCKS_PER_TICK = 12000;

    private final HayDayPlugin plugin;

    IslandBuilder(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Spillerens ø
    // ------------------------------------------------------------------

    /** Signet ved stranden hvor øens hologram svæver. */
    static Location signLocation(World world, IslandLayout layout, Island island) {
        return new Location(world, layout.centerX(island.getGridX()) + 2.5, layout.getHeight() + 4.4,
                layout.maxZ(island.getGridZ()) - 5.5);
    }

    /** Bygger startgården og returnerer hvor mange marker der blev lagt. */
    int buildStarter(World world, IslandLayout layout, Island island, int fields) {
        if (canBuildFarm(layout)) {
            island.setStyle(2);
            return buildFarm(world, layout, island, fields);
        }
        island.setStyle(1);
        return buildSimple(world, layout, island, fields);
    }

    /** Den realistiske gård kræver en ø på mindst 44x44 og at bygningerne findes i structures.yml. */
    boolean canBuildFarm(IslandLayout layout) {
        if (layout.getSize() < 44) {
            return false;
        }
        for (String id : FARM_STRUCTURES) {
            if (plugin.getStructures().get(id) == null) {
                return false;
            }
        }
        return true;
    }

    /** Den første, simple gård (små øer eller hvis structures.yml mangler noget). */
    private int buildSimple(World world, IslandLayout layout, Island island, int fields) {
        int h = layout.getHeight();
        int gx = island.getGridX();
        int gz = island.getGridZ();
        int minX = layout.minX(gx);
        int minZ = layout.minZ(gz);
        int maxX = layout.maxX(gx);
        int maxZ = layout.maxZ(gz);
        int cx = layout.centerX(gx);
        int cz = layout.centerZ(gz);
        boolean house = layout.getSize() >= 32;
        int houseZ = cz - 10;

        if (house) {
            buildHouse(world, cx, h, houseZ);
        }
        // Sti fra døren ned til stranden
        for (int z = house ? houseZ + 7 : cz; z <= maxZ - 3; z++) {
            replaceGrass(world, cx, h, z, "dirt_path");
        }
        // Skilt med lygte ved indgangen
        lamp(world, cx + 2, h, maxZ - 6);

        // Startmarker ved siden af stien
        int placed = 0;
        for (int i = 0; i < Math.min(12, fields); i++) {
            int x = cx - 3 - (i % 4);
            int z = cz + 1 + i / 4;
            Block soil = world.getBlockAt(x, h, z);
            if (plugin.getFarm().getFieldAt(soil) != null) {
                continue;
            }
            world.getBlockAt(x, h + 1, z).setType(Material.AIR, false);
            Field field = plugin.getFarm().createField(island.getOwner(), island.getOwnerName(), BlockPos.of(soil));
            plugin.getFarm().refresh(field);
            placed++;
        }

        // Træer og blomster
        Random random = new Random(island.key() * 31 + 7);
        tree(world, minX + 8, h + 1, minZ + 8);
        tree(world, maxX - 8, h + 1, minZ + 9);
        tree(world, maxX - 9, h + 1, cz + 6);
        for (int i = 0; i < 40; i++) {
            int x = minX + 4 + random.nextInt(Math.max(1, layout.getSize() - 8));
            int z = minZ + 4 + random.nextInt(Math.max(1, layout.getSize() - 8));
            boolean nearPath = Math.abs(x - cx) <= 4 && z >= houseZ - 2;
            boolean nearFields = x >= cx - 8 && x <= cx - 1 && z >= cz - 1 && z <= cz + 5;
            if (nearPath || nearFields || layout.edgeDistance(x, z) < 4) {
                continue;
            }
            flower(world, x, h, z, FLOWERS[random.nextInt(FLOWERS.length)]);
        }
        return placed;
    }

    /**
     * Et rødt stuehus på 7x7 med gavltag, skorsten, vinduer og blomster foran.
     * Huset står med bagvæggen på z0 og døren mod syd (mod stranden).
     */
    private void buildHouse(World w, int cx, int h, int z0) {
        for (int x = -3; x <= 3; x++) {
            for (int z = 0; z <= 6; z++) {
                set(w, cx + x, h, z0 + z, "spruce_planks");
                for (int y = 1; y <= 8; y++) {
                    set(w, cx + x, h + y, z0 + z, "air");
                }
                boolean edge = Math.abs(x) == 3 || z == 0 || z == 6;
                if (!edge) {
                    continue;
                }
                boolean corner = Math.abs(x) == 3 && (z == 0 || z == 6);
                for (int y = 1; y <= 3; y++) {
                    set(w, cx + x, h + y, z0 + z, corner ? "stripped_dark_oak_log" : "red_terracotta");
                }
            }
        }
        // Vinduer
        set(w, cx - 2, h + 2, z0 + 6, "glass");
        set(w, cx + 2, h + 2, z0 + 6, "glass");
        set(w, cx, h + 2, z0, "glass");
        set(w, cx - 3, h + 2, z0 + 3, "glass");
        set(w, cx + 3, h + 2, z0 + 3, "glass");
        // Dør
        set(w, cx, h + 1, z0 + 6, "oak_door[facing=south,half=lower,hinge=left]");
        set(w, cx, h + 2, z0 + 6, "oak_door[facing=south,half=upper,hinge=left]");
        // Gavle
        for (int layer = 0; layer < 4; layer++) {
            for (int z = layer; z <= 6 - layer; z++) {
                set(w, cx - 3, h + 4 + layer, z0 + z, "spruce_planks");
                set(w, cx + 3, h + 4 + layer, z0 + z, "spruce_planks");
            }
        }
        // Taget: trapper der mødes i en rygning over midten
        for (int x = -4; x <= 4; x++) {
            for (int layer = 0; layer < 4; layer++) {
                set(w, cx + x, h + 4 + layer, z0 - 1 + layer, "mangrove_stairs[facing=south]");
                set(w, cx + x, h + 4 + layer, z0 + 7 - layer, "mangrove_stairs[facing=north]");
            }
            set(w, cx + x, h + 8, z0 + 3, "mangrove_slab[type=bottom]");
        }
        // Skorsten med røg
        for (int y = 4; y <= 8; y++) {
            set(w, cx + 2, h + y, z0 + 1, "bricks");
        }
        set(w, cx + 2, h + 9, z0 + 1, "campfire[lit=true,signal_fire=false]");
        // Lidt indbo
        set(w, cx - 2, h + 1, z0 + 1, "crafting_table");
        set(w, cx - 2, h + 2, z0 + 1, "potted_red_tulip");
        set(w, cx + 2, h + 1, z0 + 5, "lantern");
        // Blomster foran vinduerne
        flower(w, cx - 2, h, z0 + 7, "red_tulip");
        flower(w, cx - 1, h, z0 + 7, "oxeye_daisy");
        flower(w, cx + 1, h, z0 + 7, "oxeye_daisy");
        flower(w, cx + 2, h, z0 + 7, "red_tulip");
    }

    // ------------------------------------------------------------------
    // Den realistiske gård (stil 2)
    // ------------------------------------------------------------------

    /** Lokale koordinater er tegnet til en 48x48-ø; på større øer rykkes alt ind mod midten. */
    static int x(IslandLayout layout, Island island, int local) {
        return layout.centerX(island.getGridX()) + (local - 24);
    }

    static int z(IslandLayout layout, Island island, int local) {
        return layout.centerZ(island.getGridZ()) + (local - 24);
    }

    private void paste(World world, String id, int x, int y, int z) {
        Structure structure = plugin.getStructures().get(id);
        if (structure != null) {
            structure.paste(world, x, y, z, 0, true);
        }
    }

    private int buildFarm(World world, IslandLayout layout, Island island, int fields) {
        int h = layout.getHeight();
        Random random = new Random(island.key() * 31 + 11);

        // Stier først, så bygningerne kan stå oven på kanterne
        for (int lz = 19; lz <= 44; lz++) {
            for (int lx = 23; lx <= 25; lx++) {
                path(world, x(layout, island, lx), h, z(layout, island, lz), random, 0.12);
            }
            if (random.nextDouble() < 0.3) {
                path(world, x(layout, island, random.nextBoolean() ? 22 : 26), h, z(layout, island, lz), random, 0.5);
            }
        }
        for (int lx = 11; lx <= 30; lx++) {
            for (int lz = 18; lz <= 20; lz++) {
                path(world, x(layout, island, lx), h, z(layout, island, lz), random, 0.35);
            }
        }
        for (int lx = 20; lx <= 22; lx++) {
            path(world, x(layout, island, lx), h, z(layout, island, 30), random, 0.2);
        }

        // Gårdens bygninger
        paste(world, "farmhouse", x(layout, island, 12), h, z(layout, island, 16));
        for (Landmark landmark : Landmark.values()) {
            paste(world, landmark.structure(), x(layout, island, landmark.x()), h, z(layout, island, landmark.z()));
        }
        paste(world, "fieldpatch", x(layout, island, 19), h, z(layout, island, 30));
        paste(world, "pond", x(layout, island, 35), h, z(layout, island, 32));
        paste(world, "dock", x(layout, island, 24), h, z(layout, island, 45));

        // Startmarker på den pløjede jord, tættest på lågen først
        int placed = 0;
        Structure patch = plugin.getStructures().get("fieldpatch");
        if (patch != null && fields > 0) {
            int ax = x(layout, island, 19);
            int az = z(layout, island, 30);
            List<Structure.Cell> spots = new ArrayList<>();
            for (Structure.Cell cell : patch.cells(0)) {
                if (cell.ch == Structure.FIELD) {
                    spots.add(cell);
                }
            }
            spots.sort(Comparator.comparingInt(cell -> Math.abs(cell.dz) * 3 - cell.dx));
            for (Structure.Cell cell : spots) {
                if (placed >= Math.min(12, fields)) {
                    break;
                }
                Block soil = world.getBlockAt(ax + cell.dx, h + cell.dy, az + cell.dz);
                if (plugin.getFarm().getFieldAt(soil) != null) {
                    continue;
                }
                Field field = plugin.getFarm().createField(island.getOwner(), island.getOwnerName(), BlockPos.of(soil));
                plugin.getFarm().refresh(field);
                placed++;
            }
        }

        // Lygtepæle langs vejen og skiltet ved bryggen
        int[][] lamps = {{22, 26}, {26, 26}, {22, 34}, {26, 34}};
        for (int[] lamp : lamps) {
            lamp(world, x(layout, island, lamp[0]), h, z(layout, island, lamp[1]));
        }
        lamp(world, layout.centerX(island.getGridX()) + 2, h, layout.maxZ(island.getGridZ()) - 6);

        // Natur: træer, buske, blomster, højt græs, sten og drivtømmer
        int[][] oaks = {{6, 21}, {43, 25}, {20, 5}, {5, 6}};
        int[][] birches = {{9, 42}, {41, 42}};
        for (int[] tree : oaks) {
            tree(world, x(layout, island, tree[0]), h + 1, z(layout, island, tree[1]), TreeType.TREE);
        }
        for (int[] tree : birches) {
            tree(world, x(layout, island, tree[0]), h + 1, z(layout, island, tree[1]), TreeType.BIRCH);
        }
        int[][] bushes = {{6, 10}, {6, 14}, {18, 9}, {25, 8}, {26, 14}, {44, 18}, {17, 38}, {3, 30}, {44, 36}};
        for (int[] bush : bushes) {
            bush(world, x(layout, island, bush[0]), h, z(layout, island, bush[1]), random);
        }
        int[][] meadows = {{10, 23}, {44, 31}, {15, 41}, {33, 44}, {4, 24}, {39, 20}};
        for (int[] meadow : meadows) {
            for (int i = 0; i < 9; i++) {
                flower(world, x(layout, island, meadow[0]) + random.nextInt(5) - 2, h,
                        z(layout, island, meadow[1]) + random.nextInt(5) - 2, FLOWERS[random.nextInt(FLOWERS.length)]);
            }
        }
        int minX = layout.minX(island.getGridX());
        int minZ = layout.minZ(island.getGridZ());
        for (int i = 0; i < 220; i++) {
            int gx = minX + 3 + random.nextInt(layout.getSize() - 6);
            int gz = minZ + 3 + random.nextInt(layout.getSize() - 6);
            if (layout.edgeDistance(gx, gz) < 3) {
                continue;
            }
            double roll = random.nextDouble();
            if (roll < 0.18) {
                tallGrass(world, gx, h, gz);
            } else {
                flower(world, gx, h, gz, roll < 0.25 ? "fern" : "short_grass");
            }
        }
        for (int i = 0; i < 60; i++) {
            int gx = minX + random.nextInt(layout.getSize());
            int gz = minZ + random.nextInt(layout.getSize());
            int edge = layout.edgeDistance(gx, gz);
            if (edge < 1 || edge > 2 || !world.getBlockAt(gx, h + 1, gz).getType().isAir()
                    || world.getBlockAt(gx, h, gz).getType() != Material.SAND) {
                continue;
            }
            double roll = random.nextDouble();
            String rock = roll < 0.35 ? "cobblestone" : roll < 0.6 ? "mossy_cobblestone" : roll < 0.8 ? "andesite"
                    : roll < 0.9 ? "stripped_oak_log[axis=x]" : "dead_bush";
            set(world, gx, h + 1, gz, rock);
        }
        return placed;
    }

    /** Sti: jordsti med lidt grus og grov jord, kun hvor der er græs. */
    private void path(World world, int x, int y, int z, Random random, double rough) {
        Material ground = world.getBlockAt(x, y, z).getType();
        if (ground != Material.GRASS_BLOCK && ground != Material.DIRT) {
            return;
        }
        double roll = random.nextDouble();
        set(world, x, y, z, roll < rough * 0.6 ? "coarse_dirt" : roll < rough ? "gravel" : "dirt_path");
        Material above = world.getBlockAt(x, y + 1, z).getType();
        if (!above.isAir() && world.getBlockAt(x, y + 1, z).isPassable()) {
            set(world, x, y + 1, z, "air");
        }
    }

    private void lamp(World world, int x, int y, int z) {
        set(world, x, y + 1, z, "spruce_fence");
        set(world, x, y + 2, z, "spruce_fence");
        set(world, x, y + 3, z, "lantern");
    }

    /** En lille busk af blade (1-3 blokke). */
    private void bush(World world, int x, int y, int z, Random random) {
        String leaves = random.nextBoolean() ? "azalea_leaves[persistent=true]" : "flowering_azalea_leaves[persistent=true]";
        if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK || !world.getBlockAt(x, y + 1, z).getType().isAir()) {
            return;
        }
        set(world, x, y + 1, z, leaves);
        if (random.nextBoolean() && world.getBlockAt(x + 1, y + 1, z).getType().isAir()
                && world.getBlockAt(x + 1, y, z).getType() == Material.GRASS_BLOCK) {
            set(world, x + 1, y + 1, z, "oak_leaves[persistent=true]");
        }
        if (random.nextInt(3) == 0 && world.getBlockAt(x, y + 2, z).getType().isAir()) {
            set(world, x, y + 2, z, "oak_leaves[persistent=true]");
        }
    }

    private void tallGrass(World world, int x, int groundY, int z) {
        if (world.getBlockAt(x, groundY, z).getType() == Material.GRASS_BLOCK
                && world.getBlockAt(x, groundY + 1, z).getType().isAir()
                && world.getBlockAt(x, groundY + 2, z).getType().isAir()) {
            set(world, x, groundY + 1, z, "tall_grass[half=lower]");
            set(world, x, groundY + 2, z, "tall_grass[half=upper]");
        }
    }

    // ------------------------------------------------------------------
    // Torvet (spawn)
    // ------------------------------------------------------------------

    void buildSpawn(World world, IslandLayout layout) {
        int h = layout.getHeight();
        int c = layout.centerX(0);
        Random random = new Random(42);
        if (plugin.getStructures().get("fountain") != null) {
            paste(world, "fountain", c, h, c);
        } else {
            set(world, c, h + 1, c, "hay_block");
        }
        for (int dx = -5; dx <= 5; dx += 10) {
            for (int dz = -5; dz <= 5; dz += 10) {
                lamp(world, c + dx, h, c + dz);
            }
        }
        Structure stall = plugin.getStructures().get("vejbod");
        if (stall != null) {
            stall.paste(world, c - 9, h + 1, c + 5, 0, true);
            stall.paste(world, c + 9, h + 1, c + 5, 0, true);
        }
        paste(world, "dock", c, h, layout.maxZ(0) - 2);
        tree(world, c - 12, h + 1, c - 12, TreeType.TREE);
        tree(world, c + 12, h + 1, c - 12, TreeType.BIRCH);
        tree(world, c - 12, h + 1, c + 12, TreeType.BIRCH);
        tree(world, c + 12, h + 1, c + 12, TreeType.TREE);
        for (int i = 0; i < 160; i++) {
            int x = c - 20 + random.nextInt(41);
            int z = c - 20 + random.nextInt(41);
            if (Math.abs(x - c) <= 7 && Math.abs(z - c) <= 7 || Math.abs(x - c) <= 1 || Math.abs(z - c) <= 1
                    || layout.edgeDistance(x, z) < 4) {
                continue;
            }
            double roll = random.nextDouble();
            if (roll < 0.35) {
                flower(world, x, h, z, FLOWERS[random.nextInt(FLOWERS.length)]);
            } else if (roll < 0.45) {
                tallGrass(world, x, h, z);
            } else {
                flower(world, x, h, z, "short_grass");
            }
        }
    }

    // ------------------------------------------------------------------
    // Tegn en ø om
    // ------------------------------------------------------------------

    /** Sætter øen tilbage til hvordan generatoren lavede den - lidt ad gangen, så serveren ikke lagger. */
    void repaint(World world, IslandLayout layout, int gx, int gz, Runnable done) {
        int minX = layout.minX(gx) - 2;
        int maxX = layout.maxX(gx) + 2;
        int minZ = layout.minZ(gz) - 2;
        int maxZ = layout.maxZ(gz) + 2;
        int bottom = Math.max(world.getMinHeight(), layout.getBottom());
        int maxY = world.getMaxHeight() - 1;
        new BukkitRunnable() {
            private int x = minX;
            private int z = minZ;

            @Override
            public void run() {
                int budget = BLOCKS_PER_TICK;
                while (budget > 0) {
                    if (x > maxX) {
                        cancel();
                        done.run();
                        return;
                    }
                    int top = Math.min(maxY, Math.max(layout.getHeight() + 1, world.getHighestBlockYAt(x, z)));
                    for (int y = bottom; y <= top; y++) {
                        Material want = layout.material(x, y, z);
                        Block block = world.getBlockAt(x, y, z);
                        Material have = block.getType();
                        if (want == null) {
                            if (!have.isAir()) {
                                block.setType(Material.AIR, false);
                            }
                        } else if (have != want) {
                            block.setType(want, false);
                        }
                    }
                    budget -= top - bottom + 1;
                    if (++z > maxZ) {
                        z = minZ;
                        x++;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------
    // Hjælpere
    // ------------------------------------------------------------------

    private void set(World world, int x, int y, int z, String data) {
        try {
            world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData("minecraft:" + data), false);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().fine("Ukendt blok i øens pynt: " + data);
        }
    }

    private void replaceGrass(World world, int x, int y, int z, String data) {
        if (world.getBlockAt(x, y, z).getType() == Material.GRASS_BLOCK) {
            set(world, x, y, z, data);
        }
    }

    /** Sætter en blomst på græsset hvis der er plads. */
    private void flower(World world, int x, int groundY, int z, String flower) {
        if (world.getBlockAt(x, groundY, z).getType() == Material.GRASS_BLOCK
                && world.getBlockAt(x, groundY + 1, z).getType().isAir()) {
            set(world, x, groundY + 1, z, flower);
        }
    }

    private void tree(World world, int x, int y, int z) {
        tree(world, x, y, z, TreeType.TREE);
    }

    private void tree(World world, int x, int y, int z, TreeType type) {
        if (world.getBlockAt(x, y - 1, z).getType() == Material.GRASS_BLOCK && world.getBlockAt(x, y, z).getType().isAir()) {
            world.generateTree(new Location(world, x + 0.5, y, z + 0.5), type);
        }
    }
}
