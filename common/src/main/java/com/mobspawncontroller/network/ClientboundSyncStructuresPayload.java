package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSyncStructuresPayload(List<String> entries, List<String> tags)
        implements CustomPacketPayload {

    public static final Type<ClientboundSyncStructuresPayload> TYPE =
            new Type<>(MobSpawnController.id("sync_structures"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSyncStructuresPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundSyncStructuresPayload::write, ClientboundSyncStructuresPayload::read);

    private static ClientboundSyncStructuresPayload read(RegistryFriendlyByteBuf buf) {
        int entryCount = buf.readVarInt();
        List<String> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(buf.readUtf(160));
        }
        int tagCount = buf.readVarInt();
        List<String> tags = new ArrayList<>(tagCount);
        for (int i = 0; i < tagCount; i++) {
            tags.add(buf.readUtf(160));
        }
        return new ClientboundSyncStructuresPayload(entries, tags);
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundSyncStructuresPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (String entry : payload.entries) {
            buf.writeUtf(entry, 160);
        }
        buf.writeVarInt(payload.tags.size());
        for (String tag : payload.tags) {
            buf.writeUtf(tag, 160);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
