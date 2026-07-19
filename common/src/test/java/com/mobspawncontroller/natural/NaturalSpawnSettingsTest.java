package com.mobspawncontroller.natural;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalSpawnSettingsTest {

    @Test
    void defaultsRoundTripThroughJson() {
        NaturalSpawnSettings defaults = NaturalSpawnSettings.defaults();

        JsonObject encoded = NaturalSpawnSettingsJsonCodec.encode(defaults);

        assertTrue(encoded.entrySet().isEmpty());
        assertEquals(defaults, NaturalSpawnSettingsJsonCodec.decode(encoded));
    }

    @Test
    void allFieldsRoundTripThroughJsonAndNetwork() {
        NaturalSpawnSettings settings = comprehensiveSettings();

        assertEquals(settings, NaturalSpawnSettingsJsonCodec.decode(
                NaturalSpawnSettingsJsonCodec.encode(settings)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            settings.write(buffer);
            assertEquals(settings, NaturalSpawnSettings.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void constructorNormalizesLegacySeasonsAndUnsafeBounds() {
        NaturalSpawnSettings defaults = NaturalSpawnSettings.defaults();
        NaturalSpawnSettings normalized = new NaturalSpawnSettings(
                2.0,
                defaults.minHeight(), defaults.maxHeight(),
                -4, 50,
                defaults.minTime(), defaults.maxTime(),
                -1, -2,
                List.of(-1, 2, 2, 8), List.of(7),
                -1.0, -2.0, -3.0, -4.0, -5.0, -6.0,
                null, null, null, null, null,
                null, null, null, null, null, null,
                List.of("spring", "MID_WINTER", "invalid"), List.of("autumn"),
                null, null, null, null,
                -1, -2, -3, -10.0,
                -1, 99, -2, 88,
                null, null, null, null);

        assertEquals(1.0, normalized.chance());
        assertEquals(0, normalized.minTotalLight());
        assertEquals(15, normalized.maxTotalLight());
        assertEquals(0, normalized.minDay());
        assertEquals(List.of(2), normalized.moonPhases());
        assertEquals(List.of("early_spring", "mid_spring", "late_spring", "mid_winter"),
                normalized.seasons());
        assertEquals(List.of("early_autumn", "mid_autumn", "late_autumn"),
                normalized.excludedSeasons());
        assertEquals(0.0, normalized.minPlayerDistance());
        assertEquals(0, normalized.maxNearby());
        assertEquals(1.0, normalized.nearbyRadius());
        assertEquals(0, normalized.minSkyLight());
        assertEquals(15, normalized.maxSkyLight());
    }

    private static NaturalSpawnSettings comprehensiveSettings() {
        return new NaturalSpawnSettings(
                0.375,
                -32, 180,
                1, 12,
                18000, 6000,
                2, 42,
                List.of(0, 3, 7), List.of(2, 5),
                12.5, 96.0,
                24.0, 1024.0,
                1.25, 4.5,
                NaturalSpawnSettings.WeatherMode.THUNDER,
                NaturalSpawnSettings.DifficultyMode.HARD,
                NaturalSpawnSettings.SkyMode.MUST_NOT_SEE,
                NaturalSpawnSettings.FluidMode.WATER,
                NaturalSpawnSettings.SlimeChunkMode.MUST_NOT,
                List.of(id("minecraft", "overworld")),
                List.of(id("minecraft", "the_end")),
                List.of(id("minecraft", "plains")),
                List.of(id("minecraft", "desert")),
                List.of(id("minecraft", "is_overworld")),
                List.of(id("minecraft", "is_badlands")),
                List.of("early_spring", "late_winter"),
                List.of("mid_summer"),
                List.of("minecraft:village_plains", "#minecraft:village", "*"),
                List.of("minecraft:ancient_city"),
                List.of("minecraft:grass_block", "#minecraft:logs"),
                List.of("minecraft:magma_block"),
                1, 8,
                5, 24.5,
                0, 15,
                1, 10,
                List.of("minecraft:air"),
                List.of("minecraft:water"),
                List.of("minecraft:stone"),
                List.of("#minecraft:leaves"));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
