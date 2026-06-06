package com.mobspawncontroller.neoforge;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.client.gui.MobSpawnControllerScreen;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MobSpawnController.MOD_ID, value = Dist.CLIENT)
public final class MobSpawnControllerNeoForgeClient {

    private static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.mobspawncontroller.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.mobspawncontroller"
    );

    private MobSpawnControllerNeoForgeClient() {
    }

    public static void init() {
        NetworkBridge.setSender(new NetworkBridge.PacketSender() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                PacketDistributor.sendToServer(payload);
            }

            @Override
            public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        while (OPEN_GUI_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MobSpawnControllerScreen());
            }
        }
    }
}
