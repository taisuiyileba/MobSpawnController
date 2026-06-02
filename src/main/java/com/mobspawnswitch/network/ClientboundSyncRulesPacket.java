package com.mobspawnswitch.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class ClientboundSyncRulesPacket {

    private final Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules;

    public ClientboundSyncRulesPacket(Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules) {
        this.rules = rules;
    }

    public ClientboundSyncRulesPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.rules = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation mobId = buf.readResourceLocation();
            int mapSize = buf.readVarInt();
            EnumMap<MobSpawnType, Boolean> map = new EnumMap<>(MobSpawnType.class);
            for (int j = 0; j < mapSize; j++) {
                MobSpawnType type = MobSpawnType.values()[buf.readVarInt()];
                boolean allowed = buf.readBoolean();
                map.put(type, allowed);
            }
            rules.put(mobId, map);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rules.size());
        rules.forEach((mobId, map) -> {
            buf.writeResourceLocation(mobId);
            buf.writeVarInt(map.size());
            map.forEach((type, allowed) -> {
                buf.writeVarInt(type.ordinal());
                buf.writeBoolean(allowed);
            });
        });
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RuleSyncReceiver receiver) {
            receiver.onRulesReceived(rules);
        }
    }

    public Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> getRules() {
        return rules;
    }

    public interface RuleSyncReceiver {
        void onRulesReceived(Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules);
    }
}
