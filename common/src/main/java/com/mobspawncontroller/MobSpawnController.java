package com.mobspawncontroller;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.nio.file.Path;

public final class MobSpawnController {

    public static final String MOD_ID = "mobspawncontroller";
    public static final String RULES_CONFIG_FILE = "rules.json";
    public static final Logger LOGGER = LogUtils.getLogger();

    private MobSpawnController() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        LOGGER.info("MobSpawnController initialized");
    }

    public static void setConfigDirectory(Path configDirectory) {
        MobSpawnManager.setSavePath(configDirectory.resolve(MOD_ID).resolve(RULES_CONFIG_FILE));
    }

    public static void serverStarting() {
        MobSpawnManager.load();
        LOGGER.info("MobSpawnController loaded rules configuration");
    }

    public static void serverStopping() {
        MobSpawnManager.save();
        LOGGER.info("MobSpawnController saved rules configuration");
    }
}
