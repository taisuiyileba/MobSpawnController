package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundSetNaturalSpawnPayload(ResourceLocation mobId, NaturalSpawnSettings settings)
        implements CustomPacketPayload {

    public static final Type<ServerboundSetNaturalSpawnPayload> TYPE =
            new Type<>(MobSpawnController.id("set_natural_spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSetNaturalSpawnPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundSetNaturalSpawnPayload::write, ServerboundSetNaturalSpawnPayload::read);

    private static ServerboundSetNaturalSpawnPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundSetNaturalSpawnPayload(buf.readResourceLocation(), NaturalSpawnSettings.read(buf));
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundSetNaturalSpawnPayload payload) {
        buf.writeResourceLocation(payload.mobId);
        payload.settings.write(buf);
    }

    public static void handle(ServerboundSetNaturalSpawnPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }
        MobSpawnManager.setNaturalSpawnSettings(payload.mobId, payload.settings);
        MobSpawnManager.save();
        NetworkBridge.sendToPlayer(player, new ClientboundSyncRulesPayload(
                MobSpawnManager.getAllRules(), MobSpawnManager.getAttributeOverrideMobs(),
                MobSpawnManager.getAllNaturalSpawnSettings()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
