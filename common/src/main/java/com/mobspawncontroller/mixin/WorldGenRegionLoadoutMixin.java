package com.mobspawncontroller.mixin;

import com.mobspawncontroller.loadout.LoadoutApplier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Structure and chunk-generation mobs are saved into the proto-chunk here and never reach ServerLevel#addEntity. */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionLoadoutMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void mobspawncontroller$applyLoadout(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        LoadoutApplier.applyPending(entity);
    }
}
