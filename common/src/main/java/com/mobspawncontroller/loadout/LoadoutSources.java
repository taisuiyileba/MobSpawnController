package com.mobspawncontroller.loadout;

import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Spawn source names used by loadout filters: vanilla spawn types plus this mod's extra spawner. */
public final class LoadoutSources {

    public static final String EXTRA_SPAWN = "extra_spawn";

    private LoadoutSources() {
    }

    public static String of(MobSpawnType spawnType) {
        return spawnType.name().toLowerCase(Locale.ROOT);
    }

    public static List<String> all() {
        List<String> sources = new ArrayList<>();
        for (MobSpawnType type : MobSpawnType.values()) {
            sources.add(of(type));
        }
        sources.add(EXTRA_SPAWN);
        return sources;
    }
}
