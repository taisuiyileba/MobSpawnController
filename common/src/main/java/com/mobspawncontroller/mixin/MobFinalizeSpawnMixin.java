package com.mobspawncontroller.mixin;

import com.mobspawncontroller.natural.SpawnCheckHolder;
import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Judges every finalizeSpawn call that no earlier hook claimed. On Forge the loader event covers the
 * vanilla call sites; this catches mods that call {@code finalizeSpawn} directly instead of the event hook.
 */
@Mixin(Mob.class)
public abstract class MobFinalizeSpawnMixin implements SpawnCheckHolder {

    @Unique
    private boolean mobspawncontroller$spawnChecked;
    @Unique
    private boolean mobspawncontroller$joined;
    @Unique
    private boolean mobspawncontroller$unknownConditionsChecked;
    @Unique
    private MobSpawnType mobspawncontroller$spawnSource;

    @Override
    public boolean mobspawncontroller$isSpawnChecked() {
        return mobspawncontroller$spawnChecked;
    }

    @Override
    public void mobspawncontroller$setSpawnChecked(boolean checked) {
        mobspawncontroller$spawnChecked = checked;
    }

    @Override
    public boolean mobspawncontroller$hasJoined() {
        return mobspawncontroller$joined;
    }

    @Override
    public void mobspawncontroller$markJoined() {
        mobspawncontroller$joined = true;
    }

    @Override
    public boolean mobspawncontroller$areUnknownConditionsChecked() {
        return mobspawncontroller$unknownConditionsChecked;
    }

    @Override
    public void mobspawncontroller$setUnknownConditionsChecked(boolean checked) {
        mobspawncontroller$unknownConditionsChecked = checked;
    }

    @Override
    public MobSpawnType mobspawncontroller$getSpawnSource() {
        return mobspawncontroller$spawnSource;
    }

    @Override
    public void mobspawncontroller$setSpawnSource(MobSpawnType spawnType) {
        mobspawncontroller$spawnSource = spawnType;
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void mobspawncontroller$cancelDisabledSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                         MobSpawnType spawnType, SpawnGroupData spawnGroupData,
                                                         CompoundTag spawnTag,
                                                         CallbackInfoReturnable<SpawnGroupData> callback) {
        Mob mob = (Mob) (Object) this;
        if (mobspawncontroller$spawnChecked) {
            return;
        }
        if (!SpawnInterception.checkBeforeFinalize(mob, level, spawnType)) {
            mob.discard();
            callback.setReturnValue(spawnGroupData);
        }
    }
}
