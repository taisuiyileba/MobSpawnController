package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSyncStructuresPayload(List<String> entries, List<String> tags) {

    public static final ResourceLocation ID = MobSpawnController.id("sync_structures");

    public static ClientboundSyncStructuresPayload read(FriendlyByteBuf buf) {
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

    public static void write(ClientboundSyncStructuresPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.entries.size());
        for (String entry : payload.entries) {
            buf.writeUtf(entry, 160);
        }
        buf.writeVarInt(payload.tags.size());
        for (String tag : payload.tags) {
            buf.writeUtf(tag, 160);
        }
    }
}
