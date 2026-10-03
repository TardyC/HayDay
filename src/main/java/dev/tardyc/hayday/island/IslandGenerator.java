package dev.tardyc.hayday.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Laver HayDay-verdenen: græsøer med sandstrand i et turkis hav. Ingen huler, malme, strukturer eller mobs.
 */
public final class IslandGenerator extends ChunkGenerator {

    private final IslandLayout layout;

    public IslandGenerator(IslandLayout layout) {
        this.layout = layout;
    }

    public IslandLayout getLayout() {
        return layout;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int minY = Math.max(worldInfo.getMinHeight(), layout.getBottom());
        int maxY = Math.min(worldInfo.getMaxHeight() - 1, layout.getHeight());
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int lx = localX;
                int lz = localZ;
                layout.fillColumn((chunkX << 4) + localX, (chunkZ << 4) + localZ, minY, maxY,
                        (y, material) -> chunkData.setBlock(lx, y, lz, material));
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, layout.centerX(0) + 0.5, layout.getHeight() + 1, layout.centerZ(0) + 6.5, 180f, 0f);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                // Grønt græs på øerne og turkis vand i havet
                return layout.edgeDistance(x, z) >= 0 ? Biome.PLAINS : Biome.WARM_OCEAN;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return Arrays.asList(Biome.PLAINS, Biome.WARM_OCEAN);
            }
        };
    }
}
