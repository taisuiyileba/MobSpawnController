package com.mobspawncontroller.natural;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI-friendly natural spawn conditions inspired by InControl's spawn rules.
 * Null bounds and empty selector lists mean that the corresponding condition is unrestricted.
 */
public record NaturalSpawnSettings(
        double chance,
        double spawnMultiplier,
        Integer minHeight,
        Integer maxHeight,
        Integer minTotalLight,
        Integer maxTotalLight,
        Integer minTime,
        Integer maxTime,
        Integer minDay,
        Integer maxDay,
        List<Integer> moonPhases,
        List<Integer> excludedMoonPhases,
        Double minPlayerDistance,
        Double maxPlayerDistance,
        Double minWorldSpawnDistance,
        Double maxWorldSpawnDistance,
        Double minLocalDifficulty,
        Double maxLocalDifficulty,
        WeatherMode weather,
        DifficultyMode difficulty,
        SkyMode skyMode,
        FluidMode fluidMode,
        SlimeChunkMode slimeChunkMode,
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
        Integer minPlayers,
        Integer maxPlayers,
        Integer maxNearby,
        double nearbyRadius,
        Integer minSkyLight,
        Integer maxSkyLight,
        Integer minBlockLight,
        Integer maxBlockLight,
        List<String> blocksAt,
        List<String> excludedBlocksAt,
        List<String> blocksAbove,
        List<String> excludedBlocksAbove,
        List<MobSpawnType> spawnTypes,
        List<MobSpawnType> excludedSpawnTypes
) {

    public NaturalSpawnSettings {
        chance = clamp(chance, 0.0, 1.0);
        spawnMultiplier = Double.isFinite(spawnMultiplier) ? clamp(spawnMultiplier, 1.0, 16.0) : 1.0;
        minTotalLight = clampNullable(minTotalLight, 0, 15);
        maxTotalLight = clampNullable(maxTotalLight, 0, 15);
        minTime = clampNullable(minTime, 0, 23999);
        maxTime = clampNullable(maxTime, 0, 23999);
        minDay = nonNegative(minDay);
        maxDay = nonNegative(maxDay);
        moonPhases = immutableInts(moonPhases);
        excludedMoonPhases = immutableInts(excludedMoonPhases);
        minPlayerDistance = nonNegative(minPlayerDistance);
        maxPlayerDistance = nonNegative(maxPlayerDistance);
        minWorldSpawnDistance = nonNegative(minWorldSpawnDistance);
        maxWorldSpawnDistance = nonNegative(maxWorldSpawnDistance);
        minLocalDifficulty = nonNegative(minLocalDifficulty);
        maxLocalDifficulty = nonNegative(maxLocalDifficulty);
        weather = weather == null ? WeatherMode.ANY : weather;
        difficulty = difficulty == null ? DifficultyMode.ANY : difficulty;
        skyMode = skyMode == null ? SkyMode.ANY : skyMode;
        fluidMode = fluidMode == null ? FluidMode.ANY : fluidMode;
        slimeChunkMode = slimeChunkMode == null ? SlimeChunkMode.ANY : slimeChunkMode;
        dimensions = immutableResources(dimensions);
        excludedDimensions = immutableResources(excludedDimensions);
        biomes = immutableResources(biomes);
        excludedBiomes = immutableResources(excludedBiomes);
        biomeTags = immutableResources(biomeTags);
        excludedBiomeTags = immutableResources(excludedBiomeTags);
        seasons = immutableSeasons(seasons);
        excludedSeasons = immutableSeasons(excludedSeasons);
        structures = immutableSelectors(structures);
        excludedStructures = immutableSelectors(excludedStructures);
        blocksBelow = immutableSelectors(blocksBelow);
        excludedBlocksBelow = immutableSelectors(excludedBlocksBelow);
        minPlayers = nonNegative(minPlayers);
        maxPlayers = nonNegative(maxPlayers);
        maxNearby = nonNegative(maxNearby);
        nearbyRadius = maxNearby == null ? 16.0 : clamp(nearbyRadius, 1.0, 256.0);
        minSkyLight = clampNullable(minSkyLight, 0, 15);
        maxSkyLight = clampNullable(maxSkyLight, 0, 15);
        minBlockLight = clampNullable(minBlockLight, 0, 15);
        maxBlockLight = clampNullable(maxBlockLight, 0, 15);
        blocksAt = immutableSelectors(blocksAt);
        excludedBlocksAt = immutableSelectors(excludedBlocksAt);
        blocksAbove = immutableSelectors(blocksAbove);
        excludedBlocksAbove = immutableSelectors(excludedBlocksAbove);
        spawnTypes = immutableSpawnTypes(spawnTypes);
        excludedSpawnTypes = immutableSpawnTypes(excludedSpawnTypes);
    }

    public static NaturalSpawnSettings defaults() {
        return new NaturalSpawnSettings(1.0, 1.0,
                null, null, null, null,
                null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null,
                WeatherMode.ANY, DifficultyMode.ANY, SkyMode.ANY, FluidMode.ANY, SlimeChunkMode.ANY,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                null, null, null, 16.0,
                null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(MobSpawnType.NATURAL), List.of());
    }

    public boolean isDefault() {
        return equals(defaults());
    }

    /** Returns whether this detailed condition set applies to the supplied spawn reason. */
    public boolean appliesTo(MobSpawnType spawnType) {
        return spawnType != null
                && (spawnTypes.isEmpty() || spawnTypes.contains(spawnType))
                && !excludedSpawnTypes.contains(spawnType);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(chance);
        buf.writeDouble(spawnMultiplier);
        writeNullableInt(buf, minHeight);
        writeNullableInt(buf, maxHeight);
        writeNullableInt(buf, minTotalLight);
        writeNullableInt(buf, maxTotalLight);
        writeNullableInt(buf, minTime);
        writeNullableInt(buf, maxTime);
        writeNullableInt(buf, minDay);
        writeNullableInt(buf, maxDay);
        writeInts(buf, moonPhases);
        writeInts(buf, excludedMoonPhases);
        writeNullableDouble(buf, minPlayerDistance);
        writeNullableDouble(buf, maxPlayerDistance);
        writeNullableDouble(buf, minWorldSpawnDistance);
        writeNullableDouble(buf, maxWorldSpawnDistance);
        writeNullableDouble(buf, minLocalDifficulty);
        writeNullableDouble(buf, maxLocalDifficulty);
        buf.writeEnum(weather);
        buf.writeEnum(difficulty);
        buf.writeEnum(skyMode);
        buf.writeEnum(fluidMode);
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
        writeNullableInt(buf, minPlayers);
        writeNullableInt(buf, maxPlayers);
        writeNullableInt(buf, maxNearby);
        buf.writeDouble(nearbyRadius);
        writeNullableInt(buf, minSkyLight);
        writeNullableInt(buf, maxSkyLight);
        writeNullableInt(buf, minBlockLight);
        writeNullableInt(buf, maxBlockLight);
        writeSelectors(buf, blocksAt);
        writeSelectors(buf, excludedBlocksAt);
        writeSelectors(buf, blocksAbove);
        writeSelectors(buf, excludedBlocksAbove);
        writeSpawnTypes(buf, spawnTypes);
        writeSpawnTypes(buf, excludedSpawnTypes);
    }

    public static NaturalSpawnSettings read(FriendlyByteBuf buf) {
        return new NaturalSpawnSettings(buf.readDouble(), buf.readDouble(),
                readNullableInt(buf), readNullableInt(buf),
                readNullableInt(buf), readNullableInt(buf),
                readNullableInt(buf), readNullableInt(buf),
                readNullableInt(buf), readNullableInt(buf),
                readInts(buf), readInts(buf),
                readNullableDouble(buf), readNullableDouble(buf),
                readNullableDouble(buf), readNullableDouble(buf),
                readNullableDouble(buf), readNullableDouble(buf),
                buf.readEnum(WeatherMode.class), buf.readEnum(DifficultyMode.class),
                buf.readEnum(SkyMode.class), buf.readEnum(FluidMode.class),
                buf.readEnum(SlimeChunkMode.class),
                readResources(buf), readResources(buf), readResources(buf), readResources(buf),
                readResources(buf), readResources(buf),
                readSelectors(buf), readSelectors(buf),
                readSelectors(buf), readSelectors(buf), readSelectors(buf), readSelectors(buf),
                readNullableInt(buf), readNullableInt(buf), readNullableInt(buf), buf.readDouble(),
                readNullableInt(buf), readNullableInt(buf), readNullableInt(buf), readNullableInt(buf),
                readSelectors(buf), readSelectors(buf), readSelectors(buf), readSelectors(buf),
                readSpawnTypes(buf), readSpawnTypes(buf));
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

    private static void writeSpawnTypes(FriendlyByteBuf buf, List<MobSpawnType> values) {
        buf.writeCollection(values, (target, value) -> target.writeEnum(value));
    }

    private static List<MobSpawnType> readSpawnTypes(FriendlyByteBuf buf) {
        return buf.readList(target -> target.readEnum(MobSpawnType.class));
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

    private static List<ResourceLocation> immutableResources(List<ResourceLocation> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private static List<String> immutableSelectors(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    /** Expands the old four broad season values and validates /season set sub-season names. */
    private static List<String> immutableSeasons(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (List.of("spring", "summer", "autumn", "winter").contains(normalized)) {
                result.add("early_" + normalized);
                result.add("mid_" + normalized);
                result.add("late_" + normalized);
            } else if (isSubSeason(normalized)) {
                result.add(normalized);
            }
        }
        return result.stream().distinct().toList();
    }

    private static boolean isSubSeason(String value) {
        return List.of("early_spring", "mid_spring", "late_spring",
                "early_summer", "mid_summer", "late_summer",
                "early_autumn", "mid_autumn", "late_autumn",
                "early_winter", "mid_winter", "late_winter").contains(value);
    }

    private static List<Integer> immutableInts(List<Integer> values) {
        return values == null ? List.of()
                : values.stream().filter(value -> value != null && value >= 0 && value <= 7).distinct().sorted().toList();
    }

    private static List<MobSpawnType> immutableSpawnTypes(List<MobSpawnType> values) {
        return values == null ? List.of()
                : values.stream().filter(java.util.Objects::nonNull).distinct()
                .sorted(java.util.Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private static void writeInts(FriendlyByteBuf buf, List<Integer> values) {
        buf.writeCollection(values, FriendlyByteBuf::writeVarInt);
    }

    private static List<Integer> readInts(FriendlyByteBuf buf) {
        return buf.readList(FriendlyByteBuf::readVarInt);
    }

    private static Integer clampNullable(Integer value, int min, int max) {
        return value == null ? null : Math.max(min, Math.min(max, value));
    }

    private static Double clampNullable(Double value, double min, double max) {
        return value == null ? null : clamp(value, min, max);
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    private static Double nonNegative(Double value) {
        return value == null ? null : Math.max(0.0, value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum WeatherMode {
        ANY, CLEAR, RAIN, THUNDER;

        public WeatherMode next() {
            return NaturalSpawnSettings.next(this);
        }

        public WeatherMode previous() {
            return NaturalSpawnSettings.previous(this);
        }
    }

    public enum DifficultyMode {
        ANY, PEACEFUL, EASY, NORMAL, HARD;

        public DifficultyMode next() {
            return NaturalSpawnSettings.next(this);
        }

        public DifficultyMode previous() {
            return NaturalSpawnSettings.previous(this);
        }
    }

    public enum SkyMode {
        ANY, MUST_SEE, MUST_NOT_SEE;

        public SkyMode next() {
            return NaturalSpawnSettings.next(this);
        }

        public SkyMode previous() {
            return NaturalSpawnSettings.previous(this);
        }
    }

    public enum FluidMode {
        ANY, DRY, WATER, LAVA;

        public FluidMode next() {
            return NaturalSpawnSettings.next(this);
        }

        public FluidMode previous() {
            return NaturalSpawnSettings.previous(this);
        }
    }

    public enum SlimeChunkMode {
        ANY, MUST, MUST_NOT;

        public SlimeChunkMode next() {
            return NaturalSpawnSettings.next(this);
        }

        public SlimeChunkMode previous() {
            return NaturalSpawnSettings.previous(this);
        }
    }

    private static <T extends Enum<T>> T next(T value) {
        T[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static <T extends Enum<T>> T previous(T value) {
        T[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() - 1 + values.length) % values.length];
    }
}
