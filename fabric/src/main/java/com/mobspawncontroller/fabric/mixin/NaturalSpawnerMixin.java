package com.mobspawncontroller.fabric.mixin;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;"
                    + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;finalizeSpawn("
                    + "Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;"
                    + "Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)"
                    + "Lnet/minecraft/world/entity/SpawnGroupData;")
    )
    private static SpawnGroupData mobspawncontroller$finalizeNaturalSpawn(
            Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
            SpawnGroupData spawnData) {
        return SpawnInterception.finalizeNaturalSpawn(mob, level, difficulty, spawnType, spawnData);
    }
}
