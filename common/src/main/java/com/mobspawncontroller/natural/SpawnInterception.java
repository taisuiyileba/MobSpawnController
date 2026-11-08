package com.mobspawncontroller.natural;

import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.loadout.LoadoutApplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Coordinates the spawn hooks so every mob is judged exactly once, wherever it first shows up.
 * <p>
 * The primary check runs right before {@code finalizeSpawn}, through the loader event or the mixin on
 * {@link Mob#finalizeSpawn}. Mobs that reach a level without that check (breeding, conversions, eggs,
 * spawners with custom NBT, modded code that skips finalizeSpawn) are judged when they are added instead.
 * The per-mob marker keeps probabilistic conditions from being rolled twice for the same spawn.
 */
public final class SpawnInterception {

    private SpawnInterception() {
    }

    public static boolean isSpawnChecked(Mob mob) {
        return ((SpawnCheckHolder) mob).mobspawncontroller$isSpawnChecked();
    }

    /**
     * Runs the spawn switch and the vanilla-spawn conditions for a spawn about to be finalized.
     * Returns false when the spawn is denied; the caller decides how to cancel it.
     */
    public static boolean checkBeforeFinalize(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
        SpawnCheckHolder holder = (SpawnCheckHolder) mob;
        holder.mobspawncontroller$setSpawnChecked(true);
        holder.mobspawncontroller$setSpawnSource(null);
        if (!isKnownSpawnAllowed(mob, level, spawnType)) {
            return false;
        }
        MobSpawnManager.queueAttributeOverrides(mob, false);
        LoadoutApplier.prepare(mob, spawnType, false);
        return true;
    }

    private static boolean isKnownSpawnAllowed(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
        return MobSpawnManager.isSpawnTypeAllowed(mob, spawnType)
                && (((SpawnCheckHolder) mob).mobspawncontroller$areUnknownConditionsChecked()
                || MobSpawnManager.isNaturalSpawnAllowed(mob, level, spawnType));
    }

    /**
     * Finalizes an extra-spawner mob through the general spawn switch, attribute overrides and loadout.
     * Vanilla-spawn conditions are intentionally not evaluated because extra spawning has its own page.
     */
    public static boolean finalizeControlledSpawn(Mob mob, ServerLevel level, MobSpawnType spawnType) {
        ((SpawnCheckHolder) mob).mobspawncontroller$setSpawnChecked(true);
        if (!MobSpawnManager.isSpawnTypeAllowed(mob, spawnType)) {
            mob.discard();
            return false;
        }
        MobSpawnManager.queueAttributeOverrides(mob, false);
        LoadoutApplier.prepare(mob, spawnType, true);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), spawnType, null, null);
        return !mob.isRemoved();
    }

    /**
     * Fabric's vanilla natural-spawner call site uses this wrapper so overrides that skip
     * {@code super.finalizeSpawn} cannot bypass spawn rules.
     */
    public static SpawnGroupData finalizeNaturalSpawn(Mob mob, ServerLevelAccessor level,
                                                       DifficultyInstance difficulty, MobSpawnType spawnType,
                                                       SpawnGroupData spawnData, CompoundTag spawnTag) {
        if (!isSpawnChecked(mob) && !checkBeforeFinalize(mob, level, spawnType)) {
            mob.discard();
            return spawnData;
        }
        return mob.finalizeSpawn(level, difficulty, spawnType, spawnData, spawnTag);
    }

    /**
     * Tags a mob and its passengers that a vanilla call site adds without calling finalizeSpawn.
     * The tagging injections are optional (require = 0): if another mod rewrites one of those call sites,
     * its mobs are simply judged as unknown-origin spawns.
     */
    public static Entity tagSpawnSource(Entity entity, MobSpawnType spawnType) {
        entity.getSelfAndPassengers().forEach(part -> {
            if (part instanceof Mob mob) {
                SpawnCheckHolder holder = (SpawnCheckHolder) mob;
                if (!holder.mobspawncontroller$isSpawnChecked() && !holder.mobspawncontroller$hasJoined()) {
                    holder.mobspawncontroller$setSpawnSource(spawnType);
                }
            }
        });
        return entity;
    }

    /**
     * Judges a mob that is being added to a level without having been checked before finalizeSpawn.
     * Tagged mobs get the full rules for their spawn type. Mobs of unknown origin are only blocked when
     * every spawn type is switched off, or when explicitly opted-in unknown-origin conditions deny them.
     * Unknown mobs keep their type check pending so a later finalizeSpawn can still apply its switch.
     * Returns false when the mob must not be added.
     */
    public static boolean checkOnJoin(Entity entity, ServerLevelAccessor level) {
        if (!(entity instanceof Mob mob)) {
            return true;
        }
        SpawnCheckHolder holder = (SpawnCheckHolder) mob;
        if (holder.mobspawncontroller$isSpawnChecked() || holder.mobspawncontroller$hasJoined()) {
            // Denied spawns were discarded already; refusing here avoids vanilla's "marked as removed" warning.
            return !mob.isRemoved();
        }

        MobSpawnType source = holder.mobspawncontroller$getSpawnSource();
        boolean allowed;
        if (source != null) {
            holder.mobspawncontroller$setSpawnChecked(true);
            allowed = isKnownSpawnAllowed(mob, level, source);
        } else {
            allowed = !MobSpawnManager.isFullyDisabled(mob);
            if (allowed && !holder.mobspawncontroller$areUnknownConditionsChecked()
                    && MobSpawnManager.hasUnknownSpawnConditions(mob)) {
                holder.mobspawncontroller$setUnknownConditionsChecked(true);
                allowed = MobSpawnManager.isNaturalSpawnAllowed(mob, level, null);
            }
        }
        if (!allowed) {
            holder.mobspawncontroller$setSpawnChecked(true);
            holder.mobspawncontroller$setSpawnSource(null);
            mob.discard();
        }
        return allowed;
    }

    /**
     * Queues the post-join changes for a mob that joined without being finalized first.
     * A late finalizeSpawn can still check the source rules before the pending changes are applied.
     */
    public static void acceptOnJoin(Entity entity, boolean added) {
        if (!added || !(entity instanceof Mob mob)) {
            return;
        }
        SpawnCheckHolder holder = (SpawnCheckHolder) mob;
        if (holder.mobspawncontroller$hasJoined()) {
            return;
        }
        holder.mobspawncontroller$markJoined();
        MobSpawnType source = holder.mobspawncontroller$getSpawnSource();
        holder.mobspawncontroller$setSpawnSource(null);
        if (source != null || !holder.mobspawncontroller$isSpawnChecked()) {
            MobSpawnManager.queueAttributeOverrides(mob, source == null);
            LoadoutApplier.prepare(mob, source, false);
        }
    }
}
