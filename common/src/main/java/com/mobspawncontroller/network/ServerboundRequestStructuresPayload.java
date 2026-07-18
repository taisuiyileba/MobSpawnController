package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record ServerboundRequestStructuresPayload() {

    public static final ResourceLocation ID = MobSpawnController.id("request_structures");

    public static ServerboundRequestStructuresPayload read(FriendlyByteBuf buf) {
        return new ServerboundRequestStructuresPayload();
    }

    public static void write(ServerboundRequestStructuresPayload payload, FriendlyByteBuf buf) {
    }

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
}
