package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.loadout.MobLoadout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundSyncLoadoutPayload(ResourceLocation mobId, MobLoadout loadout)
        implements CustomPacketPayload {

    public static final Type<ClientboundSyncLoadoutPayload> TYPE =
            new Type<>(MobSpawnController.id("sync_loadout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSyncLoadoutPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundSyncLoadoutPayload::write, ClientboundSyncLoadoutPayload::read);

    private static ClientboundSyncLoadoutPayload read(RegistryFriendlyByteBuf buf) {
        return new ClientboundSyncLoadoutPayload(buf.readResourceLocation(), MobLoadout.read(buf));
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundSyncLoadoutPayload payload) {
        buf.writeResourceLocation(payload.mobId);
        payload.loadout.write(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
