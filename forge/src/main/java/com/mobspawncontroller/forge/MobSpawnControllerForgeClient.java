package com.mobspawncontroller.forge;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.client.gui.MobSpawnControllerScreen;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MobSpawnController.MOD_ID, value = Dist.CLIENT)
public final class MobSpawnControllerForgeClient {

    private static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.mobspawncontroller.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.mobspawncontroller"
    );

    private MobSpawnControllerForgeClient() {
    }

    public static void init() {
        NetworkBridge.setToServerSender(MobSpawnControllerForge::sendToServer);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
