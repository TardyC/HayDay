package dev.tardyc.hayday.island;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.BlockPos;
import dev.tardyc.hayday.model.Field;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Bygger på øerne: stuehuset, stien, træer, blomster og startmarker på nye øer, pynt på torvet -
 * og tegner en ø helt om (når en admin nulstiller eller sletter den).
 */
final class IslandBuilder {

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
        return new Location(world, layout.centerX(island.getGridX()) + 2.5, layout.getHeight() + 3.0,
                layout.maxZ(island.getGridZ()) - 5.5);
    }

    /** Bygger startgården og returnerer hvor mange marker der blev lagt. */
    int buildStarter(World world, IslandLayout layout, Island island, int fields) {
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
        set(world, cx + 2, h + 1, maxZ - 6, "spruce_fence");
        set(world, cx + 2, h + 2, maxZ - 6, "lantern");

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
    // Torvet (spawn)
    // ------------------------------------------------------------------

    void buildSpawn(World world, IslandLayout layout) {
        int h = layout.getHeight();
        int c = layout.centerX(0);
        for (int dx = -3; dx <= 3; dx += 6) {
            for (int dz = -3; dz <= 3; dz += 6) {
                set(world, c + dx, h + 1, c + dz, "spruce_fence");
                set(world, c + dx, h + 2, c + dz, "lantern");
            }
        }
        set(world, c, h + 1, c, "hay_block");
        tree(world, c - 12, h + 1, c - 12);
        tree(world, c + 12, h + 1, c - 12);
        tree(world, c - 12, h + 1, c + 12);
        tree(world, c + 12, h + 1, c + 12);
        Random random = new Random(42);
        for (int i = 0; i < 50; i++) {
            int x = c - 20 + random.nextInt(41);
            int z = c - 20 + random.nextInt(41);
            if (Math.abs(x - c) > 2 && Math.abs(z - c) > 2 && layout.edgeDistance(x, z) >= 4) {
                flower(world, x, h, z, FLOWERS[random.nextInt(FLOWERS.length)]);
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
        if (world.getBlockAt(x, y - 1, z).getType() == Material.GRASS_BLOCK && world.getBlockAt(x, y, z).getType().isAir()) {
            world.generateTree(new Location(world, x + 0.5, y, z + 0.5), TreeType.TREE);
        }
    }
}
