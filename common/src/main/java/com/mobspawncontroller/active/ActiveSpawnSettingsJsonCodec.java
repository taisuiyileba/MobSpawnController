package com.mobspawncontroller.active;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ActiveSpawnSettingsJsonCodec {

    private ActiveSpawnSettingsJsonCodec() {
    }

    public static JsonObject encode(ActiveSpawnSettings settings) {
        JsonObject json = new JsonObject();
        if (settings.enabled()) json.addProperty("enabled", true);
        if (settings.chancePerSecond() != 1.0) json.addProperty("chance_per_second", settings.chancePerSecond());
        if (settings.attempts() != 4) json.addProperty("attempts", settings.attempts());
        if (settings.minAmount() != 1) json.addProperty("min_amount", settings.minAmount());
        if (settings.maxAmount() != 1) json.addProperty("max_amount", settings.maxAmount());
        if (settings.minDistance() != 24) json.addProperty("min_distance", settings.minDistance());
        if (settings.maxDistance() != 64) json.addProperty("max_distance", settings.maxDistance());
        addNullable(json, "min_height", settings.minHeight());
        addNullable(json, "max_height", settings.maxHeight());
        addNullable(json, "min_day", settings.minDay());
        addNullable(json, "max_day", settings.maxDay());
        addNullable(json, "min_time", settings.minTime());
        addNullable(json, "max_time", settings.maxTime());
        addNullable(json, "min_total_light", settings.minTotalLight());
        addNullable(json, "max_total_light", settings.maxTotalLight());
        addNullable(json, "max_world_count", settings.maxWorldCount());
        addNullable(json, "max_nearby_count", settings.maxNearbyCount());
        if (settings.nearbyRadius() != 32.0) json.addProperty("nearby_radius", settings.nearbyRadius());
        if (settings.placement() != ActiveSpawnSettings.PlacementMode.GROUND) {
            json.addProperty("placement", settings.placement().name().toLowerCase(Locale.ROOT));
        }
        if (!settings.obeySpawnRules()) json.addProperty("obey_spawn_rules", false);
        if (settings.skyMode() != ActiveSpawnSettings.SkyMode.ANY) {
            json.addProperty("sky", settings.skyMode().name().toLowerCase(Locale.ROOT));
        }
        addNullable(json, "min_players", settings.minPlayers());
        addNullable(json, "max_players", settings.maxPlayers());
        addNullable(json, "min_world_spawn_distance", settings.minWorldSpawnDistance());
        addNullable(json, "max_world_spawn_distance", settings.maxWorldSpawnDistance());
        addNullable(json, "min_local_difficulty", settings.minLocalDifficulty());
        addNullable(json, "max_local_difficulty", settings.maxLocalDifficulty());
        addNullable(json, "min_sky_light", settings.minSkyLight());
        addNullable(json, "max_sky_light", settings.maxSkyLight());
        addNullable(json, "min_block_light", settings.minBlockLight());
        addNullable(json, "max_block_light", settings.maxBlockLight());
        addIntList(json, "moon_phases", settings.moonPhases());
        addIntList(json, "excluded_moon_phases", settings.excludedMoonPhases());
        addEnum(json, "weather", settings.weather(), NaturalSpawnSettings.WeatherMode.ANY);
        addEnum(json, "difficulty", settings.difficulty(), NaturalSpawnSettings.DifficultyMode.ANY);
        addEnum(json, "slime_chunk", settings.slimeChunkMode(), NaturalSpawnSettings.SlimeChunkMode.ANY);
        addResourceList(json, "dimensions", settings.dimensions());
        addResourceList(json, "excluded_dimensions", settings.excludedDimensions());
        addResourceList(json, "biomes", settings.biomes());
        addResourceList(json, "excluded_biomes", settings.excludedBiomes());
        addResourceList(json, "biome_tags", settings.biomeTags());
        addResourceList(json, "excluded_biome_tags", settings.excludedBiomeTags());
        addStringList(json, "seasons", settings.seasons());
        addStringList(json, "excluded_seasons", settings.excludedSeasons());
        addStringList(json, "structures", settings.structures());
        addStringList(json, "excluded_structures", settings.excludedStructures());
        addStringList(json, "blocks_below", settings.blocksBelow());
        addStringList(json, "excluded_blocks_below", settings.excludedBlocksBelow());
        addStringList(json, "blocks_at", settings.blocksAt());
        addStringList(json, "excluded_blocks_at", settings.excludedBlocksAt());
        addStringList(json, "blocks_above", settings.blocksAbove());
        addStringList(json, "excluded_blocks_above", settings.excludedBlocksAbove());
        return json;
    }

    public static ActiveSpawnSettings decode(JsonObject json) {
        return new ActiveSpawnSettings(getBoolean(json, "enabled", false),
                getDouble(json, "chance_per_second", 1.0), getInt(json, "attempts", 4),
                getInt(json, "min_amount", 1), getInt(json, "max_amount", 1),
                getInt(json, "min_distance", 24), getInt(json, "max_distance", 64),
                getNullableInt(json, "min_height"), getNullableInt(json, "max_height"),
                getNullableInt(json, "min_day"), getNullableInt(json, "max_day"),
                getNullableInt(json, "min_time"), getNullableInt(json, "max_time"),
                getNullableInt(json, "min_total_light"), getNullableInt(json, "max_total_light"),
                getNullableInt(json, "max_world_count"), getNullableInt(json, "max_nearby_count"),
                getDouble(json, "nearby_radius", 32.0),
                getEnum(json, "placement", ActiveSpawnSettings.PlacementMode.class,
                        ActiveSpawnSettings.PlacementMode.GROUND),
                getBoolean(json, "obey_spawn_rules", true),
                getEnum(json, "sky", ActiveSpawnSettings.SkyMode.class, ActiveSpawnSettings.SkyMode.ANY),
                getNullableInt(json, "min_players"), getNullableInt(json, "max_players"),
                getNullableDouble(json, "min_world_spawn_distance"),
                getNullableDouble(json, "max_world_spawn_distance"),
                getNullableDouble(json, "min_local_difficulty"),
                getNullableDouble(json, "max_local_difficulty"),
                getNullableInt(json, "min_sky_light"), getNullableInt(json, "max_sky_light"),
                getNullableInt(json, "min_block_light"), getNullableInt(json, "max_block_light"),
                getIntList(json, "moon_phases"), getIntList(json, "excluded_moon_phases"),
                getEnum(json, "weather", NaturalSpawnSettings.WeatherMode.class,
                        NaturalSpawnSettings.WeatherMode.ANY),
                getEnum(json, "difficulty", NaturalSpawnSettings.DifficultyMode.class,
                        NaturalSpawnSettings.DifficultyMode.ANY),
                getEnum(json, "slime_chunk", NaturalSpawnSettings.SlimeChunkMode.class,
                        NaturalSpawnSettings.SlimeChunkMode.ANY),
                getResourceList(json, "dimensions"), getResourceList(json, "excluded_dimensions"),
                getResourceList(json, "biomes"), getResourceList(json, "excluded_biomes"),
                getResourceList(json, "biome_tags"), getResourceList(json, "excluded_biome_tags"),
                getStringList(json, "seasons"), getStringList(json, "excluded_seasons"),
                getStringList(json, "structures"), getStringList(json, "excluded_structures"),
                getStringList(json, "blocks_below"), getStringList(json, "excluded_blocks_below"),
                getStringList(json, "blocks_at"), getStringList(json, "excluded_blocks_at"),
                getStringList(json, "blocks_above"), getStringList(json, "excluded_blocks_above"));
    }

    private static void addNullable(JsonObject json, String key, Number value) {
        if (value != null) json.addProperty(key, value);
    }

    private static void addResourceList(JsonObject json, String key, List<ResourceLocation> values) {
        addStringList(json, key, values.stream().map(ResourceLocation::toString).toList());
    }

    private static void addIntList(JsonObject json, String key, List<Integer> values) {
        if (values.isEmpty()) return;
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add(key, array);
    }

    private static <T extends Enum<T>> void addEnum(JsonObject json, String key, T value, T defaultValue) {
        if (value != defaultValue) json.addProperty(key, value.name().toLowerCase(Locale.ROOT));
    }

    private static void addStringList(JsonObject json, String key, List<String> values) {
        if (values.isEmpty()) return;
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add(key, array);
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        Integer value = getNullableInt(json, key);
        return value == null ? fallback : value;
    }

    private static Integer getNullableInt(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : null;
    }

    private static Double getNullableDouble(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : null;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
    }

    private static <T extends Enum<T>> T getEnum(JsonObject json, String key, Class<T> type, T fallback) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return fallback;
        try {
            return Enum.valueOf(type, json.get(key).getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static List<ResourceLocation> getResourceList(JsonObject json, String key) {
        List<ResourceLocation> values = new ArrayList<>();
        for (String raw : getStringList(json, key)) {
            ResourceLocation value = ResourceLocation.tryParse(raw);
            if (value != null) values.add(value);
        }
        return values;
    }

    private static List<String> getStringList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) values.add(element.getAsString());
        }
        return values;
    }

    private static List<Integer> getIntList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<Integer> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) values.add(element.getAsInt());
        }
        return values;
    }
}

