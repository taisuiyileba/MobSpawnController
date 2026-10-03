package com.mobspawncontroller.loadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobLoadoutJsonCodec {

    private MobLoadoutJsonCodec() {
    }

    public static JsonObject encode(MobLoadout loadout) {
        JsonObject json = new JsonObject();
        if (loadout.chance() != 1.0) json.addProperty("chance", loadout.chance());
        JsonArray presets = new JsonArray();
        loadout.presets().forEach(preset -> presets.add(encodePreset(preset)));
        json.add("presets", presets);
        return json;
    }

    public static MobLoadout decode(JsonObject json) {
        double chance = getDouble(json, "chance", 1.0);
        List<LoadoutPreset> presets = new ArrayList<>();
        if (json.has("presets") && json.get("presets").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("presets")) {
                if (element.isJsonObject()) presets.add(decodePreset(element.getAsJsonObject()));
            }
        }
        return new MobLoadout(chance, presets);
    }

    private static JsonObject encodePreset(LoadoutPreset preset) {
        JsonObject json = new JsonObject();
        if (!preset.name().isEmpty()) json.addProperty("name", preset.name());
        json.addProperty("weight", preset.weight());
        addStringList(json, "sources", preset.sources());
        addStringList(json, "excluded_sources", preset.excludedSources());
        if (preset.minDay() != null) json.addProperty("min_day", preset.minDay());
        if (preset.maxDay() != null) json.addProperty("max_day", preset.maxDay());
        JsonObject slots = new JsonObject();
        for (EquipmentSlot slot : LoadoutPreset.SLOT_ORDER) {
            LoadoutSlotRule rule = preset.slots().get(slot);
            if (rule != null) slots.add(slot.getName(), encodeSlot(rule));
        }
        if (slots.size() > 0) json.add("slots", slots);
        if (!preset.nbt().isEmpty()) json.addProperty("nbt", preset.nbt());
        return json;
    }

    private static LoadoutPreset decodePreset(JsonObject json) {
        Map<EquipmentSlot, LoadoutSlotRule> slots = new EnumMap<>(EquipmentSlot.class);
        if (json.has("slots") && json.get("slots").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("slots").entrySet()) {
                EquipmentSlot slot = parseSlot(entry.getKey());
                if (slot != null && entry.getValue().isJsonObject()) {
                    slots.put(slot, decodeSlot(entry.getValue().getAsJsonObject()));
                }
            }
        }
        return new LoadoutPreset(getString(json, "name", ""), (int) getDouble(json, "weight", 1.0),
                getStringList(json, "sources"), getStringList(json, "excluded_sources"),
                getNullableInt(json, "min_day"), getNullableInt(json, "max_day"), slots,
                getString(json, "nbt", ""));
    }

    private static JsonObject encodeSlot(LoadoutSlotRule rule) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", rule.mode().name().toLowerCase(Locale.ROOT));
        if (rule.mode() == LoadoutSlotRule.Mode.SET) {
            json.addProperty("item", rule.item());
            if (rule.count() != 1) json.addProperty("count", rule.count());
        }
        switch (rule.dropMode()) {
            case NEVER -> json.addProperty("drop_chance", "never");
            case ALWAYS -> json.addProperty("drop_chance", "always");
            case CUSTOM -> json.addProperty("drop_chance", rule.dropChance());
            case DEFAULT -> {
            }
        }
        return json;
    }

    private static LoadoutSlotRule decodeSlot(JsonObject json) {
        LoadoutSlotRule.Mode mode = LoadoutSlotRule.Mode.KEEP;
        try {
            mode = LoadoutSlotRule.Mode.valueOf(getString(json, "mode", "keep").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        LoadoutSlotRule.DropMode dropMode = LoadoutSlotRule.DropMode.DEFAULT;
        float dropChance = 0.0F;
        if (json.has("drop_chance") && json.get("drop_chance").isJsonPrimitive()) {
            JsonPrimitive drop = json.getAsJsonPrimitive("drop_chance");
            if (drop.isNumber()) {
                dropMode = LoadoutSlotRule.DropMode.CUSTOM;
                dropChance = drop.getAsFloat();
            } else if (drop.getAsString().equalsIgnoreCase("never")) {
                dropMode = LoadoutSlotRule.DropMode.NEVER;
            } else if (drop.getAsString().equalsIgnoreCase("always")) {
                dropMode = LoadoutSlotRule.DropMode.ALWAYS;
            }
        }
        return new LoadoutSlotRule(mode, getString(json, "item", ""), (int) getDouble(json, "count", 1.0),
                dropMode, dropChance);
    }

    private static EquipmentSlot parseSlot(String name) {
        try {
            return EquipmentSlot.byName(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void addStringList(JsonObject json, String key, List<String> values) {
        if (values.isEmpty()) return;
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        json.add(key, array);
    }

    private static List<String> getStringList(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                if (element.isJsonPrimitive()) values.add(element.getAsString());
            }
        }
        return values;
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber()
                ? json.get(key).getAsDouble() : fallback;
    }

    private static Integer getNullableInt(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber()
                ? json.get(key).getAsInt() : null;
    }
}
