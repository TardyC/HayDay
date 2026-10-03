package dev.tardyc.hayday.registry;

import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Recipe;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Alle bygninger og opskrifter fra buildings.yml.
 */
public final class BuildingRegistry {

    private final Map<String, BuildingType> buildings = new LinkedHashMap<>();
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();

    public void load(YamlConfiguration config, ItemRegistry items, Logger log) {
        buildings.clear();
        recipes.clear();
        ConfigurationSection root = config.getConfigurationSection("buildings");
        if (root == null) {
            log.warning("buildings.yml indeholder ingen bygninger!");
            return;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            Material block = Material.matchMaterial(section.getString("block", "BARREL"));
            if (block == null || !block.isBlock() || !block.isItem()) {
                log.warning("Bygning '" + id + "' har en ugyldig blok: " + section.getString("block") + " - bruger BARREL.");
                block = Material.BARREL;
            }
            EntityType animal = null;
            String animalName = section.getString("animal");
            if (animalName != null && !animalName.isEmpty()) {
                try {
                    animal = EntityType.valueOf(animalName.toUpperCase(Locale.ROOT));
                    if (!animal.isSpawnable() || animal.getEntityClass() == null) {
                        animal = null;
                    }
                } catch (IllegalArgumentException e) {
                    animal = null;
                }
                if (animal == null) {
                    log.warning("Bygning '" + id + "' har et ugyldigt dyr: " + animalName);
                }
            }
            BuildingType.Function function = BuildingType.Function.PRODUCTION;
            String functionName = section.getString("function", "production");
            if ("roadside".equalsIgnoreCase(functionName)) {
                function = BuildingType.Function.ROADSIDE;
            } else if ("harbor".equalsIgnoreCase(functionName)) {
                function = BuildingType.Function.HARBOR;
            }
            int level = Math.max(1, section.getInt("level", 1));
            Map<String, Recipe> buildingRecipes = new LinkedHashMap<>();
            ConfigurationSection recipeRoot = section.getConfigurationSection("recipes");
            if (recipeRoot != null) {
                for (String rawRecipeId : recipeRoot.getKeys(false)) {
                    Recipe recipe = loadRecipe(id, rawRecipeId.toLowerCase(Locale.ROOT), recipeRoot.getConfigurationSection(rawRecipeId), items, log);
                    if (recipe == null) {
                        continue;
                    }
                    if (recipes.containsKey(recipe.getId())) {
                        log.warning("Opskriften '" + recipe.getId() + "' findes i flere bygninger - opskrifts-id'er skal være unikke.");
                        continue;
                    }
                    buildingRecipes.put(recipe.getId(), recipe);
                    recipes.put(recipe.getId(), recipe);
                    FarmItem output = items.get(recipe.getOutputId());
                    int unlock = Math.max(level, recipe.getLevel());
                    if (unlock < output.getUnlockLevel()) {
                        output.setUnlockLevel(unlock);
                    }
                }
            }
            int baseSlots = Math.max(1, Math.min(9, section.getInt("slots", 2)));
            buildings.put(id, new BuildingType(id,
                    section.getString("name", id),
                    function,
                    block,
                    animal,
                    section.getDouble("hologram-height", animal != null ? 2.2 : 1.25),
                    Math.max(0, section.getDouble("price", 100)),
                    level,
                    baseSlots,
                    Math.max(baseSlots, Math.min(9, section.getInt("max-slots", 6))),
                    Math.max(0, section.getDouble("slot-price", 250)),
                    Math.max(1, section.getInt("max-per-player", 1)),
                    buildingRecipes));
        }
    }

    private Recipe loadRecipe(String buildingId, String id, ConfigurationSection section, ItemRegistry items, Logger log) {
        if (section == null) {
            return null;
        }
        String output = section.getString("output", id).toLowerCase(Locale.ROOT);
        if (items.get(output) == null) {
            log.warning("Opskriften '" + id + "' i '" + buildingId + "' laver en ukendt vare: " + output);
            return null;
        }
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (ingredientSection != null) {
            for (String rawItem : ingredientSection.getKeys(false)) {
                String itemId = rawItem.toLowerCase(Locale.ROOT);
                if (items.get(itemId) == null) {
                    log.warning("Opskriften '" + id + "' bruger en ukendt ingrediens: " + itemId);
                    return null;
                }
                int amount = ingredientSection.getInt(rawItem, 1);
                if (amount > 0) {
                    ingredients.put(itemId, amount);
                }
            }
        }
        return new Recipe(id, buildingId, output,
                Math.max(1, section.getInt("amount", 1)),
                ingredients,
                Math.max(1, section.getInt("time", 60)),
                Math.max(0, section.getInt("xp", 1)),
                Math.max(1, section.getInt("level", 1)));
    }

    public BuildingType get(String id) {
        return id == null ? null : buildings.get(id.toLowerCase(Locale.ROOT));
    }

    public Recipe getRecipe(String id) {
        return id == null ? null : recipes.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<BuildingType> all() {
        return buildings.values();
    }

    public int size() {
        return buildings.size();
    }
}
