package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundSetNaturalSpawnPayload(ResourceLocation mobId, NaturalSpawnSettings settings) {

    public static final ResourceLocation ID = MobSpawnController.id("set_natural_spawn");

    public static ServerboundSetNaturalSpawnPayload read(FriendlyByteBuf buf) {
        return new ServerboundSetNaturalSpawnPayload(buf.readResourceLocation(), NaturalSpawnSettings.read(buf));
    }

    public static void write(ServerboundSetNaturalSpawnPayload payload, FriendlyByteBuf buf) {
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
                MobSpawnManager.getAllNaturalSpawnSettings(), MobSpawnManager.getAllActiveSpawnSettings()));
    }
}
