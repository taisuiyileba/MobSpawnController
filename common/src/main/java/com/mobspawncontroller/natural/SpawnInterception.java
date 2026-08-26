package com.mobspawncontroller.natural;

import com.mobspawncontroller.command.MobSpawnManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Coordinates loader-specific spawn hooks without evaluating probabilistic rules twice. */
public final class SpawnInterception {

    private static final ThreadLocal<Set<Mob>> PRECHECKED_MOBS = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static boolean platformHandlesFinalizeSpawn;

    private SpawnInterception() {
    }

    /** NeoForge has a loader event that runs before every correctly dispatched finalizeSpawn call. */
    public static void setPlatformHandlesFinalizeSpawn(boolean handled) {
        platformHandlesFinalizeSpawn = handled;
    }

    public static boolean isHandledBeforeMobFinalizeSpawn(Mob mob) {
        return platformHandlesFinalizeSpawn || PRECHECKED_MOBS.get().contains(mob);
    }

    /** Returns true only for a spawn explicitly prechecked by this coordinator. */
    public static boolean isPrechecked(Mob mob) {
        return PRECHECKED_MOBS.get().contains(mob);
    }

    /**
     * Finalizes an extra-spawner mob through the general spawn switch and attribute overrides.
     * Vanilla-spawn conditions are intentionally not evaluated because extra spawning has its own page.
     * The marker prevents loader hooks from applying probabilistic conditions a second time.
     */
    public static boolean finalizeControlledSpawn(Mob mob, ServerLevel level, MobSpawnType spawnType) {
        if (!MobSpawnManager.isSpawnTypeAllowed(mob, spawnType)) {
            mob.discard();
            return false;
        }
        MobSpawnManager.applyAttributeOverrides(mob);
        Set<Mob> prechecked = PRECHECKED_MOBS.get();
        prechecked.add(mob);
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), spawnType, null);
            return !mob.isRemoved();
        } finally {
            prechecked.remove(mob);
            if (prechecked.isEmpty()) PRECHECKED_MOBS.remove();
        }
    }

    /**
     * Fabric's vanilla natural-spawner call site uses this wrapper so overrides that skip
     * {@code super.finalizeSpawn} cannot bypass spawn rules.
     */
    public static SpawnGroupData finalizeNaturalSpawn(Mob mob, ServerLevelAccessor level,
                                                       DifficultyInstance difficulty, MobSpawnType spawnType,
                                                       SpawnGroupData spawnData) {
        if (!MobSpawnManager.isSpawnAllowed(mob, level, spawnType)) {
            mob.discard();
            return spawnData;
        }

        MobSpawnManager.applyAttributeOverrides(mob);
        Set<Mob> prechecked = PRECHECKED_MOBS.get();
        prechecked.add(mob);
        try {
            return mob.finalizeSpawn(level, difficulty, spawnType, spawnData);
        } finally {
            prechecked.remove(mob);
            if (prechecked.isEmpty()) {
                PRECHECKED_MOBS.remove();
            }
        }
    }
}
