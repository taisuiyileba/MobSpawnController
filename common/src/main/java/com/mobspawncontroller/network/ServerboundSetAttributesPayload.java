package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.attribute.MobAttributeControl;
import com.mobspawncontroller.attribute.MobAttributeDiscovery;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ServerboundSetAttributesPayload(ResourceLocation mobId, Map<ResourceLocation, Double> attributes)
        implements CustomPacketPayload {

    public static final Type<ServerboundSetAttributesPayload> TYPE =
            new Type<>(MobSpawnController.id("set_attributes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSetAttributesPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundSetAttributesPayload::write, ServerboundSetAttributesPayload::read);

    private static ServerboundSetAttributesPayload read(RegistryFriendlyByteBuf buf) {
        ResourceLocation mobId = buf.readResourceLocation();
        int size = buf.readVarInt();
        Map<ResourceLocation, Double> attributes = new HashMap<>();
        for (int i = 0; i < size; i++) {
            attributes.put(buf.readResourceLocation(), buf.readDouble());
        }
        return new ServerboundSetAttributesPayload(mobId, attributes);
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundSetAttributesPayload payload) {
        buf.writeResourceLocation(payload.mobId);
        buf.writeVarInt(payload.attributes.size());
        payload.attributes.forEach((attributeId, value) -> {
            buf.writeResourceLocation(attributeId);
            buf.writeDouble(value);
        });
    }

    public static void handle(ServerboundSetAttributesPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        MobSpawnManager.setAttributeOverrides(payload.mobId, payload.attributes);
        MobSpawnManager.save();
        List<MobAttributeControl> controls = MobAttributeDiscovery.discover(player.serverLevel(), payload.mobId);
        NetworkBridge.sendToPlayer(player, new ClientboundSyncAttributesPayload(payload.mobId, controls));
        NetworkBridge.sendToPlayer(player, new ClientboundSyncRulesPayload(MobSpawnManager.getAllRules(),
                MobSpawnManager.getAttributeOverrideMobs(), MobSpawnManager.getAllNaturalSpawnSettings(),
                MobSpawnManager.getAllActiveSpawnSettings()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
