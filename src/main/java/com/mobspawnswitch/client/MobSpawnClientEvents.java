package com.mobspawnswitch.client;

import com.mobspawnswitch.Mobspawnswitch;
import com.mobspawnswitch.client.gui.MobSpawnSwitchScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class MobSpawnClientEvents {

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.mobspawnswitch.open_gui",
            GLFW.GLFW_KEY_M,
            "key.categories.mobspawnswitch"
    );

    @Mod.EventBusSubscriber(modid = Mobspawnswitch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_GUI_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = Mobspawnswitch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (OPEN_GUI_KEY.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new MobSpawnSwitchScreen());
                }
            }
        }
    }
}
