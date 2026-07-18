package com.mobspawncontroller.forge;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.client.gui.MobSpawnControllerScreen;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
        modid = MobSpawnController.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class MobSpawnControllerForgeClient {

    private static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.mobspawncontroller.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.mobspawncontroller"
    );

    private MobSpawnControllerForgeClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NetworkBridge.setToServerSender(MobSpawnControllerForge::sendToServer);
        MinecraftForge.EVENT_BUS.addListener(MobSpawnControllerForgeClient::onClientTick);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
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
