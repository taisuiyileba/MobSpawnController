package com.mobspawncontroller.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mobspawncontroller.MobSpawnController;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MobSpawnManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> RULES = new HashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, Double>> ATTRIBUTE_OVERRIDES = new HashMap<>();
    private static Path savePath;

    private MobSpawnManager() {
    }

    public static void setSavePath(Path path) {
        savePath = path;
    }

    public static void setAllowed(ResourceLocation mobId, MobSpawnType type, boolean allowed) {
        RULES.computeIfAbsent(mobId, key -> new EnumMap<>(MobSpawnType.class)).put(type, allowed);
    }

    public static void setAllAllowed(ResourceLocation mobId, boolean allowed) {
        EnumMap<MobSpawnType, Boolean> map = RULES.computeIfAbsent(mobId, key -> new EnumMap<>(MobSpawnType.class));
        for (MobSpawnType type : MobSpawnType.values()) {
            map.put(type, allowed);
        }
    }

    public static Boolean getAllowed(ResourceLocation mobId, MobSpawnType type) {
        EnumMap<MobSpawnType, Boolean> map = RULES.get(mobId);
        return map == null ? null : map.get(type);
    }

    public static Map<MobSpawnType, Boolean> getRules(ResourceLocation mobId) {
        return RULES.get(mobId);
    }

    public static Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> getAllRules() {
        return Collections.unmodifiableMap(RULES);
    }

    public static Double getAttributeOverride(ResourceLocation mobId, ResourceLocation attributeId) {
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        return attributes == null ? null : attributes.get(attributeId);
    }

    public static Map<ResourceLocation, Double> getAttributeOverrides(ResourceLocation mobId) {
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        return attributes == null ? Map.of() : Collections.unmodifiableMap(attributes);
    }

    public static Set<ResourceLocation> getAttributeOverrideMobs() {
        return Collections.unmodifiableSet(new HashSet<>(ATTRIBUTE_OVERRIDES.keySet()));
    }

    public static void setAttributeOverrides(ResourceLocation mobId, Map<ResourceLocation, Double> attributes) {
        if (attributes.isEmpty()) {
            ATTRIBUTE_OVERRIDES.remove(mobId);
            return;
        }
        ATTRIBUTE_OVERRIDES.put(mobId, new HashMap<>(attributes));
    }

    public static void applyAttributeOverrides(LivingEntity entity) {
        ResourceLocation mobId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        attributes.forEach((attributeId, value) -> net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
                .getHolder(attributeId)
                .ifPresent(holder -> applyAttributeOverride(entity, holder, value)));

        Attributes.MAX_HEALTH.unwrapKey()
                .map(key -> key.location())
                .filter(attributes::containsKey)
                .ifPresent(attributeId -> entity.setHealth(entity.getMaxHealth()));
    }

    private static void applyAttributeOverride(LivingEntity entity, net.minecraft.core.Holder<Attribute> holder,
                                               double value) {
        if (!entity.getAttributes().hasAttribute(holder)) {
            return;
        }
        AttributeInstance instance = entity.getAttributes().getInstance(holder);
        if (instance != null) {
            instance.setBaseValue(holder.value().sanitizeValue(value));
        }
    }

    public static void clear(ResourceLocation mobId) {
        RULES.remove(mobId);
        ATTRIBUTE_OVERRIDES.remove(mobId);
    }

    public static void clearAll() {
        RULES.clear();
        ATTRIBUTE_OVERRIDES.clear();
    }

    public static void save() {
        if (savePath == null) {
            return;
        }

        JsonObject root = new JsonObject();
        RULES.forEach((mobId, map) -> {
            JsonObject mobObj = new JsonObject();
            map.forEach((type, allowed) -> mobObj.addProperty(type.name(), allowed));
            addAttributeOverrides(mobId, mobObj);
            root.add(mobId.toString(), mobObj);
        });
        ATTRIBUTE_OVERRIDES.forEach((mobId, map) -> {
            if (!root.has(mobId.toString())) {
                JsonObject mobObj = new JsonObject();
                addAttributeOverrides(mobId, mobObj);
                root.add(mobId.toString(), mobObj);
            }
        });

        try {
            Files.createDirectories(savePath.getParent());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(savePath), StandardCharsets.UTF_8))) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            MobSpawnController.LOGGER.error("Failed to save mob spawn rules", e);
        }
    }

    private static void addAttributeOverrides(ResourceLocation mobId, JsonObject mobObj) {
        Map<ResourceLocation, Double> attributes = ATTRIBUTE_OVERRIDES.get(mobId);
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        JsonObject attributeObj = new JsonObject();
        attributes.forEach((attributeId, value) -> attributeObj.addProperty(attributeId.toString(), value));
        mobObj.add(ATTRIBUTES_KEY, attributeObj);
    }

    public static void load() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }

        RULES.clear();
        ATTRIBUTE_OVERRIDES.clear();
        try (Reader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(savePath), StandardCharsets.UTF_8))) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }

            for (Map.Entry<String, JsonElement> mobEntry : root.entrySet()) {
                ResourceLocation mobId = ResourceLocation.tryParse(mobEntry.getKey());
                if (mobId == null || !mobEntry.getValue().isJsonObject()) {
                    continue;
                }

                EnumMap<MobSpawnType, Boolean> map = new EnumMap<>(MobSpawnType.class);
                for (Map.Entry<String, JsonElement> typeEntry : mobEntry.getValue().getAsJsonObject().entrySet()) {
                    if (typeEntry.getKey().equals(ATTRIBUTES_KEY) && typeEntry.getValue().isJsonObject()) {
                        loadAttributeOverrides(mobId, typeEntry.getValue().getAsJsonObject());
                        continue;
                    }
                    MobSpawnType type = parseSpawnType(typeEntry.getKey());
                    if (type != null && typeEntry.getValue().isJsonPrimitive()) {
                        map.put(type, typeEntry.getValue().getAsBoolean());
                    }
                }
                if (!map.isEmpty()) {
                    RULES.put(mobId, map);
                }
            }
            MobSpawnController.LOGGER.info("Loaded {} mob spawn rule entries", RULES.size());
        } catch (Exception e) {
            MobSpawnController.LOGGER.error("Failed to load mob spawn rules", e);
        }
    }

    private static void loadAttributeOverrides(ResourceLocation mobId, JsonObject attributeObj) {
        Map<ResourceLocation, Double> attributes = new HashMap<>();
        for (Map.Entry<String, JsonElement> attributeEntry : attributeObj.entrySet()) {
            ResourceLocation attributeId = ResourceLocation.tryParse(attributeEntry.getKey());
            if (attributeId != null && attributeEntry.getValue().isJsonPrimitive()
                    && attributeEntry.getValue().getAsJsonPrimitive().isNumber()) {
                attributes.put(attributeId, attributeEntry.getValue().getAsDouble());
            }
        }
        if (!attributes.isEmpty()) {
            ATTRIBUTE_OVERRIDES.put(mobId, attributes);
        }
    }

    public static MobSpawnType parseSpawnType(String name) {
        for (MobSpawnType type : MobSpawnType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
