package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Messages;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.gui.BuildingMenu;
import dev.tardyc.hayday.gui.RoadsideMenu;
import dev.tardyc.hayday.gui.SeedMenu;
import dev.tardyc.hayday.model.BlockPos;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.CropType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.QueueEntry;
import dev.tardyc.hayday.model.Recipe;
import dev.tardyc.hayday.util.Effects;
import dev.tardyc.hayday.util.PlaceableItems;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alt hvad en spiller kan gøre på sin gård: placere, plante, høste, producere, hente og købe.
 */
public final class FarmService {

    /** Resultat af et plante-forsøg. */
    public enum PlantResult {
        OK,
        LOCKED,
        NO_FUNDS,
        NOT_EMPTY
    }

    private final HayDayPlugin plugin;

    public FarmService(HayDayPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.getMessages();
    }

    private Settings settings() {
        return plugin.getSettings();
    }

    private FarmManager farm() {
        return plugin.getFarm();
    }

    /** Må spilleren bruge noget der ejes af {@code owner}? Sender en besked hvis ikke. */
    public boolean canUse(Player player, UUID owner, String ownerName) {
        if (player.getUniqueId().equals(owner) || player.hasPermission("hayday.bypass")) {
            return true;
        }
        msg().send(player, "general.not-yours", "owner", ownerName);
        Sounds.play(player, Sounds.ERROR);
        return false;
    }

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------

    /** Giver startpakken første gang en spiller bruger HayDay. */
    public void ensureStarted(Player player) {
        PlayerData data = plugin.getPlayers().get(player);
        if (data.isStarted()) {
            return;
        }
        data.setStarted(true);
        for (Map.Entry<String, Integer> entry : settings().starterStorage.entrySet()) {
            if (plugin.getItems().get(entry.getKey()) != null) {
                data.addItem(entry.getKey(), entry.getValue());
            }
        }
        if (settings().starterFields > 0) {
            PlaceableItems.give(player, PlaceableItems.field(settings().starterFields));
        }
        plugin.getOrders().ensure(data);
        plugin.getLeaderboard().update(data);
        msg().sendList(player, "welcome", "fields", settings().starterFields);
        Sounds.play(player, Sounds.LEVEL_UP, 1.2f);
    }

    // ------------------------------------------------------------------
    // Placering
    // ------------------------------------------------------------------

    /** Kaldes når en spiller placerer en Mark. Returnerer false hvis placeringen skal annulleres. */
    public boolean placeField(Player player, Block block) {
        if (!player.hasPermission("hayday.use")) {
            msg().send(player, "general.no-permission");
            return false;
        }
        if (!settings().isWorldAllowed(block.getWorld())) {
            msg().send(player, "general.world-not-allowed");
            return false;
        }
        if (!block.getRelative(BlockFace.UP).getType().isAir()) {
            msg().send(player, "field.need-air");
            return false;
        }
        PlayerData data = plugin.getPlayers().get(player);
        int placed = farm().countFields(player.getUniqueId());
        int max = settings().maxFields(data.getLevel());
        if (placed >= max && !player.hasPermission("hayday.bypass")) {
            msg().send(player, "field.limit", "max", max);
            return false;
        }
        ensureStarted(player);
        Field field = farm().createField(player, BlockPos.of(block));
        Bukkit.getScheduler().runTask(plugin, () -> farm().refresh(field));
        msg().send(player, "field.placed", "count", placed + 1, "max", max);
        Sounds.play(player, Sounds.PLACE);
        return true;
    }

    /** Kaldes når en spiller placerer en bygning. Returnerer false hvis placeringen skal annulleres. */
    public boolean placeBuilding(Player player, BuildingType type, Block block) {
        if (!player.hasPermission("hayday.use")) {
            msg().send(player, "general.no-permission");
            return false;
        }
        if (!settings().isWorldAllowed(block.getWorld())) {
            msg().send(player, "general.world-not-allowed");
            return false;
        }
        if (farm().countBuildings(player.getUniqueId(), type.getId()) >= type.getMaxPerPlayer()) {
            msg().send(player, "building.already-owned", "building", type.getName());
            return false;
        }
        if (type.getAnimal() != null && !block.getRelative(BlockFace.UP).getType().isAir()) {
            msg().send(player, "building.need-air");
            return false;
        }
        ensureStarted(player);
        farm().createBuilding(player, type, BlockPos.of(block));
        msg().send(player, "building.placed", "building", type.getName());
        Sounds.play(player, Sounds.PLACE);
        return true;
    }

