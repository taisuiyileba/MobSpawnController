package com.mobspawncontroller.natural;

import com.mobspawncontroller.command.MobSpawnManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weight;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Applies configured natural-spawn multipliers to vanilla selection weights and mob caps. */
public final class NaturalSpawnBoost {

    private static final ThreadLocal<ServerLevel> ACTIVE_LEVEL = new ThreadLocal<>();
    private static final Map<ResourceLocation, EnumMap<MobCategory, Double>> CAP_MULTIPLIERS =
            new ConcurrentHashMap<>();

    private NaturalSpawnBoost() {
    }

    public static void beginSpawnForChunk(ServerLevel level) {
        ACTIVE_LEVEL.set(level);
    }

    public static void endSpawnForChunk() {
        ACTIVE_LEVEL.remove();
    }

    /** Clears derived category caps after natural settings change or reload. */
    public static void invalidate() {
        CAP_MULTIPLIERS.clear();
    }

    public static WeightedRandomList<MobSpawnSettings.SpawnerData> scaleSpawnWeights(
            ServerLevel level, WeightedRandomList<MobSpawnSettings.SpawnerData> original) {
        List<MobSpawnSettings.SpawnerData> scaled = new ArrayList<>(original.unwrap().size());
        boolean changed = false;
        for (MobSpawnSettings.SpawnerData data : original.unwrap()) {
            double multiplier = multiplierFor(data.type, level);
            if (multiplier <= 1.0) {
                scaled.add(data);
                continue;
            }
            int originalWeight = data.getWeight().asInt();
            long multiplied = (long) Math.ceil(originalWeight * multiplier);
            int scaledWeight = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, multiplied));
            scaled.add(new MobSpawnSettings.SpawnerData(data.type, Weight.of(scaledWeight),
                    data.minCount, data.maxCount));
            changed = true;
        }
        return changed ? WeightedRandomList.create(scaled) : original;
    }

    /** Scales both vanilla's global category cap and its per-player local category cap. */
    public static int scaleCategoryCap(MobCategory category, int vanillaCap) {
        ServerLevel level = ACTIVE_LEVEL.get();
        if (level == null || vanillaCap <= 0) return vanillaCap;
        double multiplier = capMultipliers(level).getOrDefault(category, 1.0);
        long scaled = (long) Math.ceil(vanillaCap * multiplier);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(vanillaCap, scaled));
    }

    private static double multiplierFor(EntityType<?> type, ServerLevel level) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        NaturalSpawnSettings settings = MobSpawnManager.getAllNaturalSpawnSettings().get(mobId);
        return settings != null && appliesInLevel(settings, level) ? settings.spawnMultiplier() : 1.0;
    }

    private static EnumMap<MobCategory, Double> capMultipliers(ServerLevel level) {
        return CAP_MULTIPLIERS.computeIfAbsent(level.dimension().location(), ignored -> {
            EnumMap<MobCategory, Double> multipliers = new EnumMap<>(MobCategory.class);
            for (Map.Entry<ResourceLocation, NaturalSpawnSettings> entry
                    : MobSpawnManager.getAllNaturalSpawnSettings().entrySet()) {
                NaturalSpawnSettings settings = entry.getValue();
                if (settings.spawnMultiplier() <= 1.0 || !appliesInLevel(settings, level)) continue;
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.getKey()).orElse(null);
                if (type == null || type.getCategory() == MobCategory.MISC) continue;
                multipliers.merge(type.getCategory(), settings.spawnMultiplier(), Math::max);
            }
            return multipliers;
        });
    }

    private static boolean appliesInLevel(NaturalSpawnSettings settings, ServerLevel level) {
        if (!settings.appliesTo(MobSpawnType.NATURAL)) return false;
        ResourceLocation dimension = level.dimension().location();
        return (settings.dimensions().isEmpty() || settings.dimensions().contains(dimension))
                && !settings.excludedDimensions().contains(dimension);
    }
}
