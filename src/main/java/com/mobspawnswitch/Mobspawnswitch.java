package com.mobspawnswitch;

import com.mobspawnswitch.command.MobSpawnManager;
import com.mobspawnswitch.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod(Mobspawnswitch.MODID)
public class Mobspawnswitch {

    public static final String MODID = "mobspawnswitch";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Mobspawnswitch() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkHandler.register();
        LOGGER.info("MobSpawnSwitch initializing");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Path configDir = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve(MODID);
        MobSpawnManager.setSavePath(configDir.resolve("rules.json"));
        MobSpawnManager.load();
        LOGGER.info("MobSpawnSwitch loaded rules");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MobSpawnManager.save();
        LOGGER.info("MobSpawnSwitch saved rules");
    }
}
