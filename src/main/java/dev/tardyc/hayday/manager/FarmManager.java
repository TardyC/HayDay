package dev.tardyc.hayday.manager;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.config.Settings;
import dev.tardyc.hayday.hologram.FloatingIcon;
import dev.tardyc.hayday.hologram.Hologram;
import dev.tardyc.hayday.model.BlockPos;
import dev.tardyc.hayday.model.Building;
import dev.tardyc.hayday.model.BuildingType;
import dev.tardyc.hayday.model.CropType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.Listing;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.model.QueueEntry;
import dev.tardyc.hayday.model.Recipe;
import dev.tardyc.hayday.structure.Structure;
import dev.tardyc.hayday.util.Effects;
import dev.tardyc.hayday.util.Keys;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Alle marker og bygninger i verdenen: data, gemning, blok-visuals, dyr og hologrammer.
 * Spillerhandlinger (plant, høst, producér ...) ligger i {@link FarmService}.
 */
public final class FarmManager {

    private final HayDayPlugin plugin;
    private final File fieldsFile;
    private final File buildingsFile;
    private final BlockData air = Material.AIR.createBlockData();

    private final Map<UUID, Field> fields = new LinkedHashMap<>();
    private final Map<BlockPos, Field> fieldsBySoil = new HashMap<>();
    private final Map<BlockPos, Field> fieldsByCrop = new HashMap<>();
    private final Map<UUID, Building> buildings = new LinkedHashMap<>();
    private final Map<BlockPos, Building> buildingsByPos = new HashMap<>();
    private final Map<UUID, Building> animals = new HashMap<>();
    /** Alle blokke i bygningernes strukturer (huse, hegn, tage ...) -> bygningen. */
    private final Map<BlockPos, Building> structureBlocks = new HashMap<>();
    /** Bygninger med en type der ikke findes i buildings.yml længere - gemmes uændret så intet data går tabt. */
    private final Map<String, ConfigurationSection> unknownBuildings = new LinkedHashMap<>();

    private boolean dirty;
    private int tickCount;

