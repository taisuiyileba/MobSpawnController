package com.mobspawncontroller.natural;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JSON persistence codec kept separate from rule evaluation. */
public final class NaturalSpawnSettingsJsonCodec {

    private NaturalSpawnSettingsJsonCodec() {
    }

    public static JsonObject encode(NaturalSpawnSettings settings) {
        JsonObject json = new JsonObject();
        if (settings.chance() != 1.0) json.addProperty("chance", settings.chance());
        if (settings.spawnMultiplier() != 1.0) {
            json.addProperty("spawn_multiplier", settings.spawnMultiplier());
        }
        addNullable(json, "min_height", settings.minHeight());
        addNullable(json, "max_height", settings.maxHeight());
        addNullable(json, "min_total_light", settings.minTotalLight());
        addNullable(json, "max_total_light", settings.maxTotalLight());
        addNullable(json, "min_time", settings.minTime());
        addNullable(json, "max_time", settings.maxTime());
        addNullable(json, "min_day", settings.minDay());
        addNullable(json, "max_day", settings.maxDay());
        addIntList(json, "moon_phases", settings.moonPhases());
        addIntList(json, "excluded_moon_phases", settings.excludedMoonPhases());
        addNullable(json, "min_player_distance", settings.minPlayerDistance());
        addNullable(json, "max_player_distance", settings.maxPlayerDistance());
        addNullable(json, "min_world_spawn_distance", settings.minWorldSpawnDistance());
        addNullable(json, "max_world_spawn_distance", settings.maxWorldSpawnDistance());
        addNullable(json, "min_local_difficulty", settings.minLocalDifficulty());
        addNullable(json, "max_local_difficulty", settings.maxLocalDifficulty());
        addEnum(json, "weather", settings.weather(), NaturalSpawnSettings.WeatherMode.ANY);
        addEnum(json, "difficulty", settings.difficulty(), NaturalSpawnSettings.DifficultyMode.ANY);
        addEnum(json, "sky", settings.skyMode(), NaturalSpawnSettings.SkyMode.ANY);
        addEnum(json, "fluid", settings.fluidMode(), NaturalSpawnSettings.FluidMode.ANY);
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
        addNullable(json, "min_players", settings.minPlayers());
        addNullable(json, "max_players", settings.maxPlayers());
        addNullable(json, "max_nearby", settings.maxNearby());
        if (settings.maxNearby() != null && settings.nearbyRadius() != 16.0) {
            json.addProperty("nearby_radius", settings.nearbyRadius());
        }
        addNullable(json, "min_sky_light", settings.minSkyLight());
        addNullable(json, "max_sky_light", settings.maxSkyLight());
        addNullable(json, "min_block_light", settings.minBlockLight());
        addNullable(json, "max_block_light", settings.maxBlockLight());
        addStringList(json, "blocks_at", settings.blocksAt());
        addStringList(json, "excluded_blocks_at", settings.excludedBlocksAt());
        addStringList(json, "blocks_above", settings.blocksAbove());
        addStringList(json, "excluded_blocks_above", settings.excludedBlocksAbove());
        addEnumList(json, "spawn_types", settings.spawnTypes(), true);
        addEnumList(json, "excluded_spawn_types", settings.excludedSpawnTypes(), false);
        return json;
    }

