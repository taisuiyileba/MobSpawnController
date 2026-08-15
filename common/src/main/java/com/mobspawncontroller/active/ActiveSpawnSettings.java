package com.mobspawncontroller.active;

import com.mobspawncontroller.natural.NaturalSpawnSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A GUI-oriented subset of InControl's spawner rule settings.
 * Empty selector lists and null bounds mean that the matching condition is unrestricted.
 */
public record ActiveSpawnSettings(
        boolean enabled,
        double chancePerSecond,
        int attempts,
        int minAmount,
        int maxAmount,
        int minDistance,
        int maxDistance,
        Integer minHeight,
        Integer maxHeight,
        Integer minDay,
        Integer maxDay,
        Integer minTime,
        Integer maxTime,
        Integer minTotalLight,
        Integer maxTotalLight,
        Integer maxWorldCount,
        Integer maxNearbyCount,
        double nearbyRadius,
        PlacementMode placement,
        boolean obeySpawnRules,
        SkyMode skyMode,
        Integer minPlayers,
        Integer maxPlayers,
        Double minWorldSpawnDistance,
        Double maxWorldSpawnDistance,
        Double minLocalDifficulty,
        Double maxLocalDifficulty,
        Integer minSkyLight,
        Integer maxSkyLight,
        Integer minBlockLight,
        Integer maxBlockLight,
        List<Integer> moonPhases,
        List<Integer> excludedMoonPhases,
        NaturalSpawnSettings.WeatherMode weather,
        NaturalSpawnSettings.DifficultyMode difficulty,
        NaturalSpawnSettings.SlimeChunkMode slimeChunkMode,
        List<ResourceLocation> dimensions,
        List<ResourceLocation> excludedDimensions,
        List<ResourceLocation> biomes,
        List<ResourceLocation> excludedBiomes,
        List<ResourceLocation> biomeTags,
        List<ResourceLocation> excludedBiomeTags,
        List<String> seasons,
        List<String> excludedSeasons,
        List<String> structures,
        List<String> excludedStructures,
        List<String> blocksBelow,
        List<String> excludedBlocksBelow,
        List<String> blocksAt,
        List<String> excludedBlocksAt,
        List<String> blocksAbove,
        List<String> excludedBlocksAbove
) {

    public ActiveSpawnSettings {
        chancePerSecond = Double.isFinite(chancePerSecond) ? clamp(chancePerSecond, 0.0, 1.0) : 1.0;
        attempts = clamp(attempts, 1, 128);
        minAmount = clamp(minAmount, 1, 64);
        maxAmount = clamp(maxAmount, 1, 64);
        maxAmount = Math.max(minAmount, maxAmount);
        minDistance = clamp(minDistance, 0, 256);
        maxDistance = clamp(maxDistance, 1, 256);
        maxDistance = Math.max(minDistance, maxDistance);
        minDay = nonNegative(minDay);
        maxDay = nonNegative(maxDay);
        minTime = clampNullable(minTime, 0, 23999);
        maxTime = clampNullable(maxTime, 0, 23999);
        minTotalLight = clampNullable(minTotalLight, 0, 15);
        maxTotalLight = clampNullable(maxTotalLight, 0, 15);
        maxWorldCount = positiveNullable(maxWorldCount);
        maxNearbyCount = positiveNullable(maxNearbyCount);
        nearbyRadius = Double.isFinite(nearbyRadius) ? clamp(nearbyRadius, 1.0, 256.0) : 32.0;
        placement = placement == null ? PlacementMode.GROUND : placement;
        skyMode = skyMode == null ? SkyMode.ANY : skyMode;
        minPlayers = nonNegative(minPlayers);
        maxPlayers = nonNegative(maxPlayers);
        minWorldSpawnDistance = nonNegative(minWorldSpawnDistance);
        maxWorldSpawnDistance = nonNegative(maxWorldSpawnDistance);
        minLocalDifficulty = nonNegative(minLocalDifficulty);
        maxLocalDifficulty = nonNegative(maxLocalDifficulty);
        minSkyLight = clampNullable(minSkyLight, 0, 15);
        maxSkyLight = clampNullable(maxSkyLight, 0, 15);
        minBlockLight = clampNullable(minBlockLight, 0, 15);
        maxBlockLight = clampNullable(maxBlockLight, 0, 15);
        moonPhases = immutableMoonPhases(moonPhases);
        excludedMoonPhases = immutableMoonPhases(excludedMoonPhases);
        weather = weather == null ? NaturalSpawnSettings.WeatherMode.ANY : weather;
        difficulty = difficulty == null ? NaturalSpawnSettings.DifficultyMode.ANY : difficulty;
        slimeChunkMode = slimeChunkMode == null ? NaturalSpawnSettings.SlimeChunkMode.ANY : slimeChunkMode;
        dimensions = immutableResources(dimensions);
        excludedDimensions = immutableResources(excludedDimensions);
        biomes = immutableResources(biomes);
        excludedBiomes = immutableResources(excludedBiomes);
        biomeTags = immutableResources(biomeTags);
        excludedBiomeTags = immutableResources(excludedBiomeTags);
        seasons = immutableSelectors(seasons);
        excludedSeasons = immutableSelectors(excludedSeasons);
        structures = immutableSelectors(structures);
        excludedStructures = immutableSelectors(excludedStructures);
        blocksBelow = immutableSelectors(blocksBelow);
        excludedBlocksBelow = immutableSelectors(excludedBlocksBelow);
        blocksAt = immutableSelectors(blocksAt);
        excludedBlocksAt = immutableSelectors(excludedBlocksAt);
        blocksAbove = immutableSelectors(blocksAbove);
        excludedBlocksAbove = immutableSelectors(excludedBlocksAbove);
    }

    public static ActiveSpawnSettings defaults() {
        return new ActiveSpawnSettings(false, 1.0, 4, 1, 1, 24, 64,
                null, null, null, null, null, null, null, null,
                null, null, 32.0, PlacementMode.GROUND, true, SkyMode.ANY,
                null, null, null, null, null, null,
                null, null, null, null, List.of(), List.of(),
                NaturalSpawnSettings.WeatherMode.ANY, NaturalSpawnSettings.DifficultyMode.ANY,
                NaturalSpawnSettings.SlimeChunkMode.ANY,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    public boolean isDefault() {
        return equals(defaults());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeDouble(chancePerSecond);
        buf.writeVarInt(attempts);
        buf.writeVarInt(minAmount);
        buf.writeVarInt(maxAmount);
        buf.writeVarInt(minDistance);
        buf.writeVarInt(maxDistance);
        writeNullableInt(buf, minHeight);
        writeNullableInt(buf, maxHeight);
        writeNullableInt(buf, minDay);
        writeNullableInt(buf, maxDay);
        writeNullableInt(buf, minTime);
        writeNullableInt(buf, maxTime);
        writeNullableInt(buf, minTotalLight);
        writeNullableInt(buf, maxTotalLight);
        writeNullableInt(buf, maxWorldCount);
        writeNullableInt(buf, maxNearbyCount);
        buf.writeDouble(nearbyRadius);
        buf.writeEnum(placement);
        buf.writeBoolean(obeySpawnRules);
        buf.writeEnum(skyMode);
        writeNullableInt(buf, minPlayers);
        writeNullableInt(buf, maxPlayers);
        writeNullableDouble(buf, minWorldSpawnDistance);
        writeNullableDouble(buf, maxWorldSpawnDistance);
        writeNullableDouble(buf, minLocalDifficulty);
        writeNullableDouble(buf, maxLocalDifficulty);
        writeNullableInt(buf, minSkyLight);
        writeNullableInt(buf, maxSkyLight);
        writeNullableInt(buf, minBlockLight);
        writeNullableInt(buf, maxBlockLight);
        writeInts(buf, moonPhases);
        writeInts(buf, excludedMoonPhases);
        buf.writeEnum(weather);
        buf.writeEnum(difficulty);
        buf.writeEnum(slimeChunkMode);
        writeResources(buf, dimensions);
        writeResources(buf, excludedDimensions);
        writeResources(buf, biomes);
        writeResources(buf, excludedBiomes);
        writeResources(buf, biomeTags);
        writeResources(buf, excludedBiomeTags);
        writeSelectors(buf, seasons);
        writeSelectors(buf, excludedSeasons);
        writeSelectors(buf, structures);
        writeSelectors(buf, excludedStructures);
        writeSelectors(buf, blocksBelow);
        writeSelectors(buf, excludedBlocksBelow);
        writeSelectors(buf, blocksAt);
        writeSelectors(buf, excludedBlocksAt);
        writeSelectors(buf, blocksAbove);
        writeSelectors(buf, excludedBlocksAbove);
    }

    public static ActiveSpawnSettings read(FriendlyByteBuf buf) {
        return new ActiveSpawnSettings(buf.readBoolean(), buf.readDouble(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                readNullableInt(buf), readNullableInt(buf), readNullableInt(buf), readNullableInt(buf),
                readNullableInt(buf), readNullableInt(buf), readNullableInt(buf), readNullableInt(buf),
                readNullableInt(buf), readNullableInt(buf), buf.readDouble(),
                buf.readEnum(PlacementMode.class), buf.readBoolean(), buf.readEnum(SkyMode.class),
                readNullableInt(buf), readNullableInt(buf),
                readNullableDouble(buf), readNullableDouble(buf),
                readNullableDouble(buf), readNullableDouble(buf),
                readNullableInt(buf), readNullableInt(buf), readNullableInt(buf), readNullableInt(buf),
                readInts(buf), readInts(buf),
                buf.readEnum(NaturalSpawnSettings.WeatherMode.class),
                buf.readEnum(NaturalSpawnSettings.DifficultyMode.class),
                buf.readEnum(NaturalSpawnSettings.SlimeChunkMode.class),
                readResources(buf), readResources(buf), readResources(buf), readResources(buf),
                readResources(buf), readResources(buf),
                readSelectors(buf), readSelectors(buf), readSelectors(buf), readSelectors(buf),
                readSelectors(buf), readSelectors(buf), readSelectors(buf), readSelectors(buf),
                readSelectors(buf), readSelectors(buf));
    }

    private static void writeNullableInt(FriendlyByteBuf buf, Integer value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeVarInt(value);
    }

    private static Integer readNullableInt(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readVarInt() : null;
    }

    private static void writeNullableDouble(FriendlyByteBuf buf, Double value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeDouble(value);
    }

    private static Double readNullableDouble(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readDouble() : null;
    }

    private static void writeInts(FriendlyByteBuf buf, List<Integer> values) {
        buf.writeCollection(values, FriendlyByteBuf::writeVarInt);
    }

    private static List<Integer> readInts(FriendlyByteBuf buf) {
        return buf.readList(FriendlyByteBuf::readVarInt);
    }

    private static void writeResources(FriendlyByteBuf buf, List<ResourceLocation> values) {
        buf.writeCollection(values, FriendlyByteBuf::writeResourceLocation);
    }

    private static List<ResourceLocation> readResources(FriendlyByteBuf buf) {
        return buf.readList(FriendlyByteBuf::readResourceLocation);
    }

    private static void writeSelectors(FriendlyByteBuf buf, List<String> values) {
        buf.writeCollection(values, (target, value) -> target.writeUtf(value, 160));
    }

    private static List<String> readSelectors(FriendlyByteBuf buf) {
        return buf.readList(target -> target.readUtf(160));
    }

    private static List<ResourceLocation> immutableResources(List<ResourceLocation> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private static List<String> immutableSelectors(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static List<Integer> immutableMoonPhases(List<Integer> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && value >= 0 && value <= 7).distinct().sorted().toList();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Integer clampNullable(Integer value, int min, int max) {
        return value == null ? null : clamp(value, min, max);
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    private static Double nonNegative(Double value) {
        return value == null || !Double.isFinite(value) ? null : Math.max(0.0, value);
    }

    private static Integer positiveNullable(Integer value) {
        return value == null ? null : Math.max(1, value);
    }

    public enum PlacementMode {
        GROUND, AIR, WATER, LAVA;

        public PlacementMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public PlacementMode previous() {
            return values()[(ordinal() - 1 + values().length) % values().length];
        }
    }

    public enum SkyMode {
        ANY, MUST_SEE, MUST_NOT_SEE;

        public SkyMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public SkyMode previous() {
            return values()[(ordinal() - 1 + values().length) % values().length];
        }
    }
}
