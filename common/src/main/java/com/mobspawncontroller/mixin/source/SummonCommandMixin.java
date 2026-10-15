package com.mobspawncontroller.mixin.source;

import com.mobspawncontroller.natural.SpawnInterception;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** /summon only calls finalizeSpawn when no NBT is given. */
@Mixin(SummonCommand.class)
public abstract class SummonCommandMixin {

    @ModifyArg(method = "createEntity", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private static Entity mobspawncontroller$tagSummon(Entity entity) {
        return SpawnInterception.tagSpawnSource(entity, MobSpawnType.COMMAND);
    }
}
