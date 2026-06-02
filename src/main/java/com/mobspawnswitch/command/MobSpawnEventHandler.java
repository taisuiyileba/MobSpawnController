package com.mobspawnswitch.command;

import com.mobspawnswitch.Mobspawnswitch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Mobspawnswitch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobSpawnEventHandler {

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntityType());
        if (mobId == null) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        Boolean allowed = MobSpawnManager.getAllowed(mobId, type);
        if (allowed != null && !allowed) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (mobId == null) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        Boolean allowed = MobSpawnManager.getAllowed(mobId, type);
        if (allowed != null && !allowed) {
            event.setSpawnCancelled(true);
        }
    }
}
