package com.mobspawncontroller.natural;

import net.minecraft.world.entity.MobSpawnType;

/** Implemented on {@link net.minecraft.world.entity.Mob} by a mixin to track how its spawn was judged. */
public interface SpawnCheckHolder {

    boolean mobspawncontroller$isSpawnChecked();

    void mobspawncontroller$setSpawnChecked(boolean checked);

    /** True once this mob has passed a fresh-entity insertion hook. */
    boolean mobspawncontroller$hasJoined();

    void mobspawncontroller$markJoined();

    /** Conditions already ran at addFreshEntity; a late finalizeSpawn must still check its type switch. */
    boolean mobspawncontroller$areUnknownConditionsChecked();

    void mobspawncontroller$setUnknownConditionsChecked(boolean checked);

    /** Spawn type tagged at a call site that adds the mob without finalizeSpawn, or null when unknown. */
    MobSpawnType mobspawncontroller$getSpawnSource();

    void mobspawncontroller$setSpawnSource(MobSpawnType spawnType);
}
