package com.mobspawncontroller.mixin;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobFinalizeSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void mobspawncontroller$cancelDisabledSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                         MobSpawnType spawnType, SpawnGroupData spawnGroupData,
                                                         CompoundTag spawnTag,
                                                         CallbackInfoReturnable<SpawnGroupData> callback) {
        Mob mob = (Mob) (Object) this;
        if (SpawnInterception.isHandledBeforeMobFinalizeSpawn(mob)) {
            return;
        }
        if (!MobSpawnManager.isSpawnAllowed(mob, level, spawnType)) {
            mob.discard();
            callback.setReturnValue(spawnGroupData);
            return;
        }
        MobSpawnManager.applyAttributeOverrides(mob);
    }
}
