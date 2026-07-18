package com.mobspawncontroller;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;

public final class MobSpawnController {

    public static final String MOD_ID = "mobspawncontroller";
    public static final Logger LOGGER = LogUtils.getLogger();

    private MobSpawnController() {
    }

    public static ResourceLocation id(String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(MOD_ID, path),
                () -> "Invalid MobSpawnController resource path: " + path);
    }

    public static void init() {
        LOGGER.info("MobSpawnController initialized");
    }

    public static void serverStarting(MinecraftServer server) {
        Path configDir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MOD_ID);
        MobSpawnManager.setSavePath(configDir.resolve("rules.json"));
        MobSpawnManager.load();
        LOGGER.info("MobSpawnController loaded rules");
    }

    public static void serverStopping() {
        MobSpawnManager.save();
        LOGGER.info("MobSpawnController saved rules");
    }
}
