package com.mobspawncontroller.loadout;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** Per-mob loadout: the chance to apply any preset, then one preset picked by weight. */
public record MobLoadout(double chance, List<LoadoutPreset> presets) {

    public static final int MAX_PRESETS = 8;

    public MobLoadout {
        chance = Double.isFinite(chance) ? Math.max(0.0, Math.min(1.0, chance)) : 1.0;
        presets = presets == null ? List.of()
                : List.copyOf(presets.subList(0, Math.min(MAX_PRESETS, presets.size())));
    }

    public static MobLoadout defaults() {
        return new MobLoadout(1.0, List.of());
    }

    /** Without presets nothing can be applied, so the chance alone is not worth storing. */
    public boolean isDefault() {
        return presets.isEmpty();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(chance);
        buf.writeVarInt(presets.size());
        presets.forEach(preset -> preset.write(buf));
    }

    public static MobLoadout read(FriendlyByteBuf buf) {
        double chance = buf.readDouble();
        int size = Math.min(buf.readVarInt(), MAX_PRESETS);
        LoadoutPreset[] presets = new LoadoutPreset[size];
        for (int i = 0; i < size; i++) {
            presets[i] = LoadoutPreset.read(buf);
        }
        return new MobLoadout(chance, List.of(presets));
    }
}
