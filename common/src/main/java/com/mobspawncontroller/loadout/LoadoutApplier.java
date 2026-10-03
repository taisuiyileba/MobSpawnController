package com.mobspawncontroller.loadout;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies loadouts in two steps. A preset is chosen when the spawn is accepted, which happens before
 * {@code finalizeSpawn} runs; vanilla subclasses hand out their random equipment after that, so the preset
 * is only written once the mob joins a level (or on its first tick when finalizeSpawn ran after joining).
 */
public final class LoadoutApplier {

    private LoadoutApplier() {
    }

    public static void prepare(Mob mob, MobSpawnType spawnType, boolean extraSpawn) {
        PendingLoadoutHolder holder = (PendingLoadoutHolder) mob;
        holder.mobspawncontroller$setPendingLoadout(null);
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        MobLoadout loadout = MobSpawnManager.getLoadoutOrNull(mobId);
        if (loadout == null || loadout.presets().isEmpty()) return;

        // The entity's own random keeps world-generation threads off the shared level random.
        RandomSource random = mob.getRandom();
        if (random.nextDouble() >= loadout.chance()) return;

        String source = extraSpawn ? LoadoutSources.EXTRA_SPAWN : LoadoutSources.of(spawnType);
        long day = mob.level().getDayTime() / 24000L;
        List<LoadoutPreset> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (LoadoutPreset preset : loadout.presets()) {
            if (preset.matches(source, day)) {
                candidates.add(preset);
                totalWeight += preset.weight();
            }
        }
        if (totalWeight <= 0) return;

        int roll = random.nextInt(totalWeight);
        for (LoadoutPreset preset : candidates) {
            roll -= preset.weight();
            if (roll < 0) {
                holder.mobspawncontroller$setPendingLoadout(preset);
                return;
            }
        }
    }

    public static void applyPending(Entity entity) {
        if (!(entity instanceof Mob mob)) return;
        PendingLoadoutHolder holder = (PendingLoadoutHolder) mob;
        LoadoutPreset preset = holder.mobspawncontroller$getPendingLoadout();
        if (preset == null) return;
        holder.mobspawncontroller$setPendingLoadout(null);
        try {
            apply(mob, preset);
        } catch (RuntimeException exception) {
            MobSpawnController.LOGGER.warn("Failed to apply loadout '{}' to {}", preset.name(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), exception);
        }
    }

    /** NBT is merged first so explicit slot rules win over ArmorItems/HandItems in the NBT. */
    private static void apply(Mob mob, LoadoutPreset preset) {
        CompoundTag nbt = LoadoutParsing.cachedNbt(preset.nbt());
        if (nbt != null && !nbt.isEmpty()) {
            UUID uuid = mob.getUUID();
            CompoundTag merged = mob.saveWithoutId(new CompoundTag());
            merged.merge(nbt);
            mob.load(merged);
            mob.setUUID(uuid);
            if (!nbt.contains("Health")) mob.setHealth(mob.getMaxHealth());
        }

        for (EquipmentSlot slot : LoadoutPreset.SLOT_ORDER) {
            LoadoutSlotRule rule = preset.slot(slot);
            switch (rule.mode()) {
                case CLEAR -> mob.setItemSlot(slot, ItemStack.EMPTY);
                case SET -> {
                    ItemStack template = LoadoutParsing.cachedItem(rule.item(), mob.level().registryAccess());
                    if (!template.isEmpty()) {
                        ItemStack stack = template.copy();
                        stack.setCount(Math.min(rule.count(), stack.getMaxStackSize()));
                        mob.setItemSlot(slot, stack);
                    }
                }
                case KEEP -> {
                }
            }
            switch (rule.dropMode()) {
                case NEVER -> mob.setDropChance(slot, 0.0F);
                case CUSTOM -> mob.setDropChance(slot, rule.dropChance());
                case ALWAYS -> mob.setDropChance(slot, 2.0F);
                case DEFAULT -> {
                }
            }
        }
    }
}
