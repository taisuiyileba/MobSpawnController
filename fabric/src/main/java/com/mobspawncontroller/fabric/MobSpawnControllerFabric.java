package com.mobspawncontroller.fabric;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnCommand;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class MobSpawnControllerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        MobSpawnController.init();
        registerPayloads();

        NetworkBridge.setToPlayerSender(MobSpawnControllerFabric::sendToPlayer);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MobSpawnCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(MobSpawnController::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MobSpawnController.serverStopping());
    }

    private static void registerPayloads() {
        ServerPlayNetworking.registerGlobalReceiver(ServerboundToggleSpawnPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    ServerboundToggleSpawnPayload payload = ServerboundToggleSpawnPayload.read(buf);
                    server.execute(() -> ServerboundToggleSpawnPayload.handle(payload, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestRulesPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    ServerboundRequestRulesPayload payload = ServerboundRequestRulesPayload.read(buf);
                    server.execute(() -> ServerboundRequestRulesPayload.handle(payload, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestAttributesPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    ServerboundRequestAttributesPayload payload = ServerboundRequestAttributesPayload.read(buf);
                    server.execute(() -> ServerboundRequestAttributesPayload.handle(payload, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundSetAttributesPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    ServerboundSetAttributesPayload payload = ServerboundSetAttributesPayload.read(buf);
                    server.execute(() -> ServerboundSetAttributesPayload.handle(payload, player));
                });
    }

    private static void sendToPlayer(ServerPlayer player, Object payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        if (payload instanceof ClientboundSyncRulesPayload rules) {
            if (!ServerPlayNetworking.canSend(player, ClientboundSyncRulesPayload.ID)) {
                return;
            }
            ClientboundSyncRulesPayload.write(rules, buf);
            ServerPlayNetworking.send(player, ClientboundSyncRulesPayload.ID, buf);
        } else if (payload instanceof ClientboundSyncAttributesPayload attributes) {
            if (!ServerPlayNetworking.canSend(player, ClientboundSyncAttributesPayload.ID)) {
                return;
            }
            ClientboundSyncAttributesPayload.write(attributes, buf);
            ServerPlayNetworking.send(player, ClientboundSyncAttributesPayload.ID, buf);
        }
    }
}
