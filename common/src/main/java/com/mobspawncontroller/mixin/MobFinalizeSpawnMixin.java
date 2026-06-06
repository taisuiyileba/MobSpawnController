package com.mobspawncontroller.mixin;

import com.mobspawncontroller.command.MobSpawnManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        Boolean allowed = MobSpawnManager.getAllowed(mobId, spawnType);
        if (allowed != null && !allowed) {
            mob.discard();
            callback.setReturnValue(spawnGroupData);
            return;
        }
        MobSpawnManager.applyAttributeOverrides(mob);
    }
}
