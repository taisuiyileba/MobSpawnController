package com.mobspawncontroller.command;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobSpawnManagerFullyDisabledTest {

    private static final ResourceLocation COW = new ResourceLocation("minecraft", "cow");

    @AfterEach
    void clearState() {
        MobSpawnManager.clearAll();
    }

    @Test
    void mobWithoutRulesIsNotFullyDisabled() {
        assertFalse(MobSpawnManager.isFullyDisabled(COW));
    }

    @Test
    void disablingEverySpawnTypeFullyDisablesMob() {
        MobSpawnManager.setAllAllowed(COW, false);
        assertTrue(MobSpawnManager.isFullyDisabled(COW));
    }

    @Test
    void oneAllowedSpawnTypeKeepsMobEnabled() {
        MobSpawnManager.setAllAllowed(COW, false);
        MobSpawnManager.setAllowed(COW, MobSpawnType.BREEDING, true);
        assertFalse(MobSpawnManager.isFullyDisabled(COW));
    }

    @Test
    void spawnTypesWithoutRuleCountAsAllowed() {
        for (MobSpawnType type : MobSpawnType.values()) {
            if (type != MobSpawnType.COMMAND) {
                MobSpawnManager.setAllowed(COW, type, false);
            }
        }
        assertFalse(MobSpawnManager.isFullyDisabled(COW));
    }
}
