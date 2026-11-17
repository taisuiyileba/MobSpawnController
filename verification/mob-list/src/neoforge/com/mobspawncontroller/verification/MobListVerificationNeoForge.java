package com.mobspawncontroller.verification;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import com.mobspawncontroller.MobSpawnController;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "mobspawncontroller")
public final class MobListVerificationNeoForge {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        MobListVerification.run(event.getServer());
    }

    @EventBusSubscriber(modid = "mobspawncontroller", bus = EventBusSubscriber.Bus.MOD)
    public static final class RegistryEvents {
        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.ENTITY_TYPE, helper -> MobListVerification.registerTypes(
                    (name, type) -> helper.register(MobSpawnController.id(name), type)));
        }
    }
}
