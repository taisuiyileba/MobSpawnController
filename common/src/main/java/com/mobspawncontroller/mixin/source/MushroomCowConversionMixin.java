package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A sheared mooshroom is replaced by a cow; the item drops added by the same call are not mobs and stay untouched.
 * Vanilla adds the cow in shear, Forge moved that code into its own shearInternal.
 */
@Mixin(MushroomCow.class)
public abstract class MushroomCowConversionMixin {

    @ModifyArg(method = {"shear", "shearInternal"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagConversion(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.CONVERSION);
    }
}
