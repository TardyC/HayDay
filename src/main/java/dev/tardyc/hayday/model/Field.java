package dev.tardyc.hayday.model;

import dev.tardyc.hayday.hologram.FloatingIcon;
import dev.tardyc.hayday.hologram.Hologram;

import java.util.UUID;

/**
 * En mark ejet af en spiller. Jordblokken ligger på {@link #getSoil()}, afgrøden på blokken ovenover.
 */
public final class Field {

    private final UUID id;
    private final UUID owner;
    private String ownerName;
    private final BlockPos soil;
    private String cropId;
    private long plantedAt;
    private long readyAt;

    private transient Hologram hologram;
    private transient FloatingIcon icon;
    private transient boolean notified;

    public Field(UUID id, UUID owner, String ownerName, BlockPos soil) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.soil = soil;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public BlockPos getSoil() {
        return soil;
    }

    public BlockPos getCropPos() {
        return soil.up();
    }

    public String getCropId() {
        return cropId;
    }

    public long getPlantedAt() {
        return plantedAt;
    }

    public long getReadyAt() {
        return readyAt;
    }

    public boolean isEmpty() {
        return cropId == null;
    }

    public boolean isReady(long now) {
        return cropId != null && now >= readyAt;
    }

    public double getProgress(long now) {
        if (cropId == null) {
            return 0;
        }
        long total = Math.max(1, readyAt - plantedAt);
        return Math.max(0, Math.min(1, (now - plantedAt) / (double) total));
    }

    public void plant(String cropId, long now, long growMillis) {
        this.cropId = cropId;
        this.plantedAt = now;
        this.readyAt = now + growMillis;
        this.notified = false;
    }

    /** Bruges ved indlæsning fra fil. */
    public void restore(String cropId, long plantedAt, long readyAt) {
        this.cropId = cropId;
        this.plantedAt = plantedAt;
        this.readyAt = readyAt;
    }

    public void clear() {
        this.cropId = null;
        this.plantedAt = 0;
        this.readyAt = 0;
        this.notified = false;
    }

    /** Til admin: gør afgrøden færdig med det samme. */
    public void finish(long now) {
        if (cropId != null) {
            this.readyAt = now;
        }
    }

    /** Det svævende ikon over marken/bygningen (null hvis intet vises). */
    public FloatingIcon getIcon() {
        return icon;
    }

    public void setIcon(FloatingIcon icon) {
        this.icon = icon;
    }

    public Hologram getHologram() {
        return hologram;
    }

    public void setHologram(Hologram hologram) {
        this.hologram = hologram;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }
}
