package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Loadouts can hold large NBT strings, so they are fetched per mob instead of with the full rules sync. */
public record ServerboundRequestLoadoutPayload(ResourceLocation mobId) {

    public static final ResourceLocation ID = MobSpawnController.id("request_loadout");

    public static ServerboundRequestLoadoutPayload read(FriendlyByteBuf buf) {
        return new ServerboundRequestLoadoutPayload(buf.readResourceLocation());
    }

    public static void write(ServerboundRequestLoadoutPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.mobId);
    }

    public static void handle(ServerboundRequestLoadoutPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        NetworkBridge.sendToPlayer(player, new ClientboundSyncLoadoutPayload(payload.mobId,
                MobSpawnManager.getLoadout(payload.mobId)));
    }
}
