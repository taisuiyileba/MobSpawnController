package com.mobspawncontroller.mixin;

import com.mobspawncontroller.natural.NaturalSpawnBoost;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.SpawnState.class)
public abstract class NaturalSpawnerCapMixin {

    @Redirect(method = "canSpawnForCategory", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int mobspawncontroller$scaleGlobalCategoryCap(MobCategory category) {
        return NaturalSpawnBoost.scaleCategoryCap(category, category.getMaxInstancesPerChunk());
    }
}
