package com.mobspawncontroller.mixin;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.loadout.LoadoutApplier;
import com.mobspawncontroller.natural.SpawnCheckHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies pending spawn changes before joining or, for late initialization, before the entity ticks. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLoadoutMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void mobspawncontroller$applyLoadout(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof SpawnCheckHolder holder && !holder.mobspawncontroller$hasJoined()) {
            mobspawncontroller$applySpawnChanges(entity);
        }
    }

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void mobspawncontroller$applyBeforeTick(Entity entity, CallbackInfo callback) {
        mobspawncontroller$applySpawnChanges(entity);
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void mobspawncontroller$applyBeforePassengerTick(Entity vehicle, Entity passenger, CallbackInfo callback) {
        mobspawncontroller$applySpawnChanges(passenger);
    }

    @Unique
    private static void mobspawncontroller$applySpawnChanges(Entity entity) {
        MobSpawnManager.applyPendingAttributeOverrides(entity);
        LoadoutApplier.applyPending(entity);
    }
}
