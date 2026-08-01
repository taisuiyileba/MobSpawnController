package com.mobspawncontroller.command;

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
    void naturalRulesPersistAlongsideSpawnSwitchesAndAttributes() throws Exception {
        ResourceLocation mobId = id("minecraft", "zombie");
        ResourceLocation attributeId = id("minecraft", "generic.max_health");
        NaturalSpawnSettings settings = settings();
        Path rulesFile = tempDir.resolve("rules.json");

        MobSpawnManager.setSavePath(rulesFile);
        MobSpawnManager.setAllowed(mobId, MobSpawnType.NATURAL, false);
        MobSpawnManager.setAttributeOverrides(mobId, Map.of(attributeId, 40.0));
        MobSpawnManager.setNaturalSpawnSettings(mobId, settings);
        MobSpawnManager.save();
        String savedJson = Files.readString(rulesFile);
        assertFalse(savedJson.contains("natural_spawn"));
        assertTrue(savedJson.contains("spawn_restrictions"));

        MobSpawnManager.clearAll();
        assertFalse(MobSpawnManager.getAllRules().containsKey(mobId));
        MobSpawnManager.load();

        assertEquals(Boolean.FALSE, MobSpawnManager.getAllowed(mobId, MobSpawnType.NATURAL));
        assertEquals(Map.of(attributeId, 40.0), MobSpawnManager.getAttributeOverrides(mobId));
        assertEquals(settings, MobSpawnManager.getNaturalSpawnSettings(mobId));
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
                0.5,
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
