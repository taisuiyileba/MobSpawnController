package com.mobspawncontroller.fabric;

import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.client.gui.MobSpawnControllerScreen;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ClientboundSyncStructuresPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

public final class MobSpawnControllerFabricClient implements ClientModInitializer {

    private static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.mobspawncontroller.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.mobspawncontroller"
    );

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(OPEN_GUI_KEY);
        NetworkBridge.setSender(new NetworkBridge.PacketSender() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                ClientPlayNetworking.send(payload);
            }

            @Override
            public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncRulesPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientRuleSync.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncAttributesPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientRuleSync.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncStructuresPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientRuleSync.handle(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new MobSpawnControllerScreen());
                }
            }
        });
    }
}
