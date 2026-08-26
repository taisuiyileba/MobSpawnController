package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.active.ActiveSpawnSettings;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundSetActiveSpawnPayload(ResourceLocation mobId, ActiveSpawnSettings settings)
        implements CustomPacketPayload {

    public static final Type<ServerboundSetActiveSpawnPayload> TYPE =
            new Type<>(MobSpawnController.id("set_extra_spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSetActiveSpawnPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundSetActiveSpawnPayload::write, ServerboundSetActiveSpawnPayload::read);

    private static ServerboundSetActiveSpawnPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundSetActiveSpawnPayload(buf.readResourceLocation(), ActiveSpawnSettings.read(buf));
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundSetActiveSpawnPayload payload) {
        buf.writeResourceLocation(payload.mobId);
        payload.settings.write(buf);
    }

    public static void handle(ServerboundSetActiveSpawnPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }
        MobSpawnManager.setActiveSpawnSettings(payload.mobId, payload.settings);
        MobSpawnManager.save();
        NetworkBridge.sendToPlayer(player, new ClientboundSyncRulesPayload(
                MobSpawnManager.getAllRules(), MobSpawnManager.getAttributeOverrideMobs(),
                MobSpawnManager.getAllNaturalSpawnSettings(), MobSpawnManager.getAllActiveSpawnSettings()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
