package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Animal offspring are created with EntityType#create and never finalized; only villagers pass BREEDING to finalizeSpawn. */
@Mixin(Animal.class)
public abstract class AnimalBreedingMixin {

    @ModifyArg(method = "spawnChildFromBreeding", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private Entity mobspawncontroller$tagBreeding(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.BREEDING);
    }
}
