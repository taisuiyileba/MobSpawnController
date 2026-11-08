package com.mobspawncontroller.loadout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutPresetTest {

    @Test
    void unknownOriginRequiresExplicitInclusion() {
        LoadoutPreset allKnown = LoadoutPreset.empty("default");
        assertTrue(allKnown.matches("natural", 0));
        assertTrue(allKnown.matches(LoadoutSources.EXTRA_SPAWN, 0));
        assertFalse(allKnown.matches(LoadoutSources.UNKNOWN, 0));

        List<String> unknown = List.of(LoadoutSources.UNKNOWN);
        LoadoutPreset optedIn = new LoadoutPreset("unknown", 1, unknown, List.of(), null, null, Map.of(), "");
        assertTrue(optedIn.matches(LoadoutSources.UNKNOWN, 0));
        assertFalse(optedIn.matches("natural", 0));
        LoadoutPreset excluded = new LoadoutPreset("excluded", 1, unknown, unknown, null, null, Map.of(), "");
        assertFalse(excluded.matches(LoadoutSources.UNKNOWN, 0));
    }
}
