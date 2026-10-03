package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundRequestRulesPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestRulesPayload> TYPE =
            new Type<>(MobSpawnController.id("request_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRequestRulesPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            }, buf -> new ServerboundRequestRulesPayload());

    public static void handle(ServerboundRequestRulesPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        NetworkBridge.sendToPlayer(player, ClientboundSyncRulesPayload.current());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
