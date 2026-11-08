package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A pig struck by lightning becomes a zombified piglin without convertTo or finalizeSpawn. */
@Mixin(Pig.class)
public abstract class PigConversionMixin {

    @ModifyArg(method = "thunderHit", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagConversion(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.CONVERSION);
    }
}
