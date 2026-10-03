package dev.tardyc.hayday.model;

import org.bukkit.block.data.BlockData;

import java.util.List;

/**
 * Hvordan en afgrøde gror på en mark.
 */
public final class CropType {

    private final String itemId;
    private final int level;
    private final int growSeconds;
    private final double seedPrice;
    private final int harvestAmount;
    private final int harvestXp;
    private final List<BlockData> stages;

    public CropType(String itemId, int level, int growSeconds, double seedPrice, int harvestAmount, int harvestXp, List<BlockData> stages) {
        this.itemId = itemId;
        this.level = level;
        this.growSeconds = growSeconds;
        this.seedPrice = seedPrice;
        this.harvestAmount = harvestAmount;
        this.harvestXp = harvestXp;
        this.stages = stages;
    }

    public String getItemId() {
        return itemId;
    }

    public int getLevel() {
        return level;
    }

    public int getGrowSeconds() {
        return growSeconds;
    }

    public long getGrowMillis() {
        return growSeconds * 1000L;
    }

    public double getSeedPrice() {
        return seedPrice;
    }

    public int getHarvestAmount() {
        return harvestAmount;
    }

    public int getHarvestXp() {
        return harvestXp;
    }

    public List<BlockData> getStages() {
        return stages;
    }

    /** Blokken der skal vises ved et givent fremskridt (0-1). Sidste stadie = klar til høst. */
    public BlockData stageFor(double progress, boolean ready) {
        if (ready || stages.size() == 1) {
            return stages.get(stages.size() - 1);
        }
        int growing = stages.size() - 1;
        int index = (int) Math.floor(Math.max(0, Math.min(0.9999, progress)) * growing);
        return stages.get(Math.min(index, growing - 1));
    }
}
