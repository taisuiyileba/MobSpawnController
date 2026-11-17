package com.mobspawncontroller.verification;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.mobspawncontroller.MobSpawnController;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MobListVerificationFabric implements ModInitializer {
    public void onInitialize() {
        MobListVerification.registerTypes((name, type) ->
                Registry.register(BuiltInRegistries.ENTITY_TYPE, MobSpawnController.id(name), type));
        ServerLifecycleEvents.SERVER_STARTED.register(MobListVerification::run);
    }
}
