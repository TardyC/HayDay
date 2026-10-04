package dev.tardyc.hayday.model;

import dev.tardyc.hayday.hologram.FloatingIcon;
import dev.tardyc.hayday.hologram.Hologram;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * En placeret bygning med en produktionskø. Varerne laves én ad gangen i rækkefølge.
 */
public final class Building {

    private final UUID id;
    private final UUID owner;
    private String ownerName;
    private final String typeId;
    private final BlockPos pos;
    private int slots;
    private final List<QueueEntry> queue = new ArrayList<>();
    /** Strukturen bygningen er bygget som (null = én blok) og dens drejning (0-3). */
    private String structureId;
    private int rotation;

    private transient Hologram hologram;
    private transient FloatingIcon icon;
    private transient Entity animal;
    private transient int notifiedDone;
    /** Alle strukturens blokke (til klik, beskyttelse og fjernelse). */
    private transient List<BlockPos> structureBlocks = new ArrayList<>();
    /** Strukturens jord-blokke (bliver til græs igen når bygningen fjernes). */
    private transient List<BlockPos> groundBlocks = new ArrayList<>();

    public Building(UUID id, UUID owner, String ownerName, String typeId, BlockPos pos, int slots) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.typeId = typeId;
        this.pos = pos;
        this.slots = slots;
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

    public String getTypeId() {
        return typeId;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getSlots() {
        return slots;
    }

    public void setSlots(int slots) {
        this.slots = slots;
    }

    public List<QueueEntry> getQueue() {
        return queue;
    }

    public boolean isQueueFull() {
        return queue.size() >= slots;
    }

    public int countDone(long now) {
        int done = 0;
        for (QueueEntry entry : queue) {
            if (entry.isDone(now)) {
                done++;
            }
        }
        return done;
    }

    /** Varen der er under produktion lige nu, eller null. */
    public QueueEntry getActive(long now) {
        for (QueueEntry entry : queue) {
            if (!entry.isDone(now)) {
                return entry;
            }
        }
        return null;
    }

    /** Tilføjer en vare til køen. Den starter når den forrige er færdig. */
    public QueueEntry enqueue(String recipeId, long now, long duration) {
        long start = now;
        if (!queue.isEmpty()) {
            start = Math.max(now, queue.get(queue.size() - 1).getEndTime());
        }
        QueueEntry entry = new QueueEntry(recipeId, start + duration, duration);
        queue.add(entry);
        return entry;
    }

    /** Til admin: gør hele køen færdig med det samme. */
    public void finishAll(long now) {
        for (QueueEntry entry : queue) {
            if (!entry.isDone(now)) {
                entry.setEndTime(now);
            }
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

    public Entity getAnimal() {
        return animal;
    }

    public void setAnimal(Entity animal) {
        this.animal = animal;
    }

    public int getNotifiedDone() {
        return notifiedDone;
    }

    public void setNotifiedDone(int notifiedDone) {
        this.notifiedDone = notifiedDone;
    }

    public String getStructureId() {
        return structureId;
    }

    public void setStructureId(String structureId) {
        this.structureId = structureId;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation & 3;
    }

    public List<BlockPos> getStructureBlocks() {
        return structureBlocks;
    }

    public List<BlockPos> getGroundBlocks() {
        return groundBlocks;
    }
}
