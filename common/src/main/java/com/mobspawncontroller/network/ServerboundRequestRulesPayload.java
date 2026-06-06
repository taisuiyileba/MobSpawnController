package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

import java.util.EnumMap;
import java.util.Map;

public record ServerboundRequestRulesPayload() {

    public static final ResourceLocation ID = MobSpawnController.id("request_rules");

    public static ServerboundRequestRulesPayload read(FriendlyByteBuf buf) {
        return new ServerboundRequestRulesPayload();
    }

    public static void write(ServerboundRequestRulesPayload payload, FriendlyByteBuf buf) {
    }

    public static void handle(ServerboundRequestRulesPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        Map<net.minecraft.resources.ResourceLocation, EnumMap<MobSpawnType, Boolean>> allRules =
                MobSpawnManager.getAllRules();
        NetworkBridge.sendToPlayer(player, new ClientboundSyncRulesPayload(allRules,
                MobSpawnManager.getAttributeOverrideMobs()));
    }
}
