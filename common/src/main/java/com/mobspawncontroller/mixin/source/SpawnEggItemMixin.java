package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Using a spawn egg on an adult spawns a baby without finalizeSpawn. */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {

    @ModifyArg(method = "spawnOffspringFromSpawnEgg", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private Entity mobspawncontroller$tagOffspring(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.SPAWN_EGG);
    }
}
