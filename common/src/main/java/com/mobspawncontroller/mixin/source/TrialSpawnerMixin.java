package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla trial spawners skip finalizeSpawn when the spawn data carries custom NBT; NeoForge fires its event anyway. */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {

    @ModifyArg(method = "spawnMob", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagSpawner(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.TRIAL_SPAWNER);
    }
}
