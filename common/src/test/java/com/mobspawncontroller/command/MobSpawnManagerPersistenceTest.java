package com.mobspawncontroller.command;

import com.google.gson.JsonObject;
import com.mobspawncontroller.active.ActiveSpawnSettings;
import com.mobspawncontroller.active.ActiveSpawnSettingsJsonCodec;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobSpawnManagerPersistenceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearState() {
        MobSpawnManager.clearAll();
    }

    @Test
    void spawnSettingsPersistAlongsideSpawnSwitchesAndAttributes() throws Exception {
        ResourceLocation mobId = id("minecraft", "zombie");
        ResourceLocation attributeId = id("minecraft", "generic.max_health");
        NaturalSpawnSettings settings = settings();
        JsonObject activeJson = new JsonObject();
        activeJson.addProperty("enabled", true);
        activeJson.addProperty("chance_per_second", 0.25);
        activeJson.addProperty("max_world_count", 12);
        ActiveSpawnSettings activeSettings = ActiveSpawnSettingsJsonCodec.decode(activeJson);
        Path rulesFile = tempDir.resolve("rules.json");

        MobSpawnManager.setSavePath(rulesFile);
        MobSpawnManager.setAllowed(mobId, MobSpawnType.NATURAL, false);
        MobSpawnManager.setAttributeOverrides(mobId, Map.of(attributeId, 40.0));
        MobSpawnManager.setNaturalSpawnSettings(mobId, settings);
        MobSpawnManager.setActiveSpawnSettings(mobId, activeSettings);
        MobSpawnManager.save();
        String savedJson = Files.readString(rulesFile);
        assertFalse(savedJson.contains("spawn_restrictions"));
        assertTrue(savedJson.contains("vanilla_spawn"));
        assertTrue(savedJson.contains("extra_spawn"));

        MobSpawnManager.clearAll();
        assertFalse(MobSpawnManager.getAllRules().containsKey(mobId));
        MobSpawnManager.load();

        assertEquals(Boolean.FALSE, MobSpawnManager.getAllowed(mobId, MobSpawnType.NATURAL));
        assertEquals(Map.of(attributeId, 40.0), MobSpawnManager.getAttributeOverrides(mobId));
        assertEquals(settings, MobSpawnManager.getNaturalSpawnSettings(mobId));
        assertEquals(activeSettings, MobSpawnManager.getActiveSpawnSettings(mobId));
    }

    @Test
    void legacySpawnRestrictionsStillLoad() throws Exception {
        ResourceLocation mobId = id("minecraft", "zombie");
        Path rulesFile = tempDir.resolve("legacy-rules.json");
        Files.writeString(rulesFile, """
                {
                  "minecraft:zombie": {
                    "spawn_restrictions": {
                      "chance": 0.25
                    }
                  }
                }
                """);

        MobSpawnManager.setSavePath(rulesFile);
        MobSpawnManager.load();

        assertEquals(0.25, MobSpawnManager.getNaturalSpawnSettings(mobId).chance());
    }

    @Test
    void loadingMissingGlobalConfigClearsPreviousState() {
        ResourceLocation mobId = id("minecraft", "zombie");
        MobSpawnManager.setAllowed(mobId, MobSpawnType.NATURAL, false);
        MobSpawnManager.setSavePath(tempDir.resolve("missing.json"));

        MobSpawnManager.load();

        assertFalse(MobSpawnManager.getAllRules().containsKey(mobId));
    }

    private static NaturalSpawnSettings settings() {
        return new NaturalSpawnSettings(
                0.5, 2.5,
                10, 90,
                null, null,
                13000, 23000,
                null, null,
                List.of(), List.of(),
                16.0, 64.0,
                null, null,
                null, null,
                NaturalSpawnSettings.WeatherMode.RAIN,
                NaturalSpawnSettings.DifficultyMode.NORMAL,
                NaturalSpawnSettings.SkyMode.ANY,
                NaturalSpawnSettings.FluidMode.ANY,
                NaturalSpawnSettings.SlimeChunkMode.ANY,
                List.of(id("minecraft", "overworld")), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of("#minecraft:village"), List.of(),
                List.of("minecraft:grass_block"), List.of(),
                null, null,
                4, 24.0,
                null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(MobSpawnType.NATURAL, MobSpawnType.COMMAND), List.of());
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
