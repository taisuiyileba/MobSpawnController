package com.mobspawncontroller.mixin;

import com.mobspawncontroller.natural.NaturalSpawnBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerBoostMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"))
    private static void mobspawncontroller$beginSpawnForChunk(ServerLevel level, LevelChunk chunk,
                                                               NaturalSpawner.SpawnState spawnState,
                                                               boolean spawnFriendlies, boolean spawnEnemies,
                                                               boolean spawnPersistent, CallbackInfo callback) {
        NaturalSpawnBoost.beginSpawnForChunk(level);
    }

    @Inject(method = "spawnForChunk", at = @At("RETURN"))
    private static void mobspawncontroller$endSpawnForChunk(ServerLevel level, LevelChunk chunk,
                                                             NaturalSpawner.SpawnState spawnState,
                                                             boolean spawnFriendlies, boolean spawnEnemies,
                                                             boolean spawnPersistent, CallbackInfo callback) {
        NaturalSpawnBoost.endSpawnForChunk();
    }

    @Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
    private static void mobspawncontroller$scaleSpawnWeights(ServerLevel level,
                                                              StructureManager structureManager,
                                                              ChunkGenerator chunkGenerator,
                                                              MobCategory category, BlockPos pos,
                                                              Holder<Biome> biome,
                                                              CallbackInfoReturnable<WeightedRandomList<
                                                                      MobSpawnSettings.SpawnerData>> callback) {
        callback.setReturnValue(NaturalSpawnBoost.scaleSpawnWeights(level, callback.getReturnValue()));
    }
}
