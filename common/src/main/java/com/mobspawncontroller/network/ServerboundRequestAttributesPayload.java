package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.attribute.MobAttributeControl;
import com.mobspawncontroller.attribute.MobAttributeDiscovery;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record ServerboundRequestAttributesPayload(ResourceLocation mobId) implements CustomPacketPayload {

    public static final Type<ServerboundRequestAttributesPayload> TYPE =
            new Type<>(MobSpawnController.id("request_attributes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRequestAttributesPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundRequestAttributesPayload::write, ServerboundRequestAttributesPayload::read);

    private static ServerboundRequestAttributesPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundRequestAttributesPayload(buf.readResourceLocation());
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundRequestAttributesPayload payload) {
        buf.writeResourceLocation(payload.mobId);
    }

    public static void handle(ServerboundRequestAttributesPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        List<MobAttributeControl> controls = MobAttributeDiscovery.discover(player.serverLevel(), payload.mobId);
        NetworkBridge.sendToPlayer(player, new ClientboundSyncAttributesPayload(payload.mobId, controls));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
