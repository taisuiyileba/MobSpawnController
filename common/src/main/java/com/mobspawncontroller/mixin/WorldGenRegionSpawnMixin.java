package com.mobspawncontroller.mixin;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.loadout.LoadoutApplier;
import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure and chunk-generation mobs are saved into the proto-chunk here and never reach ServerLevel#addEntity.
 * The proto-chunk serializes the entity right away, so the spawn check, accept hooks and loadout all run first.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionSpawnMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void mobspawncontroller$checkAndApplyLoadout(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!SpawnInterception.checkOnJoin(entity, (WorldGenRegion) (Object) this)) {
            callback.setReturnValue(false);
            return;
        }
        SpawnInterception.acceptOnJoin(entity, true);
        MobSpawnManager.applyPendingAttributeOverrides(entity);
        LoadoutApplier.applyPending(entity);
    }
}
