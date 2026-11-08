package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.ThrownEgg;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Chicks hatching from thrown eggs are counted as breeding. */
@Mixin(ThrownEgg.class)
public abstract class ThrownEggMixin {

    @ModifyArg(method = "onHit", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagHatchling(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.BREEDING);
    }
}
