package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla spawners skip finalizeSpawn when the spawn data carries custom NBT; NeoForge fires its event anyway. */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {

    @ModifyArg(method = "serverTick", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagSpawner(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.SPAWNER);
    }
}
