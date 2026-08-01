package com.mobspawncontroller.fabric;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnCommand;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ClientboundSyncStructuresPayload;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.network.ServerboundRequestStructuresPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundSetNaturalSpawnPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mobspawncontroller.platform.ModCompat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class MobSpawnControllerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ModCompat.setModLoadedChecker(FabricLoader.getInstance()::isModLoaded);
        MobSpawnController.setConfigDirectory(FabricLoader.getInstance().getConfigDir());
        MobSpawnController.init();
        registerPayloads();

        NetworkBridge.setSender(new NetworkBridge.PacketSender() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
            }

            @Override
            public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
                if (!ServerPlayNetworking.canSend(player, payload.type())) {
                    return;
                }
                ServerPlayNetworking.send(player, payload);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MobSpawnCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> MobSpawnController.serverStarting());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MobSpawnController.serverStopping());
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ServerboundToggleSpawnPayload.TYPE,
                ServerboundToggleSpawnPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundRequestRulesPayload.TYPE,
                ServerboundRequestRulesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundRequestAttributesPayload.TYPE,
                ServerboundRequestAttributesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundSetAttributesPayload.TYPE,
                ServerboundSetAttributesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundSetNaturalSpawnPayload.TYPE,
                ServerboundSetNaturalSpawnPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundRequestStructuresPayload.TYPE,
                ServerboundRequestStructuresPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundSyncRulesPayload.TYPE,
                ClientboundSyncRulesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundSyncAttributesPayload.TYPE,
                ClientboundSyncAttributesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundSyncStructuresPayload.TYPE,
                ClientboundSyncStructuresPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ServerboundToggleSpawnPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerboundToggleSpawnPayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestRulesPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerboundRequestRulesPayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestAttributesPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerboundRequestAttributesPayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSetAttributesPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerboundSetAttributesPayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSetNaturalSpawnPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerboundSetNaturalSpawnPayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestStructuresPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerboundRequestStructuresPayload.handle(payload, context.player())));
    }
}
