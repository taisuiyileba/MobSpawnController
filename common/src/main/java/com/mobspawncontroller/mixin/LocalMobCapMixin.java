package com.mobspawncontroller.mixin;

import com.mobspawncontroller.natural.NaturalSpawnBoost;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public abstract class LocalMobCapMixin {

    @Redirect(method = "canSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int mobspawncontroller$scaleLocalCategoryCap(MobCategory category) {
        return NaturalSpawnBoost.scaleCategoryCap(category, category.getMaxInstancesPerChunk());
    }
}