    public FarmManager(HayDayPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Kunne ikke oprette mappen " + dataFolder.getPath());
        }
        this.fieldsFile = new File(dataFolder, "fields.yml");
        this.buildingsFile = new File(dataFolder, "buildings.yml");
    }

    // ------------------------------------------------------------------
    // Indlæsning og gemning
    // ------------------------------------------------------------------

    public void load() {
        long now = System.currentTimeMillis();
        if (fieldsFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(fieldsFile);
            ConfigurationSection root = config.getConfigurationSection("fields");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    ConfigurationSection section = root.getConfigurationSection(key);
                    BlockPos pos = section == null ? null : BlockPos.parse(section.getString("pos"));
                    if (pos == null) {
                        continue;
                    }
                    try {
                        Field field = new Field(UUID.fromString(key), UUID.fromString(section.getString("owner", "")),
                                section.getString("owner-name", "?"), pos);
                        String crop = section.getString("crop");
                        if (crop != null) {
                            FarmItem item = plugin.getItems().get(crop);
                            if (item != null && item.isCrop()) {
                                field.restore(item.getId(), section.getLong("planted"), section.getLong("ready"));
                            } else {
                                plugin.getLogger().warning("Ukendt afgrøde '" + crop + "' på marken ved " + pos + " - marken er ryddet.");
                            }
                        }
                        field.setNotified(field.isReady(now));
                        registerField(field);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Ugyldig mark i fields.yml: " + key);
                    }
                }
            }
        }
        if (buildingsFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(buildingsFile);
            ConfigurationSection root = config.getConfigurationSection("buildings");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    ConfigurationSection section = root.getConfigurationSection(key);
                    BlockPos pos = section == null ? null : BlockPos.parse(section.getString("pos"));
                    if (pos == null) {
                        continue;
                    }
                    BuildingType type = plugin.getBuildings().get(section.getString("type"));
                    if (type == null) {
                        plugin.getLogger().warning("Ukendt bygningstype '" + section.getString("type") + "' ved " + pos
                                + " - bygningen gemmes, men er inaktiv indtil typen findes igen.");
                        unknownBuildings.put(key, section);
                        continue;
                    }
                    try {
                        Building building = new Building(UUID.fromString(key), UUID.fromString(section.getString("owner", "")),
                                section.getString("owner-name", "?"), type.getId(), pos,
                                Math.max(type.getBaseSlots(), Math.min(type.getMaxSlots(), section.getInt("slots", type.getBaseSlots()))));
                        for (String raw : section.getStringList("queue")) {
                            QueueEntry entry = QueueEntry.parse(raw);
                            if (entry != null && type.getRecipes().containsKey(entry.getRecipeId())) {
                                building.getQueue().add(entry);
                            }
                        }
                        building.setNotifiedDone(building.countDone(now));
                        building.setStructureId(section.getString("structure", null));
                        building.setRotation(section.getInt("rotation", 0));
                        registerBuilding(building);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Ugyldig bygning i buildings.yml: " + key);
                    }
                }
            }
        }
        plugin.getLogger().info("Indlæste " + fields.size() + " marker og " + buildings.size() + " bygninger.");
    }

    public void markDirty() {
        dirty = true;
    }

    /** Gemmer hvis noget er ændret. Selve skrivningen til disk sker asynkront medmindre {@code sync} er true. */
    public void save(boolean sync) {
        if (!dirty && !sync) {
            return;
        }
        dirty = false;
        YamlConfiguration fieldConfig = new YamlConfiguration();
        for (Field field : fields.values()) {
            String path = "fields." + field.getId();
            fieldConfig.set(path + ".owner", field.getOwner().toString());
            fieldConfig.set(path + ".owner-name", field.getOwnerName());
            fieldConfig.set(path + ".pos", field.getSoil().serialize());
            if (field.getCropId() != null) {
                fieldConfig.set(path + ".crop", field.getCropId());
                fieldConfig.set(path + ".planted", field.getPlantedAt());
                fieldConfig.set(path + ".ready", field.getReadyAt());
            }
        }
        YamlConfiguration buildingConfig = new YamlConfiguration();
        for (Building building : buildings.values()) {
            String path = "buildings." + building.getId();
            buildingConfig.set(path + ".owner", building.getOwner().toString());
            buildingConfig.set(path + ".owner-name", building.getOwnerName());
            buildingConfig.set(path + ".type", building.getTypeId());
            buildingConfig.set(path + ".pos", building.getPos().serialize());
            buildingConfig.set(path + ".slots", building.getSlots());
            buildingConfig.set(path + ".structure", building.getStructureId());
            buildingConfig.set(path + ".rotation", building.getStructureId() == null ? null : building.getRotation());
            List<String> queue = new ArrayList<>();
            for (QueueEntry entry : building.getQueue()) {
                queue.add(entry.serialize());
            }
            buildingConfig.set(path + ".queue", queue);
        }
        for (Map.Entry<String, ConfigurationSection> entry : unknownBuildings.entrySet()) {
            buildingConfig.set("buildings." + entry.getKey(), entry.getValue());
        }
        String fieldData = fieldConfig.saveToString();
        String buildingData = buildingConfig.saveToString();
        if (sync) {
            write(fieldsFile, fieldData);
            write(buildingsFile, buildingData);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                synchronized (this) {
                    write(fieldsFile, fieldData);
                    write(buildingsFile, buildingData);
                }
            });
        }
    }

    private void write(File file, String data) {
        try {
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.write(temp.toPath(), data.getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Kunne ikke gemme " + file.getName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Registrering
    // ------------------------------------------------------------------

    private void registerField(Field field) {
        fields.put(field.getId(), field);
        fieldsBySoil.put(field.getSoil(), field);
        fieldsByCrop.put(field.getCropPos(), field);
        World world = field.getSoil().getWorld();
        if (world != null) {
            createFieldHologram(field);
        }
    }

    private void registerBuilding(Building building) {
        buildings.put(building.getId(), building);
        buildingsByPos.put(building.getPos(), building);
        registerStructure(building);
        World world = building.getPos().getWorld();
        if (world != null) {
            createBuildingHologram(building);
        }
    }

    /** Registrerer alle blokke i bygningens struktur, så klik, beskyttelse og fjernelse virker på hele huset. */
    private void registerStructure(Building building) {
        Structure structure = plugin.getStructures().get(building.getStructureId());
        building.getStructureBlocks().clear();
        building.getGroundBlocks().clear();
        if (structure == null) {
            return;
        }
        BlockPos core = building.getPos();
        for (Structure.Cell cell : structure.cells(building.getRotation())) {
            if (cell.isAnchor() || cell.ch == Structure.AIR) {
                continue;
            }
            BlockPos pos = core.offset(cell.dx, cell.dy, cell.dz);
            building.getStructureBlocks().add(pos);
            if (cell.isGround()) {
                building.getGroundBlocks().add(pos);
            }
            structureBlocks.put(pos, building);
        }
    }

    /** Efter /hayday reload: blueprints kan være ændret. */
    public void reloadStructures() {
        structureBlocks.clear();
        for (Building building : buildings.values()) {
            registerStructure(building);
        }
    }

    /** Hvor højt over bygningens blok hologrammet svæver (over taget hvis bygningen er et hus). */
    public double hologramHeight(Building building, BuildingType type) {
        Structure structure = plugin.getStructures().get(building.getStructureId());
        return structure != null ? structure.getHologramHeight() : type.getHologramHeight();
    }

    private void createFieldHologram(Field field) {
        if (field.getHologram() != null) {
            return;
        }
        BlockPos soil = field.getSoil();
        Location location = soil.toCenter().add(0, plugin.getSettings().fieldHologramHeight, 0);
        field.setHologram(plugin.getHolograms().create(location, fieldLines(field, System.currentTimeMillis()), null));
    }

    private void createBuildingHologram(Building building) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        if (building.getHologram() != null || type == null) {
            return;
        }
        Location location = building.getPos().toCenter().add(0, hologramHeight(building, type), 0);
        building.setHologram(plugin.getHolograms().create(location, buildingLines(building, type, System.currentTimeMillis()), null));
    }

    /** Opretter hologrammer for marker/bygninger i en verden der lige er blevet indlæst. */
    public void onWorldLoad(World world) {
        for (Field field : fields.values()) {
            if (field.getSoil().getWorldName().equals(world.getName())) {
                createFieldHologram(field);
            }
        }
        for (Building building : buildings.values()) {
            if (building.getPos().getWorldName().equals(world.getName())) {
                createBuildingHologram(building);
            }
        }
    }

    public void onWorldUnload(World world) {
        for (Field field : fields.values()) {
            if (field.getSoil().getWorldName().equals(world.getName())) {
                hideIcon(field.getIcon());
                if (field.getHologram() != null) {
                    plugin.getHolograms().remove(field.getHologram());
                    field.setHologram(null);
                }
            }
        }
        for (Building building : buildings.values()) {
            if (building.getPos().getWorldName().equals(world.getName())) {
                removeAnimal(building);
                hideIcon(building.getIcon());
                if (building.getHologram() != null) {
                    plugin.getHolograms().remove(building.getHologram());
                    building.setHologram(null);
                }
            }
        }
    }

    public Field createField(Player owner, BlockPos soil) {
        return createField(owner.getUniqueId(), owner.getName(), soil);
    }

    public Field createField(UUID owner, String ownerName, BlockPos soil) {
        Field field = new Field(UUID.randomUUID(), owner, ownerName, soil);
        registerField(field);
        dirty = true;
        return field;
    }

    public void removeField(Field field) {
        hideIcon(field.getIcon());
        field.setIcon(null);
        fields.remove(field.getId());
        fieldsBySoil.remove(field.getSoil());
        fieldsByCrop.remove(field.getCropPos());
        if (field.getHologram() != null) {
            plugin.getHolograms().remove(field.getHologram());
            field.setHologram(null);
        }
        Block soil = field.getSoil().getBlock();
        if (soil != null) {
            Block crop = soil.getRelative(BlockFace.UP);
            if (fieldsBySoil.get(BlockPos.of(crop)) == null) {
                crop.setType(Material.AIR, false);
            }
            soil.setType(Material.DIRT, false);
        }
        dirty = true;
    }

    public Building createBuilding(Player owner, BuildingType type, BlockPos pos) {
        return createBuilding(owner, type, pos, null, 0);
    }

    public Building createBuilding(Player owner, BuildingType type, BlockPos pos, String structureId, int rotation) {
        Building building = new Building(UUID.randomUUID(), owner.getUniqueId(), owner.getName(), type.getId(), pos, type.getBaseSlots());
        building.setStructureId(structureId);
        building.setRotation(rotation);
        registerBuilding(building);
        dirty = true;
        return building;
    }

    public void removeBuilding(Building building) {
        hideIcon(building.getIcon());
        building.setIcon(null);
        buildings.remove(building.getId());
        buildingsByPos.remove(building.getPos());
        removeAnimal(building);
        if (building.getHologram() != null) {
            plugin.getHolograms().remove(building.getHologram());
            building.setHologram(null);
        }
        Block block = building.getPos().getBlock();
        if (block != null) {
            block.setType(Material.AIR, false);
        }
        // Riv huset ned: jorden bliver til græs, resten til luft
        for (BlockPos pos : building.getStructureBlocks()) {
            structureBlocks.remove(pos);
            Block part = pos.getBlock();
            if (part != null) {
                part.setType(building.getGroundBlocks().contains(pos) ? Material.GRASS_BLOCK : Material.AIR, false);
            }
        }
        building.getStructureBlocks().clear();
        building.getGroundBlocks().clear();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Opslag
    // ------------------------------------------------------------------

    /** Marken hvis blokken er enten jorden eller afgrøden. */
    public Field getFieldAt(Block block) {
        if (fields.isEmpty()) {
            return null;
        }
        BlockPos pos = BlockPos.of(block);
        Field field = fieldsBySoil.get(pos);
        return field != null ? field : fieldsByCrop.get(pos);
    }

    public Field getFieldBySoil(Block block) {
        return fields.isEmpty() ? null : fieldsBySoil.get(BlockPos.of(block));
    }

    public Field getFieldByCrop(Block block) {
        return fields.isEmpty() ? null : fieldsByCrop.get(BlockPos.of(block));
    }

    public Building getBuildingAt(Block block) {
        if (buildings.isEmpty()) {
            return null;
        }
        BlockPos pos = BlockPos.of(block);
        Building building = buildingsByPos.get(pos);
        return building != null ? building : structureBlocks.get(pos);
    }

    /** Er positionen optaget af en mark eller en bygning (inkl. bygningens hus)? */
    public boolean isOccupied(BlockPos pos) {
        return fieldsBySoil.containsKey(pos) || fieldsByCrop.containsKey(pos) || buildingsByPos.containsKey(pos)
                || structureBlocks.containsKey(pos);
    }

    public boolean isFarmBlock(Block block) {
        if (fields.isEmpty() && buildings.isEmpty()) {
            return false;
        }
        return isOccupied(BlockPos.of(block));
    }

    public boolean hasFields() {
        return !fields.isEmpty();
    }

    public Collection<Field> getFields() {
        return Collections.unmodifiableCollection(fields.values());
    }

    public Collection<Building> getBuildings() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public List<Field> getFields(UUID owner) {
        List<Field> list = new ArrayList<>();
        for (Field field : fields.values()) {
            if (field.getOwner().equals(owner)) {
                list.add(field);
            }
        }
        return list;
    }

    public List<Building> getBuildings(UUID owner) {
        List<Building> list = new ArrayList<>();
        for (Building building : buildings.values()) {
            if (building.getOwner().equals(owner)) {
                list.add(building);
            }
        }
        return list;
    }

    public int countFields(UUID owner) {
        int count = 0;
        for (Field field : fields.values()) {
            if (field.getOwner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public int countBuildings(UUID owner, String typeId) {
        int count = 0;
        for (Building building : buildings.values()) {
            if (building.getOwner().equals(owner) && building.getTypeId().equals(typeId)) {
                count++;
            }
        }
        return count;
    }

    public Building getBuildingByAnimal(Entity entity) {
        return animals.isEmpty() ? null : animals.get(entity.getUniqueId());
    }

    /** Opdaterer ejernavnet på marker og bygninger når en spiller logger ind med nyt navn. */
    public void updateOwnerName(Player player) {
        for (Field field : fields.values()) {
            if (field.getOwner().equals(player.getUniqueId()) && !player.getName().equals(field.getOwnerName())) {
                field.setOwnerName(player.getName());
                dirty = true;
            }
        }
        for (Building building : buildings.values()) {
            if (building.getOwner().equals(player.getUniqueId()) && !player.getName().equals(building.getOwnerName())) {
                building.setOwnerName(player.getName());
                dirty = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // Opdatering (kører hvert update-interval)
    // ------------------------------------------------------------------

    public void tick() {
        long now = System.currentTimeMillis();
        Settings settings = plugin.getSettings();
        Map<UUID, Integer> readyFields = new HashMap<>();
        Map<UUID, List<String[]>> readyBuildings = new HashMap<>();
        boolean sparkle = settings.animSparkles && (++tickCount % 2 == 0);

        for (Field field : fields.values()) {
            if (field.getCropId() != null && field.isReady(now) && !field.isNotified()) {
                field.setNotified(true);
                readyFields.merge(field.getOwner(), 1, Integer::sum);
            }
            if (field.getSoil().isLoaded()) {
                updateFieldBlocks(field, now);
                List<String> lines = fieldLines(field, now);
                if (field.getHologram() != null) {
                    field.getHologram().setLines(lines);
                }
                updateFieldIcon(field, now, lines.size());
                if (sparkle && field.isReady(now)) {
                    Effects.readySparkle(field.getCropPos().toCenter().add(0, 0.7, 0));
                }
            } else if (field.getIcon() != null) {
                hideIcon(field.getIcon());
            }
        }

        for (Building building : buildings.values()) {
            BuildingType type = plugin.getBuildings().get(building.getTypeId());
            if (type == null) {
                continue;
            }
            int done = building.countDone(now);
            if (done > building.getNotifiedDone()) {
                readyBuildings.computeIfAbsent(building.getOwner(), k -> new ArrayList<>())
                        .add(new String[]{type.getName(), String.valueOf(done)});
            }
            building.setNotifiedDone(done);
            if (building.getPos().isLoaded()) {
                updateBuildingBlock(building, type);
                updateAnimal(building, type);
                List<String> lines = buildingLines(building, type, now);
                if (building.getHologram() != null) {
                    building.getHologram().setLines(lines);
                }
                updateBuildingIcon(building, type, now, lines.size());
            } else {
                if (building.getAnimal() != null) {
                    removeAnimal(building);
                }
                hideIcon(building.getIcon());
            }
        }

        if (settings.notifyReady) {
            for (Map.Entry<UUID, Integer> entry : readyFields.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    plugin.getMessages().send(player, "field.ready-notify", "count", entry.getValue());
                }
            }
            for (Map.Entry<UUID, List<String[]>> entry : readyBuildings.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    for (String[] note : entry.getValue()) {
                        plugin.getMessages().send(player, "building.done-notify", "building", note[0], "count", note[1]);
                    }
                }
            }
        }
    }

    /** Sørger for at jorden er våd farmland og at afgrøden viser det rigtige vækststadie. */
    public void updateFieldBlocks(Field field, long now) {
        Block soil = field.getSoil().getBlock();
        if (soil == null) {
            return;
        }
        if (soil.getType() != Material.FARMLAND) {
            soil.setType(Material.FARMLAND, false);
        }
        BlockData soilData = soil.getBlockData();
        if (soilData instanceof Farmland) {
            Farmland farmland = (Farmland) soilData;
            if (farmland.getMoisture() != farmland.getMaximumMoisture()) {
                farmland.setMoisture(farmland.getMaximumMoisture());
                soil.setBlockData(farmland, false);
            }
        }
        Block cropBlock = soil.getRelative(BlockFace.UP);
        BlockData target = air;
        if (field.getCropId() != null) {
            FarmItem item = plugin.getItems().get(field.getCropId());
            if (item != null && item.isCrop()) {
                target = item.getCrop().stageFor(field.getProgress(now), field.isReady(now));
            }
        }
        if (!cropBlock.getBlockData().equals(target)) {
            cropBlock.setBlockData(target, false);
        }
    }

    private void updateBuildingBlock(Building building, BuildingType type) {
        Block block = building.getPos().getBlock();
        if (block != null && block.getType() != type.getBlock()) {
            block.setType(type.getBlock(), false);
        }
    }

    // ------------------------------------------------------------------
    // Dyr
    // ------------------------------------------------------------------

    private void updateAnimal(Building building, BuildingType type) {
        if (type.getAnimal() == null) {
            if (building.getAnimal() != null) {
                removeAnimal(building);
            }
            return;
        }
        // Havnens båd ligger kun ved kajen når skibet er i havn
        if (type.isHarbor()) {
            PlayerData data = plugin.getPlayers().getIfLoaded(building.getOwner());
            if (data == null || plugin.getShip().state(data) != ShipManager.State.DOCKED) {
                removeAnimal(building);
                return;
            }
        }
        Entity animal = building.getAnimal();
        if (animal != null && animal.isValid() && animal.getType() == type.getAnimal()) {
            // Er den blevet skubbet væk (fx en båd), så sæt den tilbage
            Location home = building.getPos().toCenter().add(0, 1, 0);
            if (animal.getLocation().distanceSquared(home) > 0.25) {
                home.setYaw(animal.getLocation().getYaw());
                animal.teleport(home);
            }
            return;
        }
        removeAnimal(building);
        spawnAnimal(building, type);
    }

    private void spawnAnimal(Building building, BuildingType type) {
        World world = building.getPos().getWorld();
        Class<? extends Entity> entityClass = type.getAnimal().getEntityClass();
        if (world == null || entityClass == null) {
            return;
        }
        Location location = building.getPos().toCenter().add(0, 1, 0);
        location.setYaw((building.getId().hashCode() & 0xFF) * 360f / 256f);
        Consumer<Entity> setup = entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setGravity(false);
            entity.getPersistentDataContainer().set(Keys.ANIMAL, PersistentDataType.STRING, building.getId().toString());
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                living.setAI(false);
                living.setCollidable(false);
                living.setRemoveWhenFarAway(false);
            }
            if (entity instanceof Ageable) {
                ((Ageable) entity).setAdult();
            }
        };
        Entity entity = plugin.getHolograms().spawnSafely(() -> world.spawn(location, entityClass, setup));
        if (entity != null && entity.isValid()) {
            building.setAnimal(entity);
            animals.put(entity.getUniqueId(), building);
        }
    }

    private void removeAnimal(Building building) {
        Entity animal = building.getAnimal();
        if (animal != null) {
            animals.remove(animal.getUniqueId());
            animal.remove();
            building.setAnimal(null);
        }
    }

    public void removeAllAnimals() {
        for (Building building : buildings.values()) {
            removeAnimal(building);
        }
        animals.clear();
    }

    /** Fjerner HayDay-dyr der ikke hører til en bygning (fx efter et crash). */
    public void removeStray(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(Keys.ANIMAL, PersistentDataType.STRING)
                    && !animals.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Svævende ikoner
    // ------------------------------------------------------------------

    /** Højden over hologrammets bund hvor ikonet skal svæve (lige over teksten). */
    private double iconOffset(int lineCount) {
        float scale = plugin.getSettings().holoScale > 0 ? plugin.getSettings().holoScale : 1f;
        return lineCount * 0.27 * scale + 0.45;
    }

    private void updateFieldIcon(Field field, long now, int lineCount) {
        FarmItem item = field.getCropId() == null ? null : plugin.getItems().get(field.getCropId());
        if (!plugin.getSettings().animFieldIcons || item == null || !field.isReady(now)) {
            hideIcon(field.getIcon());
            return;
        }
        if (field.getIcon() == null) {
            field.setIcon(new FloatingIcon(plugin));
        }
        Location location = field.getSoil().toCenter()
                .add(0, plugin.getSettings().fieldHologramHeight + iconOffset(lineCount), 0);
        field.getIcon().show(location, item.getDisplayStack(), 0.55f, false);
    }

    private void updateBuildingIcon(Building building, BuildingType type, long now, int lineCount) {
        FarmItem output = null;
        int done = building.countDone(now);
        if (type.isProduction() && !building.getQueue().isEmpty()) {
            QueueEntry entry = done > 0 ? building.getQueue().get(0) : building.getActive(now);
            Recipe recipe = entry == null ? null : type.getRecipes().get(entry.getRecipeId());
            output = recipe == null ? null : plugin.getItems().get(recipe.getOutputId());
        }
        if (!plugin.getSettings().animBuildingIcons || output == null) {
            hideIcon(building.getIcon());
            return;
        }
        if (building.getIcon() == null) {
            building.setIcon(new FloatingIcon(plugin));
        }
        Location location = building.getPos().toCenter().add(0, hologramHeight(building, type) + iconOffset(lineCount), 0);
        building.getIcon().show(location, output.getDisplayStack(), 0.6f, done > 0);
    }

    private static void hideIcon(FloatingIcon icon) {
        if (icon != null) {
            icon.hide();
        }
    }

    public void hideAllIcons() {
        for (Field field : fields.values()) {
            hideIcon(field.getIcon());
        }
        for (Building building : buildings.values()) {
            hideIcon(building.getIcon());
        }
    }

    // ------------------------------------------------------------------
    // Hologram-tekster
    // ------------------------------------------------------------------

    public List<String> fieldLines(Field field, long now) {
        Settings settings = plugin.getSettings();
        if (field.getCropId() == null) {
            return replaceAll(settings.fieldEmpty, "owner", field.getOwnerName());
        }
        FarmItem item = plugin.getItems().get(field.getCropId());
        String crop = item == null ? field.getCropId() : item.getName();
        if (field.isReady(now)) {
            return replaceAll(settings.fieldReady, "crop", crop, "owner", field.getOwnerName());
        }
        double progress = field.getProgress(now);
        return replaceAll(settings.fieldGrowing, "crop", crop, "owner", field.getOwnerName(),
                "time", Text.timeMillis(field.getReadyAt() - now),
                "bar", settings.bar(progress),
                "percent", (int) Math.floor(progress * 100));
    }

    public List<String> buildingLines(Building building, BuildingType type, long now) {
        Settings settings = plugin.getSettings();
        if (type.isHarbor()) {
            PlayerData data = plugin.getPlayers().getIfLoaded(building.getOwner());
            if (data == null) {
                return replaceAll(settings.harborOffline, "building", type.getName(), "owner", building.getOwnerName());
            }
            switch (plugin.getShip().state(data)) {
                case LOCKED:
                    return replaceAll(settings.harborLocked, "building", type.getName(), "owner", building.getOwnerName(),
                            "level", settings.shipLevel);
                case AWAY:
                    return replaceAll(settings.harborAway, "building", type.getName(), "owner", building.getOwnerName(),
                            "time", Text.timeMillis(plugin.getShip().timeLeft(data)));
                default:
                    return replaceAll(settings.harborDocked, "building", type.getName(), "owner", building.getOwnerName(),
                            "filled", plugin.getShip().filled(data), "total", data.getShipCrates().size(),
                            "time", Text.timeMillis(plugin.getShip().timeLeft(data)));
            }
        }
        if (type.isRoadside()) {
            return replaceAll(settings.roadsideLines, "building", type.getName(), "owner", building.getOwnerName(),
                    "active", plugin.getMarket().count(building.getOwner(), Listing.State.ACTIVE),
                    "sold", plugin.getMarket().count(building.getOwner(), Listing.State.SOLD));
        }
        int done = building.countDone(now);
        if (done > 0) {
            return replaceAll(settings.buildingDone, "building", type.getName(), "owner", building.getOwnerName(),
                    "done", done, "queue", building.getQueue().size(), "slots", building.getSlots());
        }
        QueueEntry active = building.getActive(now);
        if (active != null) {
            Recipe recipe = type.getRecipes().get(active.getRecipeId());
            FarmItem output = recipe == null ? null : plugin.getItems().get(recipe.getOutputId());
            return replaceAll(settings.buildingWorking, "building", type.getName(), "owner", building.getOwnerName(),
                    "product", output == null ? active.getRecipeId() : output.getName(),
                    "time", Text.timeMillis(active.getEndTime() - now),
                    "queue", building.getQueue().size(), "slots", building.getSlots());
        }
        return replaceAll(settings.buildingIdle, "building", type.getName(), "owner", building.getOwnerName(),
                "queue", 0, "slots", building.getSlots());
    }

    private static List<String> replaceAll(List<String> lines, Object... placeholders) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.replace(line, placeholders));
        }
        return out;
    }

    /** Opdaterer hologrammet med det samme (fx lige efter en spiller har plantet). */
    public void refresh(Field field) {
        long now = System.currentTimeMillis();
        if (field.getSoil().isLoaded()) {
            updateFieldBlocks(field, now);
        }
        Hologram hologram = field.getHologram();
        if (hologram != null) {
            hologram.setLines(fieldLines(field, now));
        }
    }

    public void refresh(Building building) {
        BuildingType type = plugin.getBuildings().get(building.getTypeId());
        Hologram hologram = building.getHologram();
        if (type != null && hologram != null) {
            hologram.setLines(buildingLines(building, type, System.currentTimeMillis()));
        }
    }

    /** Når config er genindlæst: flyt hologrammer til nye højder. */
    public void reloadHolograms() {
        for (Field field : fields.values()) {
            if (field.getHologram() != null && field.getSoil().getWorld() != null) {
                plugin.getHolograms().move(field.getHologram(),
                        field.getSoil().toCenter().add(0, plugin.getSettings().fieldHologramHeight, 0));
            }
        }
        for (Building building : buildings.values()) {
            BuildingType type = plugin.getBuildings().get(building.getTypeId());
            if (building.getHologram() != null && type != null && building.getPos().getWorld() != null) {
                plugin.getHolograms().move(building.getHologram(),
                        building.getPos().toCenter().add(0, hologramHeight(building, type), 0));
            }
        }
    }

    public int fieldCount() {
        return fields.size();
    }

    public int buildingCount() {
        return buildings.size();
    }
}
