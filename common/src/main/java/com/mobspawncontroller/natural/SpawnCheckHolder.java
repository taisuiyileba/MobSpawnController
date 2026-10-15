package com.mobspawncontroller.natural;

import net.minecraft.world.entity.MobSpawnType;

/** Implemented on {@link net.minecraft.world.entity.Mob} by a mixin to track how its spawn was judged. */
public interface SpawnCheckHolder {

    boolean mobspawncontroller$isSpawnChecked();

    void mobspawncontroller$setSpawnChecked(boolean checked);

    /** Spawn type tagged at a call site that adds the mob without finalizeSpawn, or null when unknown. */
    MobSpawnType mobspawncontroller$getSpawnSource();

    void mobspawncontroller$setSpawnSource(MobSpawnType spawnType);
}
