package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.active.ActiveSpawnSettings;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundSetActiveSpawnPayload(ResourceLocation mobId, ActiveSpawnSettings settings) {

    public static final ResourceLocation ID = MobSpawnController.id("set_extra_spawn");

    public static ServerboundSetActiveSpawnPayload read(FriendlyByteBuf buf) {
        return new ServerboundSetActiveSpawnPayload(buf.readResourceLocation(), ActiveSpawnSettings.read(buf));
    }

    public static void write(ServerboundSetActiveSpawnPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.mobId);
        payload.settings.write(buf);
    }

    public static void handle(ServerboundSetActiveSpawnPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        MobSpawnManager.setActiveSpawnSettings(payload.mobId, payload.settings);
        MobSpawnManager.save();
        NetworkBridge.sendToPlayer(player, new ClientboundSyncRulesPayload(
                MobSpawnManager.getAllRules(), MobSpawnManager.getAttributeOverrideMobs(),
                MobSpawnManager.getAllNaturalSpawnSettings(), MobSpawnManager.getAllActiveSpawnSettings()));
    }
}
