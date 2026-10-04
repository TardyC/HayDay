package dev.tardyc.hayday.model;

import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.Map;

/**
 * En bygningstype fra buildings.yml.
 */
public final class BuildingType {

    /** Hvad bygningen bruges til. */
    public enum Function {
        /** Laver varer ud fra opskrifter. */
        PRODUCTION,
        /** Spillerens vejbod, hvor andre kan købe varer. */
        ROADSIDE,
        /** Havnen, hvor skibet lægger til. */
        HARBOR
    }

    private final String id;
    private final String name;
    private final Function function;
    private final Material block;
    private final EntityType animal;
    private final double hologramHeight;
    private final double price;
    private final int level;
    private final int baseSlots;
    private final int maxSlots;
    private final double slotPrice;
    private final int maxPerPlayer;
    private final Map<String, Recipe> recipes;
    /** Navnet på bygningens struktur i structures.yml ("none" = kun én blok). */
    private String structureId;

    public BuildingType(String id, String name, Function function, Material block, EntityType animal, double hologramHeight, double price, int level,
                        int baseSlots, int maxSlots, double slotPrice, int maxPerPlayer, Map<String, Recipe> recipes) {
        this.id = id;
        this.name = name;
        this.function = function;
        this.block = block;
        this.animal = animal;
        this.hologramHeight = hologramHeight;
        this.price = price;
        this.level = level;
        this.baseSlots = baseSlots;
        this.maxSlots = Math.max(baseSlots, maxSlots);
        this.slotPrice = slotPrice;
        this.maxPerPlayer = maxPerPlayer;
        this.recipes = Collections.unmodifiableMap(recipes);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return Text.color(name);
    }

    public String getPlainName() {
        return Text.strip(name);
    }

    public Function getFunction() {
        return function;
    }

    public boolean isRoadside() {
        return function == Function.ROADSIDE;
    }

    public boolean isHarbor() {
        return function == Function.HARBOR;
    }

    /** Laver bygningen varer (og har dermed en produktionskø)? */
    public boolean isProduction() {
        return function == Function.PRODUCTION;
    }

    public Material getBlock() {
        return block;
    }

    public EntityType getAnimal() {
        return animal;
    }

    public double getHologramHeight() {
        return hologramHeight;
    }

    public double getPrice() {
        return price;
    }

    public int getLevel() {
        return level;
    }

    public int getBaseSlots() {
        return baseSlots;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public double getSlotPrice() {
        return slotPrice;
    }

    public int getMaxPerPlayer() {
        return maxPerPlayer;
    }

    public Map<String, Recipe> getRecipes() {
        return recipes;
    }

    public String getStructureId() {
        return structureId;
    }

    public void setStructureId(String structureId) {
        this.structureId = structureId == null || structureId.equalsIgnoreCase("none") ? null : structureId;
    }
}