    // ------------------------------------------------------------------
    // Marker
    // ------------------------------------------------------------------

    public void clickField(Player player, Field field) {
        if (!canUse(player, field.getOwner(), field.getOwnerName())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (player.isSneaking()) {
            if (!harvestNearby(player, field.getSoil().toCenter())) {
                if (field.isEmpty()) {
                    new SeedMenu(plugin, player, field).open();
                } else {
                    msg().send(player, "field.nothing-ready");
                }
            }
            return;
        }
        if (field.isEmpty()) {
            new SeedMenu(plugin, player, field).open();
        } else if (field.isReady(now)) {
            harvestSingle(player, field);
        } else {
            sendGrowing(player, field, now);
        }
    }

    public void breakField(Player player, Field field) {
        if (!canUse(player, field.getOwner(), field.getOwnerName())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (player.isSneaking()) {
            if (!field.isEmpty()) {
                msg().send(player, "field.remove-not-empty");
                Sounds.play(player, Sounds.ERROR);
                return;
            }
            farm().removeField(field);
            if (PlaceableItems.give(player, PlaceableItems.field(1))) {
                msg().send(player, "general.inventory-full");
            }
            msg().send(player, "field.removed");
            Sounds.play(player, Sounds.HARVEST, 0.8f);
        } else if (field.isReady(now)) {
            harvestSingle(player, field);
        } else if (field.isEmpty()) {
            msg().send(player, "field.remove-hint");
        } else {
            sendGrowing(player, field, now);
        }
    }

    private void sendGrowing(Player player, Field field, long now) {
        FarmItem item = plugin.getItems().get(field.getCropId());
        msg().send(player, "field.growing", "crop", item == null ? field.getCropId() : item.getName(),
                "time", Text.timeMillis(field.getReadyAt() - now));
    }

    public PlantResult tryPlant(Player player, Field field, FarmItem crop) {
        if (!field.isEmpty()) {
            return PlantResult.NOT_EMPTY;
        }
        PlayerData data = plugin.getPlayers().get(player);
        CropType type = crop.getCrop();
        if (type == null || data.getLevel() < type.getLevel()) {
            return PlantResult.LOCKED;
        }
        if (settings().useSiloCrop && data.getAmount(crop.getId()) > 0) {
            data.removeItem(crop.getId(), 1);
        } else if (type.getSeedPrice() > 0) {
            if (!plugin.getEconomy().has(player, type.getSeedPrice()) || !plugin.getEconomy().withdraw(player, type.getSeedPrice())) {
                return PlantResult.NO_FUNDS;
            }
        }
        field.plant(crop.getId(), System.currentTimeMillis(), type.getGrowMillis());
        farm().markDirty();
        farm().refresh(field);
        plugin.getAnimations().plant(field.getCropPos().toCenter(), crop.getDisplayStack());
        return PlantResult.OK;
    }

    /** Planter på én mark og sender den rigtige besked. */
    public boolean plant(Player player, Field field, FarmItem crop) {
        if (!canUse(player, field.getOwner(), field.getOwnerName())) {
            return false;
        }
        PlantResult result = tryPlant(player, field, crop);
        switch (result) {
            case OK:
                msg().send(player, "field.planted", "crop", crop.getName(), "time", Text.time(crop.getCrop().getGrowSeconds()));
                Sounds.play(player, Sounds.PLANT);
                return true;
            case LOCKED:
                msg().send(player, "field.crop-locked", "crop", crop.getName(), "level", crop.getCrop().getLevel());
                break;
            case NO_FUNDS:
                msg().send(player, "field.cannot-afford-seed", "crop", crop.getName(),
                        "price", plugin.getEconomy().format(crop.getCrop().getSeedPrice()));
                break;
            default:
                msg().send(player, "field.not-empty");
                break;
        }
        Sounds.play(player, Sounds.ERROR);
        return false;
    }

    /** Planter afgrøden på alle spillerens tomme marker i nærheden. */
    public int plantNearby(Player player, FarmItem crop, Location center) {
        PlayerData data = plugin.getPlayers().get(player);
        if (crop.getCrop() == null || data.getLevel() < crop.getCrop().getLevel()) {
            msg().send(player, "field.crop-locked", "crop", crop.getName(), "level", crop.getCrop() == null ? "?" : crop.getCrop().getLevel());
            Sounds.play(player, Sounds.ERROR);
            return 0;
        }
        int planted = 0;
        for (Field field : nearbyFields(player, center)) {
            if (!field.isEmpty()) {
                continue;
            }
            PlantResult result = tryPlant(player, field, crop);
            if (result == PlantResult.OK) {
                planted++;
            } else if (result != PlantResult.NOT_EMPTY) {
                break;
            }
        }
        if (planted > 0) {
            msg().send(player, "field.planted-many", "crop", crop.getName(), "count", planted);
            Sounds.play(player, Sounds.PLANT);
        } else {
            msg().send(player, "field.plant-none");
            Sounds.play(player, Sounds.ERROR);
        }
        return planted;
    }

    private List<Field> nearbyFields(Player player, Location center) {
        double radius = settings().massRadius;
        double radiusSquared = radius * radius;
        List<Field> list = new ArrayList<>();
        for (Field field : farm().getFields(player.getUniqueId())) {
            if (field.getSoil().distanceSquared(center) <= radiusSquared) {
                list.add(field);
            }
        }
        list.sort(Comparator.comparingDouble(field -> field.getSoil().distanceSquared(center)));
        return list;
    }

    public void harvestSingle(Player player, Field field) {
        Map<String, Integer> collected = new LinkedHashMap<>();
        int[] xp = new int[1];
        HarvestResult result = harvest(player, field, collected, xp);
        if (result == HarvestResult.FULL) {
            msg().send(player, "field.silo-full");
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (result == HarvestResult.OK) {
            Map.Entry<String, Integer> entry = collected.entrySet().iterator().next();
            FarmItem item = plugin.getItems().get(entry.getKey());
            msg().send(player, "field.harvested", "amount", entry.getValue(), "crop", item.getName(), "xp", xp[0]);
            Sounds.play(player, Sounds.HARVEST);
            plugin.getAnimations().floatingText(field.getSoil().toCenter().add(0, 1.6, 0),
                    "&a+" + entry.getValue() + " " + item.getName() + " &b+" + xp[0] + " XP");
            plugin.getLevels().addXp(player, xp[0]);
        }
    }

    /** Høster alle spillerens færdige marker i nærheden. Returnerer false hvis der ikke var noget at høste. */
    public boolean harvestNearby(Player player, Location center) {
        Map<String, Integer> collected = new LinkedHashMap<>();
        int[] xp = new int[1];
        int count = 0;
        boolean full = false;
        long now = System.currentTimeMillis();
        for (Field field : nearbyFields(player, center)) {
            if (!field.isReady(now)) {
                continue;
            }
            HarvestResult result = harvest(player, field, collected, xp);
            if (result == HarvestResult.FULL) {
                full = true;
                break;
            }
            if (result == HarvestResult.OK) {
                count++;
            }
        }
        if (count > 0) {
            msg().send(player, "field.harvested-many", "count", count, "items", describe(collected), "xp", xp[0]);
            Sounds.play(player, Sounds.HARVEST);
            plugin.getAnimations().floatingText(player.getLocation().add(0, 2.3, 0), "&b+" + xp[0] + " XP");
            plugin.getLevels().addXp(player, xp[0]);
        }
        if (full) {
            msg().send(player, "field.silo-full");
            Sounds.play(player, Sounds.ERROR);
        }
        return count > 0 || full;
    }

    private enum HarvestResult {
        OK,
        FULL,
        NOTHING
    }

    private HarvestResult harvest(Player player, Field field, Map<String, Integer> collected, int[] xp) {
        if (!field.isReady(System.currentTimeMillis())) {
            return HarvestResult.NOTHING;
        }
        FarmItem item = plugin.getItems().get(field.getCropId());
        if (item == null || !item.isCrop()) {
            field.clear();
            farm().refresh(field);
            return HarvestResult.NOTHING;
        }
        PlayerData data = plugin.getPlayers().get(player);
        int amount = item.getCrop().getHarvestAmount();
        if (!plugin.getStorage().hasSpace(data, item, amount)) {
            return HarvestResult.FULL;
        }
        data.addItem(item.getId(), amount);
        collected.merge(item.getId(), amount, Integer::sum);
        xp[0] += item.getCrop().getHarvestXp();
        field.clear();
        farm().markDirty();
        farm().refresh(field);
        Effects.harvest(field.getCropPos().toCenter().add(0, 0.5, 0));
        plugin.getAnimations().collect(player, field.getCropPos().toCenter().add(0, 0.5, 0), item.getDisplayStack(), amount);
        return HarvestResult.OK;
    }

    // ------------------------------------------------------------------
    // Bygninger
    // ------------------------------------------------------------------

    public void clickBuilding(Player player, Building building) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (type == null) {
            return;
        }
        // Alle må besøge en vejbod og købe varer
        if (type.isRoadside()) {
            new RoadsideMenu(plugin, player, building.getOwner(), building.getOwnerName()).open();
            return;
        }
        if (!canUse(player, building.getOwner(), building.getOwnerName())) {
            return;
        }
        collect(player, building, true);
        new BuildingMenu(plugin, player, building).open();
    }

    public void breakBuilding(Player player, Building building) {
        if (!canUse(player, building.getOwner(), building.getOwnerName())) {
            return;
        }
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (type == null) {
            return;
        }
        if (!player.isSneaking()) {
            msg().send(player, "building.remove-hint");
            return;
        }
        if (!building.getQueue().isEmpty()) {
            msg().send(player, "building.remove-not-empty");
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        farm().removeBuilding(building);
        if (PlaceableItems.give(player, PlaceableItems.building(type, plugin.getItems(), 1))) {
            msg().send(player, "general.inventory-full");
        }
        msg().send(player, "building.removed", "building", type.getName());
        Sounds.play(player, Sounds.COLLECT, 0.8f);
    }

    public boolean produce(Player player, Building building, Recipe recipe) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (type == null || !canUse(player, building.getOwner(), building.getOwnerName())) {
            return false;
        }
        PlayerData data = plugin.getPlayers().get(player);
        FarmItem output = plugin.getItems().get(recipe.getOutputId());
        int required = Math.max(type.getLevel(), recipe.getLevel());
        if (data.getLevel() < required) {
            msg().send(player, "building.recipe-locked", "product", output.getName(), "level", required);
            Sounds.play(player, Sounds.ERROR);
            return false;
        }
        if (building.isQueueFull()) {
            msg().send(player, "building.queue-full");
            Sounds.play(player, Sounds.ERROR);
            return false;
        }
        if (!data.hasItems(recipe.getIngredients())) {
            msg().send(player, "building.missing-ingredients", "missing", plugin.getOrders().missing(data, recipe.getIngredients()));
            Sounds.play(player, Sounds.ERROR);
            return false;
        }
        for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
            data.removeItem(entry.getKey(), entry.getValue());
        }
        long now = System.currentTimeMillis();
        QueueEntry entry = building.enqueue(recipe.getId(), now, recipe.getMillis());
        farm().markDirty();
        farm().refresh(building);
        msg().send(player, "building.started", "product", output.getName(), "time", Text.timeMillis(entry.getEndTime() - now));
        Sounds.play(player, Sounds.PRODUCE, 1.2f);
        return true;
    }

    /** Henter færdige varer. Returnerer antal hentede. */
    public int collect(Player player, Building building, boolean quiet) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (type == null) {
            return 0;
        }
        PlayerData data = plugin.getPlayers().get(player);
        long now = System.currentTimeMillis();
        Map<String, Integer> collected = new LinkedHashMap<>();
        int xp = 0;
        boolean full = false;
        Iterator<QueueEntry> iterator = building.getQueue().iterator();
        while (iterator.hasNext()) {
            QueueEntry entry = iterator.next();
            if (!entry.isDone(now)) {
                break;
            }
            Recipe recipe = type.getRecipes().get(entry.getRecipeId());
            FarmItem output = recipe == null ? null : plugin.getItems().get(recipe.getOutputId());
            if (output == null) {
                iterator.remove();
                continue;
            }
            if (!plugin.getStorage().hasSpace(data, output, recipe.getOutputAmount())) {
                full = true;
                break;
            }
            data.addItem(output.getId(), recipe.getOutputAmount());
            collected.merge(output.getId(), recipe.getOutputAmount(), Integer::sum);
            plugin.getAnimations().collect(player, building.getPos().toCenter().add(0, 1.2, 0), output.getDisplayStack(),
                    recipe.getOutputAmount());
            xp += recipe.getXp();
            iterator.remove();
        }
        if (!collected.isEmpty()) {
            farm().markDirty();
            farm().refresh(building);
            msg().send(player, "building.collected", "items", describe(collected), "xp", xp);
            Sounds.play(player, Sounds.COLLECT);
            plugin.getAnimations().floatingText(building.getPos().toCenter().add(0, 1.8, 0), "&b+" + xp + " XP");
            Effects.produce(building.getPos().toCenter().add(0, 1.2, 0));
            plugin.getLevels().addXp(player, xp);
        }
        if (full) {
            msg().send(player, "building.barn-full");
            Sounds.play(player, Sounds.ERROR);
        }
        int total = 0;
        for (int amount : collected.values()) {
            total += amount;
        }
        return total;
    }

