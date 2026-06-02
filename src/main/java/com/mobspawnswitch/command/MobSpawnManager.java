package com.mobspawnswitch.command;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class MobSpawnManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> RULES = new HashMap<>();
    private static Path savePath;

    private MobSpawnManager() {
    }

    public static void setSavePath(Path path) {
        savePath = path;
    }

    public static void setAllowed(ResourceLocation mobId, MobSpawnType type, boolean allowed) {
        RULES.computeIfAbsent(mobId, k -> new EnumMap<>(MobSpawnType.class)).put(type, allowed);
    }

    public static void setAllAllowed(ResourceLocation mobId, boolean allowed) {
        EnumMap<MobSpawnType, Boolean> map = RULES.computeIfAbsent(mobId, k -> new EnumMap<>(MobSpawnType.class));
        for (MobSpawnType t : MobSpawnType.values()) {
            map.put(t, allowed);
        }
    }

    public static Boolean getAllowed(ResourceLocation mobId, MobSpawnType type) {
        EnumMap<MobSpawnType, Boolean> map = RULES.get(mobId);
        if (map == null) {
            return null;
        }
        return map.get(type);
    }

    public static Map<MobSpawnType, Boolean> getRules(ResourceLocation mobId) {
        return RULES.get(mobId);
    }

    public static Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> getAllRules() {
        return Collections.unmodifiableMap(RULES);
    }

    public static void clear(ResourceLocation mobId) {
        RULES.remove(mobId);
    }

    public static void clearAll() {
        RULES.clear();
    }

    public static void save() {
        if (savePath == null) {
            return;
        }
        JsonObject root = new JsonObject();
        RULES.forEach((mobId, map) -> {
            JsonObject mobObj = new JsonObject();
            map.forEach((type, allowed) -> mobObj.addProperty(type.name(), allowed));
            root.add(mobId.toString(), mobObj);
        });
        try {
            Files.createDirectories(savePath.getParent());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(savePath), StandardCharsets.UTF_8))) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save mob spawn rules", e);
        }
    }

    public static void load() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }
        RULES.clear();
        try (Reader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(savePath), StandardCharsets.UTF_8))) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> mobEntry : root.entrySet()) {
                ResourceLocation mobId = new ResourceLocation(mobEntry.getKey());
                if (!(mobEntry.getValue() instanceof JsonObject mobObj)) {
                    continue;
                }
                EnumMap<MobSpawnType, Boolean> map = new EnumMap<>(MobSpawnType.class);
                for (Map.Entry<String, JsonElement> typeEntry : mobObj.entrySet()) {
                    MobSpawnType type = parseSpawnType(typeEntry.getKey());
                    if (type != null && typeEntry.getValue().isJsonPrimitive()) {
                        map.put(type, typeEntry.getValue().getAsBoolean());
                    }
                }
                if (!map.isEmpty()) {
                    RULES.put(mobId, map);
                }
            }
            LOGGER.info("Loaded {} mob spawn rule entries", RULES.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load mob spawn rules", e);
        }
    }

    private static MobSpawnType parseSpawnType(String name) {
        for (MobSpawnType t : MobSpawnType.values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
