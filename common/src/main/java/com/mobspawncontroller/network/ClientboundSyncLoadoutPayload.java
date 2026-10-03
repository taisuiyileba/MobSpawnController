package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.loadout.MobLoadout;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ClientboundSyncLoadoutPayload(ResourceLocation mobId, MobLoadout loadout) {

    public static final ResourceLocation ID = MobSpawnController.id("sync_loadout");

    public static ClientboundSyncLoadoutPayload read(FriendlyByteBuf buf) {
        return new ClientboundSyncLoadoutPayload(buf.readResourceLocation(), MobLoadout.read(buf));
    }

    public static void write(ClientboundSyncLoadoutPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.mobId);
        payload.loadout.write(buf);
    }
}
