package com.mobspawncontroller.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkBridge {

    private static PacketSender sender = new NoopPacketSender();

    private NetworkBridge() {
    }

    public static void setSender(PacketSender sender) {
        NetworkBridge.sender = sender;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        sender.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        sender.sendToPlayer(player, payload);
    }

    public interface PacketSender {
        void sendToServer(CustomPacketPayload payload);

        void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
    }

    private static final class NoopPacketSender implements PacketSender {
        @Override
        public void sendToServer(CustomPacketPayload payload) {
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        }
    }
}