    public double slotPrice(Building building, BuildingType type) {
        return type.getSlotPrice() * (building.getSlots() - type.getBaseSlots() + 1);
    }

    public void upgradeSlots(Player player, Building building) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (type == null || !canUse(player, building.getOwner(), building.getOwnerName())) {
            return;
        }
        if (building.getSlots() >= type.getMaxSlots()) {
            msg().send(player, "building.slot-max");
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (!plugin.getEconomy().charge(player, slotPrice(building, type))) {
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        building.setSlots(building.getSlots() + 1);
        farm().markDirty();
        farm().refresh(building);
        msg().send(player, "building.slot-upgraded", "building", type.getName(), "slots", building.getSlots());
        Sounds.play(player, Sounds.LEVEL_UP, 1.5f);
    }

    // ------------------------------------------------------------------
    // Butik
    // ------------------------------------------------------------------

    public int maxFields(Player player) {
        return settings().maxFields(plugin.getPlayers().get(player).getLevel());
    }

    public int ownedFields(Player player) {
        return farm().countFields(player.getUniqueId()) + PlaceableItems.count(player, PlaceableItems.FIELD_TAG);
    }

    public double fieldPrice(Player player) {
        return settings().fieldPrice(plugin.getPlayers().get(player).getFieldsBought());
    }

    public void buyField(Player player) {
        PlayerData data = plugin.getPlayers().get(player);
        int max = maxFields(player);
        if (ownedFields(player) >= max) {
            msg().send(player, "shop.field-limit", "max", max);
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        double price = fieldPrice(player);
        if (!plugin.getEconomy().charge(player, price)) {
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        data.setFieldsBought(data.getFieldsBought() + 1);
        if (PlaceableItems.give(player, PlaceableItems.field(1))) {
            msg().send(player, "general.inventory-full");
        }
        msg().send(player, "shop.bought", "item", "&a&lMark", "price", plugin.getEconomy().format(price));
        Sounds.play(player, Sounds.COINS);
    }

    public int ownedBuildings(Player player, BuildingType type) {
        return farm().countBuildings(player.getUniqueId(), type.getId())
                + PlaceableItems.count(player, PlaceableItems.BUILDING_PREFIX + type.getId());
    }

    public void buyBuilding(Player player, BuildingType type) {
        PlayerData data = plugin.getPlayers().get(player);
        if (data.getLevel() < type.getLevel()) {
            msg().send(player, "building.locked", "building", type.getName(), "level", type.getLevel());
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (ownedBuildings(player, type) >= type.getMaxPerPlayer()) {
            msg().send(player, "building.already-owned", "building", type.getName());
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (!plugin.getEconomy().charge(player, type.getPrice())) {
            Sounds.play(player, Sounds.ERROR);
            return;
        }
        if (PlaceableItems.give(player, PlaceableItems.building(type, plugin.getItems(), 1))) {
            msg().send(player, "general.inventory-full");
        }
        msg().send(player, "shop.bought", "item", type.getName(), "price", plugin.getEconomy().format(type.getPrice()));
        Sounds.play(player, Sounds.COINS);
    }

    // ------------------------------------------------------------------
    // Hjælpere
    // ------------------------------------------------------------------

    /** "4x Hvede, 2x Gulerod" */
    public String describe(Map<String, Integer> items) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            FarmItem item = plugin.getItems().get(entry.getKey());
            parts.add(entry.getValue() + "x " + (item == null ? entry.getKey() : item.getName()) + "&f");
        }
        return Text.color(String.join(", ", parts));
    }

    /** Antal klare marker og færdige varer for en spiller (bruges ved login og i PlaceholderAPI). */
    public int[] readyCounts(UUID owner) {
        long now = System.currentTimeMillis();
        int fields = 0;
        int items = 0;
        for (Field field : farm().getFields(owner)) {
            if (field.isReady(now)) {
                fields++;
            }
        }
        for (Building building : farm().getBuildings(owner)) {
            items += building.countDone(now);
        }
        return new int[]{fields, items};
    }
}
