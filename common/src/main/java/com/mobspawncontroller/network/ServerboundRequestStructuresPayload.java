package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record ServerboundRequestStructuresPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestStructuresPayload> TYPE =
            new Type<>(MobSpawnController.id("request_structures"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRequestStructuresPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
            }, buf -> new ServerboundRequestStructuresPayload());

    public static void handle(ServerboundRequestStructuresPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }
        List<String> entries = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        player.serverLevel().registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
            registry.keySet().forEach(id -> entries.add(id.toString()));
            registry.getTagNames().forEach(tag -> tags.add(tag.location().toString()));
        });
        entries.sort(String::compareTo);
        tags.sort(String::compareTo);
        NetworkBridge.sendToPlayer(player, new ClientboundSyncStructuresPayload(entries, tags));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
