package com.mobspawncontroller.loadout;

import com.mobspawncontroller.command.MobSpawnManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobLoadoutTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearState() {
        MobSpawnManager.clearAll();
    }

    @Test
    void loadoutRoundTripsThroughJsonNetworkAndRulesFile() throws Exception {
        MobLoadout loadout = sampleLoadout();

        assertEquals(loadout, MobLoadoutJsonCodec.decode(MobLoadoutJsonCodec.encode(loadout)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            loadout.write(buffer);
            assertEquals(loadout, MobLoadout.read(buffer));
        } finally {
            buffer.release();
        }

        ResourceLocation mobId = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");
        Path rulesFile = tempDir.resolve("rules.json");
        MobSpawnManager.setSavePath(rulesFile);
        MobSpawnManager.setLoadout(mobId, loadout);
        MobSpawnManager.save();
        String savedJson = Files.readString(rulesFile);
        assertTrue(savedJson.contains("\"loadout\""));
        // Single quotes inside NBT must stay readable instead of being HTML-escaped by Gson.
        assertTrue(savedJson.contains("{CustomName:'{\\\"text\\\":\\\"Knight\\\"}'}"));

        MobSpawnManager.clearAll();
        MobSpawnManager.load();
        assertEquals(loadout, MobSpawnManager.getLoadout(mobId));
        assertTrue(MobSpawnManager.getLoadoutMobs().contains(mobId));
    }

    @Test
    void emptyLoadoutIsNotStored() {
        ResourceLocation mobId = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");
        MobSpawnManager.setLoadout(mobId, new MobLoadout(0.5, List.of()));

        assertNull(MobSpawnManager.getLoadoutOrNull(mobId));
        assertFalse(MobSpawnManager.getLoadoutMobs().contains(mobId));
    }

    @Test
    void slotRulesDropFieldsTheirModeIgnores() {
        LoadoutSlotRule clear = new LoadoutSlotRule(LoadoutSlotRule.Mode.CLEAR, "minecraft:stone", 5,
                LoadoutSlotRule.DropMode.CUSTOM, 0.5F);
        assertEquals("", clear.item());
        assertEquals(LoadoutSlotRule.DropMode.DEFAULT, clear.dropMode());

        LoadoutPreset preset = new LoadoutPreset("p", 1, List.of(), List.of(), null, null,
                Map.of(EquipmentSlot.HEAD, LoadoutSlotRule.KEEP), "");
        assertTrue(preset.slots().isEmpty());
    }

    @Test
    void presetMatchesSourceAndDayConditions() {
        LoadoutPreset preset = new LoadoutPreset("p", 1, List.of("natural", "spawner"), List.of(), 10, 20,
                Map.of(), "");

        assertTrue(preset.matches("natural", 15));
        assertFalse(preset.matches("command", 15));
        assertFalse(preset.matches("natural", 9));
        assertFalse(preset.matches("natural", 21));

        LoadoutPreset excluding = new LoadoutPreset("q", 1, List.of(), List.of(LoadoutSources.EXTRA_SPAWN), null,
                null, Map.of(), "");
        assertTrue(excluding.matches("natural", 0));
        assertFalse(excluding.matches(LoadoutSources.EXTRA_SPAWN, 0));
    }

    private static MobLoadout sampleLoadout() {
        EnumMap<EquipmentSlot, LoadoutSlotRule> slots = new EnumMap<>(EquipmentSlot.class);
        slots.put(EquipmentSlot.HEAD, new LoadoutSlotRule(LoadoutSlotRule.Mode.SET,
                "minecraft:iron_helmet[damage=3]", 1, LoadoutSlotRule.DropMode.CUSTOM, 0.25F));
        slots.put(EquipmentSlot.FEET, new LoadoutSlotRule(LoadoutSlotRule.Mode.CLEAR, "", 1,
                LoadoutSlotRule.DropMode.DEFAULT, 0.0F));
        slots.put(EquipmentSlot.MAINHAND, new LoadoutSlotRule(LoadoutSlotRule.Mode.SET,
                "minecraft:diamond_sword", 1, LoadoutSlotRule.DropMode.ALWAYS, 0.0F));
        slots.put(EquipmentSlot.OFFHAND, new LoadoutSlotRule(LoadoutSlotRule.Mode.KEEP, "", 1,
                LoadoutSlotRule.DropMode.NEVER, 0.0F));
        LoadoutPreset knight = new LoadoutPreset("Knight", 3, List.of("natural", "spawner"), List.of(), 10, null,
                slots, "{CustomName:'{\"text\":\"Knight\"}'}");
        LoadoutPreset late = new LoadoutPreset("", 1, List.of(), List.of(LoadoutSources.EXTRA_SPAWN), null, 7,
                Map.of(), "");
        return new MobLoadout(0.6, List.of(knight, late));
    }
}
