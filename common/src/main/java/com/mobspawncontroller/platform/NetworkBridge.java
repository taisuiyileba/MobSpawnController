package com.mobspawncontroller.platform;

import net.minecraft.server.level.ServerPlayer;

public final class NetworkBridge {

    private static ToServerSender toServerSender = payload -> {
    };
    private static ToPlayerSender toPlayerSender = (player, payload) -> {
    };

    private NetworkBridge() {
    }

    public static void setToServerSender(ToServerSender sender) {
        NetworkBridge.toServerSender = sender;
    }

    public static void setToPlayerSender(ToPlayerSender sender) {
        NetworkBridge.toPlayerSender = sender;
    }

    public static void sendToServer(Object payload) {
        toServerSender.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        toPlayerSender.sendToPlayer(player, payload);
    }

    public interface ToServerSender {
        void sendToServer(Object payload);
    }

    public interface ToPlayerSender {
        void sendToPlayer(ServerPlayer player, Object payload);
    }
}
