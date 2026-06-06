package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record ClientboundSyncRulesPayload(Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules,
                                          Set<ResourceLocation> attributeModifiedMobs)
        implements CustomPacketPayload {

    public static final Type<ClientboundSyncRulesPayload> TYPE =
            new Type<>(MobSpawnController.id("sync_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSyncRulesPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundSyncRulesPayload::write, ClientboundSyncRulesPayload::read);

    private static ClientboundSyncRulesPayload read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation mobId = buf.readResourceLocation();
            int mapSize = buf.readVarInt();
            EnumMap<MobSpawnType, Boolean> map = new EnumMap<>(MobSpawnType.class);
            for (int j = 0; j < mapSize; j++) {
                int ordinal = buf.readVarInt();
                boolean allowed = buf.readBoolean();
                MobSpawnType[] values = MobSpawnType.values();
                if (ordinal >= 0 && ordinal < values.length) {
                    map.put(values[ordinal], allowed);
                }
            }
            rules.put(mobId, map);
        }
        int attributeSize = buf.readVarInt();
        Set<ResourceLocation> attributeModifiedMobs = new HashSet<>();
        for (int i = 0; i < attributeSize; i++) {
            attributeModifiedMobs.add(buf.readResourceLocation());
        }
        return new ClientboundSyncRulesPayload(rules, attributeModifiedMobs);
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundSyncRulesPayload payload) {
        buf.writeVarInt(payload.rules.size());
        payload.rules.forEach((mobId, map) -> {
            buf.writeResourceLocation(mobId);
            buf.writeVarInt(map.size());
            map.forEach((type, allowed) -> {
                buf.writeVarInt(type.ordinal());
                buf.writeBoolean(allowed);
            });
        });
        buf.writeVarInt(payload.attributeModifiedMobs.size());
        payload.attributeModifiedMobs.forEach(buf::writeResourceLocation);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
