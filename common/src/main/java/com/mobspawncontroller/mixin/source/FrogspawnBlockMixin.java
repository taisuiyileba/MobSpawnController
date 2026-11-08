package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.FrogspawnBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Tadpoles hatching from frogspawn are counted as breeding, like the frogspawn they come from. */
@Mixin(FrogspawnBlock.class)
public abstract class FrogspawnBlockMixin {

    @ModifyArg(method = "spawnTadpoles", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagHatchling(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.BREEDING);
    }
}
