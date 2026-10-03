package com.mobspawncontroller.mixin;

import com.mobspawncontroller.loadout.LoadoutApplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every fresh entity added to a running level passes through addEntity, after finalizeSpawn finished. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLoadoutMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void mobspawncontroller$applyLoadout(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        LoadoutApplier.applyPending(entity);
    }
}
