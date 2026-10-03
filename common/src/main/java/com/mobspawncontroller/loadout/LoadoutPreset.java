package com.mobspawncontroller.loadout;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One weighted equipment/NBT preset of a mob loadout.
 * Empty source lists and null day bounds mean that the condition is unrestricted.
 */
public record LoadoutPreset(String name, int weight, List<String> sources, List<String> excludedSources,
                            Integer minDay, Integer maxDay, Map<EquipmentSlot, LoadoutSlotRule> slots,
                            String nbt) {

    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_NBT_LENGTH = 4096;
    public static final int MAX_WEIGHT = 1000;
    public static final List<EquipmentSlot> SLOT_ORDER = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    public LoadoutPreset {
        name = name == null ? "" : name.trim();
        if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
        weight = Math.max(1, Math.min(MAX_WEIGHT, weight));
        sources = immutableSources(sources);
        excludedSources = immutableSources(excludedSources);
        minDay = minDay == null ? null : Math.max(0, minDay);
        maxDay = maxDay == null ? null : Math.max(0, maxDay);
        EnumMap<EquipmentSlot, LoadoutSlotRule> copy = new EnumMap<>(EquipmentSlot.class);
        if (slots != null) {
            slots.forEach((slot, rule) -> {
                if (slot != null && rule != null && !rule.isDefault()) copy.put(slot, rule);
            });
        }
        slots = Collections.unmodifiableMap(copy);
        nbt = nbt == null ? "" : nbt.trim();
        if (nbt.length() > MAX_NBT_LENGTH) nbt = nbt.substring(0, MAX_NBT_LENGTH);
    }

    public static LoadoutPreset empty(String name) {
        return new LoadoutPreset(name, 1, List.of(), List.of(), null, null, Map.of(), "");
    }

    public LoadoutSlotRule slot(EquipmentSlot slot) {
        return slots.getOrDefault(slot, LoadoutSlotRule.KEEP);
    }

    public boolean matches(String source, long day) {
        return (sources.isEmpty() || sources.contains(source))
                && !excludedSources.contains(source)
                && (minDay == null || day >= minDay)
                && (maxDay == null || day <= maxDay);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, MAX_NAME_LENGTH);
        buf.writeVarInt(weight);
        buf.writeCollection(sources, (target, value) -> target.writeUtf(value, 64));
        buf.writeCollection(excludedSources, (target, value) -> target.writeUtf(value, 64));
        writeNullableInt(buf, minDay);
        writeNullableInt(buf, maxDay);
        buf.writeVarInt(slots.size());
        slots.forEach((slot, rule) -> {
            buf.writeEnum(slot);
            rule.write(buf);
        });
        buf.writeUtf(nbt, MAX_NBT_LENGTH);
    }

    public static LoadoutPreset read(FriendlyByteBuf buf) {
        String name = buf.readUtf(MAX_NAME_LENGTH);
        int weight = buf.readVarInt();
        List<String> sources = buf.readList(target -> target.readUtf(64));
        List<String> excludedSources = buf.readList(target -> target.readUtf(64));
        Integer minDay = readNullableInt(buf);
        Integer maxDay = readNullableInt(buf);
        int slotCount = Math.min(buf.readVarInt(), EquipmentSlot.values().length);
        EnumMap<EquipmentSlot, LoadoutSlotRule> slots = new EnumMap<>(EquipmentSlot.class);
        for (int i = 0; i < slotCount; i++) {
            slots.put(buf.readEnum(EquipmentSlot.class), LoadoutSlotRule.read(buf));
        }
        return new LoadoutPreset(name, weight, sources, excludedSources, minDay, maxDay, slots,
                buf.readUtf(MAX_NBT_LENGTH));
    }

    private static void writeNullableInt(FriendlyByteBuf buf, Integer value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeVarInt(value);
    }

    private static Integer readNullableInt(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readVarInt() : null;
    }

    private static List<String> immutableSources(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }
}
