package com.mobspawncontroller.mixin;

import com.mobspawncontroller.loadout.LoadoutApplier;
import com.mobspawncontroller.loadout.LoadoutPreset;
import com.mobspawncontroller.loadout.PendingLoadoutHolder;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobLoadoutMixin implements PendingLoadoutHolder {

    @Unique
    private LoadoutPreset mobspawncontroller$pendingLoadout;

    @Override
    public LoadoutPreset mobspawncontroller$getPendingLoadout() {
        return mobspawncontroller$pendingLoadout;
    }

    @Override
    public void mobspawncontroller$setPendingLoadout(LoadoutPreset preset) {
        mobspawncontroller$pendingLoadout = preset;
    }

    /** Conversions call finalizeSpawn after the mob already joined the level; apply on its next tick. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void mobspawncontroller$applyLateLoadout(CallbackInfo callback) {
        if (mobspawncontroller$pendingLoadout != null && !((Mob) (Object) this).level().isClientSide) {
            LoadoutApplier.applyPending((Mob) (Object) this);
        }
    }
}
