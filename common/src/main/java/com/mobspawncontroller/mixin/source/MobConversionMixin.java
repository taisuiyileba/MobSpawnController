package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Zombie to drowned, skeleton to stray, piglin and hoglin zombification all go through convertTo. */
@Mixin(Mob.class)
public abstract class MobConversionMixin {

    @ModifyArg(method = "convertTo", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagConversion(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.CONVERSION);
    }
}
