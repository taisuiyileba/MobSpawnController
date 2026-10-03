package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Loadouts can hold large NBT strings, so they are fetched per mob instead of with the full rules sync. */
public record ServerboundRequestLoadoutPayload(ResourceLocation mobId) implements CustomPacketPayload {

    public static final Type<ServerboundRequestLoadoutPayload> TYPE =
            new Type<>(MobSpawnController.id("request_loadout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRequestLoadoutPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundRequestLoadoutPayload::write, ServerboundRequestLoadoutPayload::read);

    private static ServerboundRequestLoadoutPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundRequestLoadoutPayload(buf.readResourceLocation());
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundRequestLoadoutPayload payload) {
        buf.writeResourceLocation(payload.mobId);
    }

    public static void handle(ServerboundRequestLoadoutPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        NetworkBridge.sendToPlayer(player, new ClientboundSyncLoadoutPayload(payload.mobId,
                MobSpawnManager.getLoadout(payload.mobId)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
