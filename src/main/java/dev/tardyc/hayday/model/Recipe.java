package dev.tardyc.hayday.model;

import java.util.Collections;
import java.util.Map;

/**
 * En opskrift i en bygning: ingredienser -> vare.
 */
public final class Recipe {

    private final String id;
    private final String buildingId;
    private final String outputId;
    private final int outputAmount;
    private final Map<String, Integer> ingredients;
    private final int seconds;
    private final int xp;
    private final int level;

    public Recipe(String id, String buildingId, String outputId, int outputAmount, Map<String, Integer> ingredients, int seconds, int xp, int level) {
        this.id = id;
        this.buildingId = buildingId;
        this.outputId = outputId;
        this.outputAmount = outputAmount;
        this.ingredients = Collections.unmodifiableMap(ingredients);
        this.seconds = seconds;
        this.xp = xp;
        this.level = level;
    }

    public String getId() {
        return id;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public String getOutputId() {
        return outputId;
    }

    public int getOutputAmount() {
        return outputAmount;
    }

    public Map<String, Integer> getIngredients() {
        return ingredients;
    }

    public int getSeconds() {
        return seconds;
    }

    public long getMillis() {
        return seconds * 1000L;
    }

    public int getXp() {
        return xp;
    }

    public int getLevel() {
        return level;
    }
}
