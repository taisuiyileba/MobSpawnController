package com.mobspawncontroller.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.active.ActiveSpawnSettings;
import com.mobspawncontroller.active.ActiveSpawnSettingsJsonCodec;
import com.mobspawncontroller.attribute.PendingAttributesHolder;
import com.mobspawncontroller.attribute.PendingAttributesHolder.State;
import com.mobspawncontroller.compat.SereneSeasonsCompat;
import com.mobspawncontroller.loadout.LoadoutParsing;
import com.mobspawncontroller.loadout.MobLoadout;
import com.mobspawncontroller.loadout.MobLoadoutJsonCodec;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import com.mobspawncontroller.natural.NaturalSpawnSettingsJsonCodec;
import com.mobspawncontroller.natural.NaturalSpawnBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MobSpawnManager {

    // Loadout NBT strings are full of single quotes, which HTML escaping would make unreadable in rules.json.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String VANILLA_SPAWN_KEY = "vanilla_spawn";
    private static final String LEGACY_SPAWN_RESTRICTIONS_KEY = "spawn_restrictions";
    private static final String EXTRA_SPAWN_KEY = "extra_spawn";
    private static final String LOADOUT_KEY = "loadout";
    private static final int SPAWN_TYPE_COUNT = MobSpawnType.values().length;
    private static final Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> RULES = new HashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, Double>> ATTRIBUTE_OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, NaturalSpawnSettings> NATURAL_SPAWN_SETTINGS = new HashMap<>();
    private static final Map<ResourceLocation, ActiveSpawnSettings> ACTIVE_SPAWN_SETTINGS = new HashMap<>();
    // Read from world-generation threads when structure mobs spawn, so it must tolerate concurrent access.
    private static final Map<ResourceLocation, MobLoadout> LOADOUTS = new ConcurrentHashMap<>();
    private static Path savePath;

    private MobSpawnManager() {
    }

    public static void setSavePath(Path path) {
        savePath = path;
    }

    public static void setAllowed(ResourceLocation mobId, MobSpawnType type, boolean allowed) {
        RULES.computeIfAbsent(mobId, key -> new EnumMap<>(MobSpawnType.class)).put(type, allowed);
    }

    public static void setAllAllowed(ResourceLocation mobId, boolean allowed) {
        EnumMap<MobSpawnType, Boolean> map = RULES.computeIfAbsent(mobId, key -> new EnumMap<>(MobSpawnType.class));
        for (MobSpawnType type : MobSpawnType.values()) {
            map.put(type, allowed);
        }
    }

    public static Boolean getAllowed(ResourceLocation mobId, MobSpawnType type) {
        EnumMap<MobSpawnType, Boolean> map = RULES.get(mobId);
        return map == null ? null : map.get(type);
    }

    public static Map<MobSpawnType, Boolean> getRules(ResourceLocation mobId) {
        return RULES.get(mobId);
    }

    public static Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> getAllRules() {
        return Collections.unmodifiableMap(RULES);
    }

    public static Double getAttributeOverride(ResourceLocation mobId, ResourceLocation attributeId) {
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        return attributes == null ? null : attributes.get(attributeId);
    }

    public static Map<ResourceLocation, Double> getAttributeOverrides(ResourceLocation mobId) {
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        return attributes == null ? Map.of() : Collections.unmodifiableMap(attributes);
    }

    public static Set<ResourceLocation> getAttributeOverrideMobs() {
        return Collections.unmodifiableSet(new HashSet<>(ATTRIBUTE_OVERRIDES.keySet()));
    }

    public static NaturalSpawnSettings getNaturalSpawnSettings(ResourceLocation mobId) {
        return NATURAL_SPAWN_SETTINGS.getOrDefault(mobId, NaturalSpawnSettings.defaults());
    }

    public static Map<ResourceLocation, NaturalSpawnSettings> getAllNaturalSpawnSettings() {
        return Collections.unmodifiableMap(NATURAL_SPAWN_SETTINGS);
    }

    public static void setNaturalSpawnSettings(ResourceLocation mobId, NaturalSpawnSettings settings) {
        if (settings == null || settings.isDefault()) {
            NATURAL_SPAWN_SETTINGS.remove(mobId);
        } else {
            NATURAL_SPAWN_SETTINGS.put(mobId, settings);
        }
        NaturalSpawnBoost.invalidate();
    }

    public static ActiveSpawnSettings getActiveSpawnSettings(ResourceLocation mobId) {
        return ACTIVE_SPAWN_SETTINGS.getOrDefault(mobId, ActiveSpawnSettings.defaults());
    }

    public static Map<ResourceLocation, ActiveSpawnSettings> getAllActiveSpawnSettings() {
        return Collections.unmodifiableMap(ACTIVE_SPAWN_SETTINGS);
    }

    public static void setActiveSpawnSettings(ResourceLocation mobId, ActiveSpawnSettings settings) {
        if (settings == null || settings.isDefault()) {
            ACTIVE_SPAWN_SETTINGS.remove(mobId);
        } else {
            ACTIVE_SPAWN_SETTINGS.put(mobId, settings);
        }
    }

    public static MobLoadout getLoadout(ResourceLocation mobId) {
        return LOADOUTS.getOrDefault(mobId, MobLoadout.defaults());
    }

    public static MobLoadout getLoadoutOrNull(ResourceLocation mobId) {
        return LOADOUTS.get(mobId);
    }

    public static Set<ResourceLocation> getLoadoutMobs() {
        return Set.copyOf(LOADOUTS.keySet());
    }

    public static void setLoadout(ResourceLocation mobId, MobLoadout loadout) {
        if (loadout == null || loadout.isDefault()) {
            LOADOUTS.remove(mobId);
        } else {
            LOADOUTS.put(mobId, loadout);
        }
    }

    /** Queues one attribute write for a newly accepted mob. */
    public static void queueAttributeOverrides(Mob mob, boolean preservePartialHealth) {
        PendingAttributesHolder holder = (PendingAttributesHolder) mob;
        if (holder.mobspawncontroller$getAttributeState() == State.APPLIED) {
            return;
        }
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (ATTRIBUTE_OVERRIDES.containsKey(mobId)) {
            holder.mobspawncontroller$setAttributeState(preservePartialHealth ? State.PENDING_PRESERVE_HEALTH : State.PENDING);
        }
    }

    /** Runs before joining/serialization, or on the first tick if initialization happened after joining. */
    public static void applyPendingAttributeOverrides(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        PendingAttributesHolder holder = (PendingAttributesHolder) mob;
        State state = holder.mobspawncontroller$getAttributeState();
        if (!state.isPending()) {
            return;
        }
        holder.mobspawncontroller$setAttributeState(State.APPLIED);
        applyAttributeOverrides(mob, state == State.PENDING_PRESERVE_HEALTH);
    }

    public static void setAttributeOverrides(ResourceLocation mobId, Map<ResourceLocation, Double> attributes) {
        if (attributes.isEmpty()) {
            ATTRIBUTE_OVERRIDES.remove(mobId);
            return;
        }
        ATTRIBUTE_OVERRIDES.put(mobId, new HashMap<>(attributes));
    }

    public static void applyAttributeOverrides(LivingEntity entity) {
        applyAttributeOverrides(entity, false);
    }

    private static void applyAttributeOverrides(LivingEntity entity, boolean preservePartialHealth) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        ResourceLocation maxHealthId = BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH);
        boolean updateHealth = attributes.containsKey(maxHealthId) && entity.isAlive()
                && entity.getAttributes().hasAttribute(Attributes.MAX_HEALTH);
        float healthBefore = updateHealth ? entity.getHealth() : 0;
        boolean keepHealth = updateHealth && preservePartialHealth && healthBefore < entity.getMaxHealth();

        attributes.forEach((attributeId, value) -> {
            Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId);
            if (attribute != null) {
                applyAttributeOverride(entity, attribute, value);
            }
        });

        if (updateHealth) {
            float maxHealth = entity.getMaxHealth();
            entity.setHealth(keepHealth ? Math.min(healthBefore, maxHealth) : maxHealth);
        }
    }

    private static void applyAttributeOverride(LivingEntity entity, Attribute attribute, double value) {
        if (!entity.getAttributes().hasAttribute(attribute)) {
            return;
        }
        AttributeInstance instance = entity.getAttributes().getInstance(attribute);
        if (instance != null) {
            instance.setBaseValue(attribute.sanitizeValue(value));
        }
    }

    public static void clear(ResourceLocation mobId) {
        RULES.remove(mobId);
        ATTRIBUTE_OVERRIDES.remove(mobId);
        NATURAL_SPAWN_SETTINGS.remove(mobId);
        ACTIVE_SPAWN_SETTINGS.remove(mobId);
        LOADOUTS.remove(mobId);
        NaturalSpawnBoost.invalidate();
    }

    public static void clearAll() {
        RULES.clear();
        ATTRIBUTE_OVERRIDES.clear();
        NATURAL_SPAWN_SETTINGS.clear();
        ACTIVE_SPAWN_SETTINGS.clear();
        LOADOUTS.clear();
        NaturalSpawnBoost.invalidate();
    }

    public static void save() {
        if (savePath == null) {
            return;
        }

        JsonObject root = new JsonObject();
        RULES.forEach((mobId, map) -> {
            JsonObject mobObj = new JsonObject();
            map.forEach((type, allowed) -> mobObj.addProperty(type.name(), allowed));
            addAttributeOverrides(mobId, mobObj);
            root.add(mobId.toString(), mobObj);
        });
        ATTRIBUTE_OVERRIDES.forEach((mobId, map) -> {
            if (!root.has(mobId.toString())) {
                JsonObject mobObj = new JsonObject();
                addAttributeOverrides(mobId, mobObj);
                root.add(mobId.toString(), mobObj);
            }
        });
        NATURAL_SPAWN_SETTINGS.forEach((mobId, settings) -> {
            JsonObject mobObj;
            if (root.has(mobId.toString())) {
                mobObj = root.getAsJsonObject(mobId.toString());
            } else {
                mobObj = new JsonObject();
                root.add(mobId.toString(), mobObj);
            }
            mobObj.add(VANILLA_SPAWN_KEY, NaturalSpawnSettingsJsonCodec.encode(settings));
        });
        ACTIVE_SPAWN_SETTINGS.forEach((mobId, settings) -> {
            JsonObject mobObj;
            if (root.has(mobId.toString())) {
                mobObj = root.getAsJsonObject(mobId.toString());
            } else {
                mobObj = new JsonObject();
                root.add(mobId.toString(), mobObj);
            }
            mobObj.add(EXTRA_SPAWN_KEY, ActiveSpawnSettingsJsonCodec.encode(settings));
        });
        LOADOUTS.forEach((mobId, loadout) -> {
            JsonObject mobObj;
            if (root.has(mobId.toString())) {
                mobObj = root.getAsJsonObject(mobId.toString());
            } else {
                mobObj = new JsonObject();
                root.add(mobId.toString(), mobObj);
            }
            mobObj.add(LOADOUT_KEY, MobLoadoutJsonCodec.encode(loadout));
        });

        try {
            Files.createDirectories(savePath.getParent());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(savePath), StandardCharsets.UTF_8))) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            MobSpawnController.LOGGER.error("Failed to save mob spawn rules", e);
        }
    }

    private static void addAttributeOverrides(ResourceLocation mobId, JsonObject mobObj) {
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        JsonObject attributeObj = new JsonObject();
        attributes.forEach((attributeId, value) -> attributeObj.addProperty(attributeId.toString(), value));
        mobObj.add(ATTRIBUTES_KEY, attributeObj);
    }

    public static void load() {
        RULES.clear();
        ATTRIBUTE_OVERRIDES.clear();
        NATURAL_SPAWN_SETTINGS.clear();
        ACTIVE_SPAWN_SETTINGS.clear();
        LOADOUTS.clear();
        LoadoutParsing.clearCache();
        NaturalSpawnBoost.invalidate();
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }

        try (Reader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(savePath), StandardCharsets.UTF_8))) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }

            for (Map.Entry<String, JsonElement> mobEntry : root.entrySet()) {
                ResourceLocation mobId = ResourceLocation.tryParse(mobEntry.getKey());
                if (mobId == null || !mobEntry.getValue().isJsonObject()) {
                    continue;
                }

                EnumMap<MobSpawnType, Boolean> map = new EnumMap<>(MobSpawnType.class);
                for (Map.Entry<String, JsonElement> typeEntry : mobEntry.getValue().getAsJsonObject().entrySet()) {
                    if (typeEntry.getKey().equals(ATTRIBUTES_KEY) && typeEntry.getValue().isJsonObject()) {
                        loadAttributeOverrides(mobId, typeEntry.getValue().getAsJsonObject());
                        continue;
                    }
                    if ((typeEntry.getKey().equals(VANILLA_SPAWN_KEY)
                            || typeEntry.getKey().equals(LEGACY_SPAWN_RESTRICTIONS_KEY))
                            && typeEntry.getValue().isJsonObject()) {
                        if (typeEntry.getKey().equals(LEGACY_SPAWN_RESTRICTIONS_KEY)
                                && mobEntry.getValue().getAsJsonObject().has(VANILLA_SPAWN_KEY)) {
                            continue;
                        }
                        NaturalSpawnSettings settings = NaturalSpawnSettingsJsonCodec.decode(
                                typeEntry.getValue().getAsJsonObject());
                        if (!settings.isDefault()) {
                            NATURAL_SPAWN_SETTINGS.put(mobId, settings);
                        }
                        continue;
                    }
                    if (typeEntry.getKey().equals(EXTRA_SPAWN_KEY)
                            && typeEntry.getValue().isJsonObject()) {
                        ActiveSpawnSettings settings = ActiveSpawnSettingsJsonCodec.decode(
                                typeEntry.getValue().getAsJsonObject());
                        if (!settings.isDefault()) {
                            ACTIVE_SPAWN_SETTINGS.put(mobId, settings);
                        }
                        continue;
                    }
                    if (typeEntry.getKey().equals(LOADOUT_KEY) && typeEntry.getValue().isJsonObject()) {
                        MobLoadout loadout = MobLoadoutJsonCodec.decode(typeEntry.getValue().getAsJsonObject());
                        if (!loadout.isDefault()) {
                            LOADOUTS.put(mobId, loadout);
                        }
                        continue;
                    }
                    MobSpawnType type = parseSpawnType(typeEntry.getKey());
                    if (type != null && typeEntry.getValue().isJsonPrimitive()) {
                        map.put(type, typeEntry.getValue().getAsBoolean());
                    }
                }
                if (!map.isEmpty()) {
                    RULES.put(mobId, map);
                }
            }
            MobSpawnController.LOGGER.info("Loaded {} mob spawn rule entries", RULES.size());
        } catch (Exception e) {
            MobSpawnController.LOGGER.error("Failed to load mob spawn rules", e);
        }
    }

    private static void loadAttributeOverrides(ResourceLocation mobId, JsonObject attributeObj) {
        Map<ResourceLocation, Double> attributes = new HashMap<>();
        for (Map.Entry<String, JsonElement> attributeEntry : attributeObj.entrySet()) {
            ResourceLocation attributeId = ResourceLocation.tryParse(attributeEntry.getKey());
            if (attributeId != null && attributeEntry.getValue().isJsonPrimitive()
                    && attributeEntry.getValue().getAsJsonPrimitive().isNumber()) {
                attributes.put(attributeId, attributeEntry.getValue().getAsDouble());
            }
        }
        if (!attributes.isEmpty()) {
            ATTRIBUTE_OVERRIDES.put(mobId, attributes);
        }
    }

    public static MobSpawnType parseSpawnType(String name) {
        for (MobSpawnType type : MobSpawnType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    /** Applies the spawn-type switch first, then the detailed conditions when their type filter matches. */
    public static boolean isSpawnAllowed(Entity entity, ServerLevelAccessor level, MobSpawnType spawnType) {
        return isSpawnTypeAllowed(entity, spawnType) && isNaturalSpawnAllowed(entity, level, spawnType);
    }

    /** Applies only the general spawn-type switch, without the vanilla-spawn condition page. */
    public static boolean isSpawnTypeAllowed(Entity entity, MobSpawnType spawnType) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Boolean allowed = getAllowed(mobId, spawnType);
        return allowed == null || allowed;
    }

    /** True when every spawn-type switch is off, which the GUI lists as disabled. */
    public static boolean isFullyDisabled(Entity entity) {
        return isFullyDisabled(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static boolean isFullyDisabled(ResourceLocation mobId) {
        EnumMap<MobSpawnType, Boolean> map = RULES.get(mobId);
        return map != null && map.size() == SPAWN_TYPE_COUNT && !map.containsValue(Boolean.TRUE);
    }

    /** Unknown origins only use detailed conditions when the user explicitly selected them. */
    public static boolean hasUnknownSpawnConditions(Entity entity) {
        NaturalSpawnSettings settings = NATURAL_SPAWN_SETTINGS.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        return settings != null && settings.appliesTo(null);
    }

    /** Returns false when an applicable spawn fails one of the configured GUI conditions. */
    public static boolean isNaturalSpawnAllowed(Entity entity, ServerLevelAccessor level, MobSpawnType spawnType) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        NaturalSpawnSettings settings = NATURAL_SPAWN_SETTINGS.get(mobId);
        if (settings == null || !settings.appliesTo(spawnType)) {
            return true;
        }
        // Common deny-all rules need neither randomness nor any world queries.
        if (settings.chance() <= 0.0 || (settings.chance() < 1.0
                && level.getRandom().nextDouble() >= settings.chance())) {
            return false;
        }
        if (settings.maxNearby() != null && settings.maxNearby() == 0) return false;

        BlockPos pos = entity.blockPosition();
        if ((settings.minHeight() != null && pos.getY() < settings.minHeight())
                || (settings.maxHeight() != null && pos.getY() > settings.maxHeight())) {
            return false;
        }

        ServerLevel serverLevel = level.getLevel();
        ResourceLocation dimensionId = serverLevel.dimension().location();
        if ((!settings.dimensions().isEmpty() && !settings.dimensions().contains(dimensionId))
                || settings.excludedDimensions().contains(dimensionId)) {
            return false;
        }
        if (settings.minTime() != null || settings.maxTime() != null
                || settings.minDay() != null || settings.maxDay() != null) {
            long absoluteTime = serverLevel.getDayTime();
            if (!matchesTimeRange(absoluteTime % 24000L, settings.minTime(), settings.maxTime())
                    || (settings.minDay() != null && absoluteTime / 24000L < settings.minDay())
                    || (settings.maxDay() != null && absoluteTime / 24000L > settings.maxDay())) {
                return false;
            }
        }
        if (!settings.moonPhases().isEmpty() || !settings.excludedMoonPhases().isEmpty()) {
            int moonPhase = serverLevel.getMoonPhase();
            if ((!settings.moonPhases().isEmpty() && !settings.moonPhases().contains(moonPhase))
                    || settings.excludedMoonPhases().contains(moonPhase)) return false;
        }
        if ((settings.weather() != NaturalSpawnSettings.WeatherMode.ANY
                && !matchesWeather(settings.weather(), serverLevel))
                || (settings.difficulty() != NaturalSpawnSettings.DifficultyMode.ANY
                && !matchesDifficulty(settings.difficulty(), level.getDifficulty()))) {
            return false;
        }

        if (settings.minTotalLight() != null || settings.maxTotalLight() != null) {
            int light = level.getMaxLocalRawBrightness(pos);
            if ((settings.minTotalLight() != null && light < settings.minTotalLight())
                    || (settings.maxTotalLight() != null && light > settings.maxTotalLight())) return false;
        }
        if (settings.minSkyLight() != null || settings.maxSkyLight() != null) {
            int light = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
            if ((settings.minSkyLight() != null && light < settings.minSkyLight())
                    || (settings.maxSkyLight() != null && light > settings.maxSkyLight())) return false;
        }
        if (settings.minBlockLight() != null || settings.maxBlockLight() != null) {
            int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
            if ((settings.minBlockLight() != null && light < settings.minBlockLight())
                    || (settings.maxBlockLight() != null && light > settings.maxBlockLight())) return false;
        }

        if (settings.minPlayerDistance() != null || settings.maxPlayerDistance() != null) {
            Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), -1.0, false);
            if (nearest == null) {
                if (settings.maxPlayerDistance() != null) return false;
            } else {
                double distanceSquared = nearest.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if ((settings.minPlayerDistance() != null
                        && distanceSquared < settings.minPlayerDistance() * settings.minPlayerDistance())
                        || (settings.maxPlayerDistance() != null
                        && distanceSquared > settings.maxPlayerDistance() * settings.maxPlayerDistance())) return false;
            }
        }
        if (settings.minWorldSpawnDistance() != null || settings.maxWorldSpawnDistance() != null) {
            double distanceSquared = pos.distSqr(serverLevel.getSharedSpawnPos());
            if ((settings.minWorldSpawnDistance() != null
                    && distanceSquared < settings.minWorldSpawnDistance() * settings.minWorldSpawnDistance())
                    || (settings.maxWorldSpawnDistance() != null
                    && distanceSquared > settings.maxWorldSpawnDistance() * settings.maxWorldSpawnDistance())) return false;
        }
        if (settings.minLocalDifficulty() != null || settings.maxLocalDifficulty() != null) {
            float difficulty = level.getCurrentDifficultyAt(pos).getEffectiveDifficulty();
            if ((settings.minLocalDifficulty() != null && difficulty < settings.minLocalDifficulty())
                    || (settings.maxLocalDifficulty() != null && difficulty > settings.maxLocalDifficulty())) return false;
        }
        if (settings.minPlayers() != null || settings.maxPlayers() != null) {
            long playerCount = serverLevel.players().stream().filter(player -> !player.isSpectator()).count();
            if ((settings.minPlayers() != null && playerCount < settings.minPlayers())
                    || (settings.maxPlayers() != null && playerCount > settings.maxPlayers())) return false;
        }

        if (!settings.biomes().isEmpty() || !settings.excludedBiomes().isEmpty()
                || !settings.biomeTags().isEmpty() || !settings.excludedBiomeTags().isEmpty()) {
            Holder<Biome> biome = level.getBiome(pos);
            ResourceLocation biomeId = biome.unwrapKey().map(key -> key.location()).orElse(null);
            boolean hasWhitelist = !settings.biomes().isEmpty() || !settings.biomeTags().isEmpty();
            boolean matchesWhitelist = settings.biomes().contains(biomeId)
                    || matchesAnyTag(biome, settings.biomeTags(), Registries.BIOME);
            if ((hasWhitelist && !matchesWhitelist) || settings.excludedBiomes().contains(biomeId)
                    || matchesAnyTag(biome, settings.excludedBiomeTags(), Registries.BIOME)) return false;
        }
        if (!SereneSeasonsCompat.isCurrentSeasonAllowed(serverLevel, settings.seasons(), settings.excludedSeasons())) {
            return false;
        }
        if (settings.skyMode() != NaturalSpawnSettings.SkyMode.ANY) {
            boolean seesSky = level.canSeeSky(pos);
            if ((settings.skyMode() == NaturalSpawnSettings.SkyMode.MUST_SEE && !seesSky)
                    || (settings.skyMode() == NaturalSpawnSettings.SkyMode.MUST_NOT_SEE && seesSky)) return false;
        }
        if ((settings.fluidMode() != NaturalSpawnSettings.FluidMode.ANY
                && !matchesFluid(settings.fluidMode(), level.getFluidState(pos)))
                || !matchesSlimeChunk(settings.slimeChunkMode(), serverLevel, pos)) return false;

        if ((!settings.structures().isEmpty() && !matchesAnyStructure(serverLevel, pos, settings.structures()))
                || (!settings.excludedStructures().isEmpty()
                && matchesAnyStructure(serverLevel, pos, settings.excludedStructures()))) return false;

        if (!settings.blocksBelow().isEmpty() || !settings.excludedBlocksBelow().isEmpty()) {
            BlockState state = level.getBlockState(pos.below());
            if ((!settings.blocksBelow().isEmpty() && !matchesAnyBlock(state, settings.blocksBelow()))
                    || matchesAnyBlock(state, settings.excludedBlocksBelow())) return false;
        }
        if (!settings.blocksAt().isEmpty() || !settings.excludedBlocksAt().isEmpty()) {
            BlockState state = level.getBlockState(pos);
            if ((!settings.blocksAt().isEmpty() && !matchesAnyBlock(state, settings.blocksAt()))
                    || matchesAnyBlock(state, settings.excludedBlocksAt())) return false;
        }
        if (!settings.blocksAbove().isEmpty() || !settings.excludedBlocksAbove().isEmpty()) {
            BlockState state = level.getBlockState(pos.above());
            if ((!settings.blocksAbove().isEmpty() && !matchesAnyBlock(state, settings.blocksAbove()))
                    || matchesAnyBlock(state, settings.excludedBlocksAbove())) return false;
        }
        if (settings.maxNearby() != null) {
            AABB area = new AABB(pos).inflate(settings.nearbyRadius());
            int nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                    other -> other != entity && other.getType() == entity.getType()).size();
            if (nearby >= settings.maxNearby()) return false;
        }
        return true;
    }

    private static boolean matchesFluid(NaturalSpawnSettings.FluidMode mode, FluidState fluid) {
        return switch (mode) {
            case ANY -> true;
            case DRY -> fluid.isEmpty();
            case WATER -> fluid.is(FluidTags.WATER);
            case LAVA -> fluid.is(FluidTags.LAVA);
        };
    }

    private static boolean matchesTimeRange(long time, Integer minTime, Integer maxTime) {
        if (minTime == null && maxTime == null) return true;
        if (minTime == null) return time <= maxTime;
        if (maxTime == null) return time >= minTime;
        return minTime <= maxTime
                ? time >= minTime && time <= maxTime
                : time >= minTime || time <= maxTime;
    }

    private static boolean matchesSlimeChunk(NaturalSpawnSettings.SlimeChunkMode mode,
                                             ServerLevel level, BlockPos pos) {
        if (mode == NaturalSpawnSettings.SlimeChunkMode.ANY) return true;
        boolean slimeChunk = WorldgenRandom.seedSlimeChunk(pos.getX() >> 4, pos.getZ() >> 4,
                level.getSeed(), 987234911L).nextInt(10) == 0;
        return mode == NaturalSpawnSettings.SlimeChunkMode.MUST ? slimeChunk : !slimeChunk;
    }

    private static <T> boolean matchesAnyTag(Holder<T> holder, List<ResourceLocation> tags,
                                             ResourceKey<? extends Registry<T>> registry) {
        for (ResourceLocation tag : tags) {
            if (holder.is(TagKey.create(registry, tag))) return true;
        }
        return false;
    }

    private static boolean matchesAnyStructure(ServerLevel level, BlockPos pos, List<String> selectors) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            // A structure's full bounding box can contain gaps between its individual pieces.
            // Natural spawn positions in villages and other multi-piece structures commonly land
            // in those gaps, so piece-only checks incorrectly reject valid whitelist matches.
            if (!level.structureManager().getStructureAt(pos, structure).isValid()) continue;

            ResourceLocation structureId = registry.getKey(structure);
            ResourceKey<Structure> structureKey = registry.getResourceKey(structure).orElse(null);
            for (String selector : selectors) {
                if (selector.equals("*")) return true;

                boolean tag = selector.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
                if (id == null) continue;
                if (!tag && id.equals(structureId)) return true;
                if (tag && structureKey != null
                        && registry.getHolder(structureKey)
                        .map(holder -> holder.is(TagKey.create(Registries.STRUCTURE, id)))
                        .orElse(false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAnyBlock(BlockState state, List<String> selectors) {
        for (String selector : selectors) {
            boolean tag = selector.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
            if (id == null) continue;
            if (tag ? state.is(TagKey.create(Registries.BLOCK, id))
                    : BuiltInRegistries.BLOCK.getOptional(id).map(state::is).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesWeather(NaturalSpawnSettings.WeatherMode mode, ServerLevel level) {
        return switch (mode) {
            case ANY -> true;
            case CLEAR -> !level.isRaining();
            case RAIN -> level.isRaining() && !level.isThundering();
            case THUNDER -> level.isThundering();
        };
    }

    private static boolean matchesDifficulty(NaturalSpawnSettings.DifficultyMode mode, Difficulty difficulty) {
        return mode == NaturalSpawnSettings.DifficultyMode.ANY || mode.name().equals(difficulty.name());
    }

}
