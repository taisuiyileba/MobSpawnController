package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Shulker duplication is counted as breeding. */
@Mixin(Shulker.class)
public abstract class ShulkerDuplicationMixin {

    @ModifyArg(method = "hitByShulkerBullet", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobspawncontroller$tagDuplicate(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.BREEDING);
    }
}
