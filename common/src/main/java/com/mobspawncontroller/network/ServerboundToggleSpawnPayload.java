package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public record ServerboundToggleSpawnPayload(ResourceLocation mobId, String spawnType, boolean allowed)
        implements CustomPacketPayload {

    public static final Type<ServerboundToggleSpawnPayload> TYPE =
            new Type<>(MobSpawnController.id("toggle_spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundToggleSpawnPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundToggleSpawnPayload::write, ServerboundToggleSpawnPayload::read);

    private static ServerboundToggleSpawnPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundToggleSpawnPayload(buf.readResourceLocation(), buf.readUtf(64), buf.readBoolean());
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundToggleSpawnPayload payload) {
        buf.writeResourceLocation(payload.mobId);
        buf.writeUtf(payload.spawnType.toLowerCase(Locale.ROOT), 64);
        buf.writeBoolean(payload.allowed);
    }

    public static void handle(ServerboundToggleSpawnPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        if ("all".equalsIgnoreCase(payload.spawnType)) {
            MobSpawnManager.setAllAllowed(payload.mobId, payload.allowed);
        } else {
            MobSpawnType type = MobSpawnManager.parseSpawnType(payload.spawnType);
            if (type != null) {
                MobSpawnManager.setAllowed(payload.mobId, type, payload.allowed);
            }
        }
        MobSpawnManager.save();

        Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> allRules = MobSpawnManager.getAllRules();
        NetworkBridge.sendToPlayer(player, new ClientboundSyncRulesPayload(allRules,
                MobSpawnManager.getAttributeOverrideMobs()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
