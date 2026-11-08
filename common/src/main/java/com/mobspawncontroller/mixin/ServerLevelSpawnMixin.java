package com.mobspawncontroller.mixin;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fresh entities pass through addFreshEntity; teleports and entities loaded from disk do not. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSpawnMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void mobspawncontroller$checkUncheckedSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!SpawnInterception.checkOnJoin(entity, (ServerLevel) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void mobspawncontroller$acceptFreshSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        SpawnInterception.acceptOnJoin(entity, callback.getReturnValueZ());
    }
}
