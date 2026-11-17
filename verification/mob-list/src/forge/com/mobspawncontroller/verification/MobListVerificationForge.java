package com.mobspawncontroller.verification;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mobspawncontroller.MobSpawnController;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = "mobspawncontroller")
public final class MobListVerificationForge {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        MobListVerification.run(event.getServer());
    }

    @Mod.EventBusSubscriber(modid = "mobspawncontroller", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class RegistryEvents {
        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.ENTITY_TYPE, helper -> MobListVerification.registerTypes(
                    (name, type) -> helper.register(MobSpawnController.id(name), type)));
        }
    }
}
