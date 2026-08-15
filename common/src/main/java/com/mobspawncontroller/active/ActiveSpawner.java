package com.mobspawncontroller.active;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.compat.SereneSeasonsCompat;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/** Executes configured active-spawn rules once per second for a server level. */
public final class ActiveSpawner {

    private ActiveSpawner() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20L != 0L || level.players().isEmpty()) {
            return;
        }
        List<? extends Player> players = level.players().stream().filter(player -> !player.isSpectator()).toList();
        if (players.isEmpty()) return;

        for (Map.Entry<ResourceLocation, ActiveSpawnSettings> entry
                : MobSpawnManager.getAllActiveSpawnSettings().entrySet()) {
            ActiveSpawnSettings settings = entry.getValue();
            if (!settings.enabled() || !matchesLevel(level, settings)
                    || level.random.nextDouble() >= settings.chancePerSecond()) {
                continue;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entry.getKey());
            if (entityType == null || reachedWorldCap(level, entityType, settings.maxWorldCount())) {
                continue;
            }
            spawnRule(level, players, entityType, settings);
        }
    }

    private static void spawnRule(ServerLevel level, List<? extends Player> players, EntityType<?> entityType,
                                  ActiveSpawnSettings settings) {
        int amountRange = settings.maxAmount() - settings.minAmount() + 1;
        int desired = settings.minAmount() + (amountRange > 1 ? level.random.nextInt(amountRange) : 0);
        int spawned = 0;
        for (int attempt = 0; attempt < settings.attempts() && spawned < desired; attempt++) {
            if (reachedWorldCap(level, entityType, settings.maxWorldCount())) break;

            Player player = players.get(level.random.nextInt(players.size()));
            BlockPos pos = findPosition(level, player.blockPosition(), settings);
            if (pos == null || !matchesPlayerDistance(pos, player.blockPosition(), settings)
                    || !matchesPosition(level, pos, settings)
                    || reachedNearbyCap(level, entityType, pos, settings)) {
                continue;
            }

            Entity created = entityType.create(level);
            if (!(created instanceof Mob mob)) continue;
            if (mob instanceof Enemy && level.getDifficulty() == Difficulty.PEACEFUL) {
                mob.discard();
                continue;
            }

            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(mob) || !mob.checkSpawnObstruction(level)) {
                mob.discard();
                continue;
            }
            if (settings.obeySpawnRules()
                    && (!NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(entityType),
                    level, pos, entityType)
                    || !SpawnPlacements.checkSpawnRules(entityType, level, MobSpawnType.NATURAL, pos, level.random)
                    || !mob.checkSpawnRules(level, MobSpawnType.NATURAL))) {
                mob.discard();
                continue;
            }
            if (!SpawnInterception.finalizeControlledSpawn(mob, level, MobSpawnType.NATURAL)) {
                continue;
            }
            if (level.addFreshEntity(mob)) {
                spawned++;
            }
        }
    }

    private static boolean matchesLevel(ServerLevel level, ActiveSpawnSettings settings) {
        if (!settings.dimensions().isEmpty()
                && !settings.dimensions().contains(level.dimension().location())) {
            return false;
        }
        if (settings.excludedDimensions().contains(level.dimension().location())) return false;
        long day = level.getDayTime() / 24000L;
        if ((settings.minDay() != null && day < settings.minDay())
                || (settings.maxDay() != null && day > settings.maxDay())) {
            return false;
        }
        int playerCount = (int) level.players().stream().filter(player -> !player.isSpectator()).count();
        return (settings.minPlayers() == null || playerCount >= settings.minPlayers())
                && (settings.maxPlayers() == null || playerCount <= settings.maxPlayers())
                && matchesWeather(settings.weather(), level)
                && (settings.difficulty() == NaturalSpawnSettings.DifficultyMode.ANY
                || settings.difficulty().name().equals(level.getDifficulty().name()))
                && SereneSeasonsCompat.isCurrentSeasonAllowed(level, settings.seasons(), settings.excludedSeasons());
    }

    private static boolean matchesPosition(ServerLevel level, BlockPos pos, ActiveSpawnSettings settings) {
        long time = level.getDayTime() % 24000L;
        if (!matchesTimeRange(time, settings.minTime(), settings.maxTime())) return false;

        int light = level.getMaxLocalRawBrightness(pos);
        if ((settings.minTotalLight() != null && light < settings.minTotalLight())
                || (settings.maxTotalLight() != null && light > settings.maxTotalLight())) {
            return false;
        }
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        if ((settings.minSkyLight() != null && skyLight < settings.minSkyLight())
                || (settings.maxSkyLight() != null && skyLight > settings.maxSkyLight())) {
            return false;
        }
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        if ((settings.minBlockLight() != null && blockLight < settings.minBlockLight())
                || (settings.maxBlockLight() != null && blockLight > settings.maxBlockLight())) {
            return false;
        }
        int moonPhase = level.getMoonPhase();
        if ((!settings.moonPhases().isEmpty() && !settings.moonPhases().contains(moonPhase))
                || settings.excludedMoonPhases().contains(moonPhase)) {
            return false;
        }
        boolean seesSky = level.canSeeSky(pos);
        if ((settings.skyMode() == ActiveSpawnSettings.SkyMode.MUST_SEE && !seesSky)
                || (settings.skyMode() == ActiveSpawnSettings.SkyMode.MUST_NOT_SEE && seesSky)) {
            return false;
        }

        double spawnDistanceSquared = pos.distSqr(level.getSharedSpawnPos());
        if ((settings.minWorldSpawnDistance() != null
                && spawnDistanceSquared < settings.minWorldSpawnDistance() * settings.minWorldSpawnDistance())
                || (settings.maxWorldSpawnDistance() != null
                && spawnDistanceSquared > settings.maxWorldSpawnDistance() * settings.maxWorldSpawnDistance())) {
            return false;
        }
        float localDifficulty = level.getCurrentDifficultyAt(pos).getEffectiveDifficulty();
        if ((settings.minLocalDifficulty() != null && localDifficulty < settings.minLocalDifficulty())
                || (settings.maxLocalDifficulty() != null && localDifficulty > settings.maxLocalDifficulty())
                || !matchesSlimeChunk(settings.slimeChunkMode(), level, pos)) {
            return false;
        }

        Holder<Biome> biome = level.getBiome(pos);
        ResourceLocation biomeId = biome.unwrapKey().map(ResourceKey::location).orElse(null);
        boolean hasBiomeFilter = !settings.biomes().isEmpty() || !settings.biomeTags().isEmpty();
        boolean matchesBiome = settings.biomes().contains(biomeId)
                || settings.biomeTags().stream().anyMatch(id -> biome.is(TagKey.create(Registries.BIOME, id)));
        if ((hasBiomeFilter && !matchesBiome) || settings.excludedBiomes().contains(biomeId)
                || settings.excludedBiomeTags().stream()
                .anyMatch(id -> biome.is(TagKey.create(Registries.BIOME, id)))) {
            return false;
        }
        if ((!settings.structures().isEmpty() && !matchesAnyStructure(level, pos, settings.structures()))
                || matchesAnyStructure(level, pos, settings.excludedStructures())) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return (settings.blocksBelow().isEmpty() || matchesAnyBlock(below, settings.blocksBelow()))
                && !matchesAnyBlock(below, settings.excludedBlocksBelow())
                && (settings.blocksAt().isEmpty() || matchesAnyBlock(at, settings.blocksAt()))
                && !matchesAnyBlock(at, settings.excludedBlocksAt())
                && (settings.blocksAbove().isEmpty() || matchesAnyBlock(above, settings.blocksAbove()))
                && !matchesAnyBlock(above, settings.excludedBlocksAbove());
    }

    private static BlockPos findPosition(ServerLevel level, BlockPos center, ActiveSpawnSettings settings) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double minSq = (double) settings.minDistance() * settings.minDistance();
        double maxSq = (double) settings.maxDistance() * settings.maxDistance();
        double distance = Math.sqrt(minSq + level.random.nextDouble() * Math.max(0.0, maxSq - minSq));
        int x = Mth.floor(center.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(center.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunk(x >> 4, z >> 4)) return null;

        int minY = Math.max(level.getMinBuildHeight() + 1,
                settings.minHeight() == null ? level.getMinBuildHeight() + 1 : settings.minHeight());
        int maxY = Math.min(level.getMaxBuildHeight() - 2,
                settings.maxHeight() == null ? level.getMaxBuildHeight() - 2 : settings.maxHeight());
        if (minY > maxY) return null;

        if (settings.placement() == ActiveSpawnSettings.PlacementMode.GROUND) {
            return findGroundPosition(level, x, z, minY, maxY);
        }

        for (int i = 0; i < 16; i++) {
            BlockPos candidate = new BlockPos(x, minY + level.random.nextInt(maxY - minY + 1), z);
            if (matchesPlacement(level, candidate, settings.placement())) return candidate;
        }
        return null;
    }

    /**
     * Starts below the column surface at a random height and scans downward for a
     * usable floor. This allows cave floors to be selected instead of repeatedly
     * returning only the surface position in the column.
     */
    private static BlockPos findGroundPosition(ServerLevel level, int x, int z, int minY, int maxY) {
        int surfaceY = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), maxY);
        if (surfaceY < minY) return null;

        int startY = minY + level.random.nextInt(surfaceY - minY + 1);
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos(x, startY, z);
        while (candidate.getY() >= minY) {
            if (isGroundCandidate(level, candidate)) return candidate.immutable();
            candidate.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean isGroundCandidate(ServerLevel level, BlockPos pos) {
        return isOpenSpace(level, pos)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean isOpenSpace(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean matchesPlacement(ServerLevel level, BlockPos pos,
                                            ActiveSpawnSettings.PlacementMode placement) {
        return switch (placement) {
            case GROUND -> isGroundCandidate(level, pos);
            case AIR -> isOpenSpace(level, pos);
            case WATER -> level.getFluidState(pos).is(FluidTags.WATER);
            case LAVA -> level.getFluidState(pos).is(FluidTags.LAVA);
        };
    }

    private static boolean reachedWorldCap(ServerLevel level, EntityType<?> type, Integer cap) {
        if (cap == null) return false;
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getType() == type && ++count >= cap) return true;
        }
        return false;
    }

    private static boolean reachedNearbyCap(ServerLevel level, EntityType<?> type, BlockPos pos,
                                            ActiveSpawnSettings settings) {
        if (settings.maxNearbyCount() == null) return false;
        AABB box = new AABB(pos).inflate(settings.nearbyRadius());
        return level.getEntitiesOfClass(Mob.class, box, mob -> mob.getType() == type).size()
                >= settings.maxNearbyCount();
    }

    private static boolean matchesTimeRange(long time, Integer minTime, Integer maxTime) {
        if (minTime == null && maxTime == null) return true;
        if (minTime == null) return time <= maxTime;
        if (maxTime == null) return time >= minTime;
        return minTime <= maxTime ? time >= minTime && time <= maxTime : time >= minTime || time <= maxTime;
    }

    private static boolean matchesPlayerDistance(BlockPos pos, BlockPos playerPos, ActiveSpawnSettings settings) {
        double distanceSquared = pos.distSqr(playerPos);
        return distanceSquared >= (double) settings.minDistance() * settings.minDistance()
                && distanceSquared <= (double) settings.maxDistance() * settings.maxDistance();
    }

    private static boolean matchesWeather(NaturalSpawnSettings.WeatherMode mode, ServerLevel level) {
        return switch (mode) {
            case ANY -> true;
            case CLEAR -> !level.isRaining();
            case RAIN -> level.isRaining() && !level.isThundering();
            case THUNDER -> level.isThundering();
        };
    }

    private static boolean matchesSlimeChunk(NaturalSpawnSettings.SlimeChunkMode mode,
                                             ServerLevel level, BlockPos pos) {
        if (mode == NaturalSpawnSettings.SlimeChunkMode.ANY) return true;
        boolean slimeChunk = WorldgenRandom.seedSlimeChunk(pos.getX() >> 4, pos.getZ() >> 4,
                level.getSeed(), 987234911L).nextInt(10) == 0;
        return mode == NaturalSpawnSettings.SlimeChunkMode.MUST ? slimeChunk : !slimeChunk;
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

    private static boolean matchesAnyStructure(ServerLevel level, BlockPos pos, List<String> selectors) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            if (!level.structureManager().getStructureAt(pos, structure).isValid()) continue;
            ResourceLocation structureId = registry.getKey(structure);
            ResourceKey<Structure> key = registry.getResourceKey(structure).orElse(null);
            for (String selector : selectors) {
                if (selector.equals("*")) return true;
                boolean tag = selector.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
                if (id == null) continue;
                if (!tag && id.equals(structureId)) return true;
                if (tag && key != null && registry.getHolder(key)
                        .map(holder -> holder.is(TagKey.create(Registries.STRUCTURE, id))).orElse(false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
