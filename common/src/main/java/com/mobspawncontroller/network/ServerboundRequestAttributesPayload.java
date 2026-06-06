package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.attribute.MobAttributeControl;
import com.mobspawncontroller.attribute.MobAttributeDiscovery;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record ServerboundRequestAttributesPayload(ResourceLocation mobId) {

    public static final ResourceLocation ID = MobSpawnController.id("request_attributes");

    public static ServerboundRequestAttributesPayload read(FriendlyByteBuf buf) {
        return new ServerboundRequestAttributesPayload(buf.readResourceLocation());
    }

    public static void write(ServerboundRequestAttributesPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.mobId);
    }

    public static void handle(ServerboundRequestAttributesPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        List<MobAttributeControl> controls = MobAttributeDiscovery.discover(player.serverLevel(), payload.mobId);
        NetworkBridge.sendToPlayer(player, new ClientboundSyncAttributesPayload(payload.mobId, controls));
    }
}
