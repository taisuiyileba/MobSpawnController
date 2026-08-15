package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public record ServerboundToggleSpawnPayload(ResourceLocation mobId, String spawnType, boolean allowed) {

    public static final ResourceLocation ID = MobSpawnController.id("toggle_spawn");

    public static ServerboundToggleSpawnPayload read(FriendlyByteBuf buf) {
        return new ServerboundToggleSpawnPayload(buf.readResourceLocation(), buf.readUtf(64), buf.readBoolean());
    }

    public static void write(ServerboundToggleSpawnPayload payload, FriendlyByteBuf buf) {
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
                MobSpawnManager.getAttributeOverrideMobs(), MobSpawnManager.getAllNaturalSpawnSettings(),
                MobSpawnManager.getAllActiveSpawnSettings()));
    }
}
