package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.TurtleEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hatching turtles are counted as breeding, like the eggs they come from. */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggBlockMixin {

    @ModifyArg(method = "randomTick", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagHatchling(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.BREEDING);
    }
}
