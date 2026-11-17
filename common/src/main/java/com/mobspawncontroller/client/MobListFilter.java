package com.mobspawncontroller.client;

import com.mobspawncontroller.MobSpawnController;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Matches the Mob-based spawn hooks, including mobs registered in the MISC category. */
public final class MobListFilter {
    private final Map<EntityType<?>, Entity> entityCache;
    private final Set<EntityType<?>> rejectedTypes = new HashSet<>();

    public MobListFilter(Map<EntityType<?>, Entity> entityCache) {
        this.entityCache = entityCache;
    }

    public boolean isMob(EntityType<?> type, Level level) {
        Entity cached = entityCache.get(type);
        if (cached != null) {
            return cached instanceof Mob;
        }
        if (level == null || rejectedTypes.contains(type)) {
            return false;
        }

        try {
            // Inspect without spawning; reuse accepted instances when rendering their icons.
            Entity entity = type.create(level);
            if (entity instanceof Mob) {
                entityCache.put(type, entity);
                return true;
            }
        } catch (Exception | LinkageError error) {
            MobSpawnController.LOGGER.warn("Could not inspect entity type {} for the mob list",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), error);
        }
        rejectedTypes.add(type);
        return false;
    }

    public void clear() {
        entityCache.clear();
        rejectedTypes.clear();
    }
}
