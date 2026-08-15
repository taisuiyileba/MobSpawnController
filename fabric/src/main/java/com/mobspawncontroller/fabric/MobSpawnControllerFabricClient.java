package com.mobspawncontroller.fabric;

import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.client.gui.MobSpawnControllerScreen;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ClientboundSyncStructuresPayload;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.network.ServerboundRequestStructuresPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundSetActiveSpawnPayload;
import com.mobspawncontroller.network.ServerboundSetNaturalSpawnPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
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
        NetworkBridge.setToServerSender(MobSpawnControllerFabricClient::sendToServer);

        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncRulesPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ClientboundSyncRulesPayload payload = ClientboundSyncRulesPayload.read(buf);
                    client.execute(() -> ClientRuleSync.handle(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncAttributesPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ClientboundSyncAttributesPayload payload = ClientboundSyncAttributesPayload.read(buf);
                    client.execute(() -> ClientRuleSync.handle(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundSyncStructuresPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ClientboundSyncStructuresPayload payload = ClientboundSyncStructuresPayload.read(buf);
                    client.execute(() -> ClientRuleSync.handle(payload));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new MobSpawnControllerScreen());
                }
            }
        });
    }

    private static void sendToServer(Object payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        if (payload instanceof ServerboundToggleSpawnPayload toggle) {
            ServerboundToggleSpawnPayload.write(toggle, buf);
            ClientPlayNetworking.send(ServerboundToggleSpawnPayload.ID, buf);
        } else if (payload instanceof ServerboundRequestRulesPayload requestRules) {
            ServerboundRequestRulesPayload.write(requestRules, buf);
            ClientPlayNetworking.send(ServerboundRequestRulesPayload.ID, buf);
        } else if (payload instanceof ServerboundRequestAttributesPayload requestAttributes) {
            ServerboundRequestAttributesPayload.write(requestAttributes, buf);
            ClientPlayNetworking.send(ServerboundRequestAttributesPayload.ID, buf);
        } else if (payload instanceof ServerboundSetAttributesPayload setAttributes) {
            ServerboundSetAttributesPayload.write(setAttributes, buf);
            ClientPlayNetworking.send(ServerboundSetAttributesPayload.ID, buf);
        } else if (payload instanceof ServerboundSetNaturalSpawnPayload naturalSpawn) {
            ServerboundSetNaturalSpawnPayload.write(naturalSpawn, buf);
            ClientPlayNetworking.send(ServerboundSetNaturalSpawnPayload.ID, buf);
        } else if (payload instanceof ServerboundSetActiveSpawnPayload activeSpawn) {
            ServerboundSetActiveSpawnPayload.write(activeSpawn, buf);
            ClientPlayNetworking.send(ServerboundSetActiveSpawnPayload.ID, buf);
        } else if (payload instanceof ServerboundRequestStructuresPayload requestStructures) {
            ServerboundRequestStructuresPayload.write(requestStructures, buf);
            ClientPlayNetworking.send(ServerboundRequestStructuresPayload.ID, buf);
        }
    }
}