    public static NaturalSpawnSettings decode(JsonObject json) {
        return new NaturalSpawnSettings(getDouble(json, "chance", 1.0),
                getDouble(json, "spawn_multiplier", 1.0),
                getInteger(json, "min_height"), getInteger(json, "max_height"),
                getInteger(json, "min_total_light"), getInteger(json, "max_total_light"),
                getInteger(json, "min_time"), getInteger(json, "max_time"),
                getInteger(json, "min_day"), getInteger(json, "max_day"),
                getIntList(json, "moon_phases"), getIntList(json, "excluded_moon_phases"),
                getNullableDouble(json, "min_player_distance"), getNullableDouble(json, "max_player_distance"),
                getNullableDouble(json, "min_world_spawn_distance"),
                getNullableDouble(json, "max_world_spawn_distance"),
                getNullableDouble(json, "min_local_difficulty"),
                getNullableDouble(json, "max_local_difficulty"),
                getEnum(json, "weather", NaturalSpawnSettings.WeatherMode.class,
                        NaturalSpawnSettings.WeatherMode.ANY),
                getEnum(json, "difficulty", NaturalSpawnSettings.DifficultyMode.class,
                        NaturalSpawnSettings.DifficultyMode.ANY),
                getEnum(json, "sky", NaturalSpawnSettings.SkyMode.class, NaturalSpawnSettings.SkyMode.ANY),
                getEnum(json, "fluid", NaturalSpawnSettings.FluidMode.class, NaturalSpawnSettings.FluidMode.ANY),
                getEnum(json, "slime_chunk", NaturalSpawnSettings.SlimeChunkMode.class,
                        NaturalSpawnSettings.SlimeChunkMode.ANY),
                getResourceList(json, "dimensions"), getResourceList(json, "excluded_dimensions"),
                getResourceList(json, "biomes"), getResourceList(json, "excluded_biomes"),
                getResourceList(json, "biome_tags"), getResourceList(json, "excluded_biome_tags"),
                getStringList(json, "seasons"), getStringList(json, "excluded_seasons"),
                getStringList(json, "structures"), getStringList(json, "excluded_structures"),
                getStringList(json, "blocks_below"), getStringList(json, "excluded_blocks_below"),
                getInteger(json, "min_players"), getInteger(json, "max_players"),
                getInteger(json, "max_nearby"), getDouble(json, "nearby_radius", 16.0),
                getInteger(json, "min_sky_light"), getInteger(json, "max_sky_light"),
                getInteger(json, "min_block_light"), getInteger(json, "max_block_light"),
                getStringList(json, "blocks_at"), getStringList(json, "excluded_blocks_at"),
                getStringList(json, "blocks_above"), getStringList(json, "excluded_blocks_above"),
                getEnumList(json, "spawn_types", MobSpawnType.class, List.of(MobSpawnType.NATURAL)),
                getEnumList(json, "excluded_spawn_types", MobSpawnType.class, List.of()));
    }

    private static void addNullable(JsonObject json, String key, Number value) {
        if (value != null) json.addProperty(key, value);
    }

    private static <T extends Enum<T>> void addEnum(JsonObject json, String key, T value, T defaultValue) {
        if (value != defaultValue) json.addProperty(key, value.name().toLowerCase(Locale.ROOT));
    }

    private static void addResourceList(JsonObject json, String key, List<ResourceLocation> values) {
        addStringList(json, key, values.stream().map(ResourceLocation::toString).toList());
    }

    private static void addStringList(JsonObject json, String key, List<String> values) {
        if (values.isEmpty()) return;
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add(key, array);
    }

    private static void addIntList(JsonObject json, String key, List<Integer> values) {
        if (values.isEmpty()) return;
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add(key, array);
    }

    private static <T extends Enum<T>> void addEnumList(JsonObject json, String key, List<T> values,
                                                         boolean includeEmpty) {
        if (values.isEmpty() && !includeEmpty) return;
        JsonArray array = new JsonArray();
        values.forEach(value -> array.add(value.name().toLowerCase(Locale.ROOT)));
        json.add(key, array);
    }

    private static Integer getInteger(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : null;
    }

    private static Double getNullableDouble(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : null;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        Double value = getNullableDouble(json, key);
        return value == null ? fallback : value;
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
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<ResourceLocation> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            ResourceLocation value = element.isJsonPrimitive()
                    ? ResourceLocation.tryParse(element.getAsString()) : null;
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

    private static <T extends Enum<T>> List<T> getEnumList(JsonObject json, String key, Class<T> type,
                                                            List<T> fallback) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return fallback;
        List<T> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) continue;
            try {
                values.add(Enum.valueOf(type, element.getAsString().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown values so configurations remain forward-compatible.
            }
        }
        return values;
    }
}
