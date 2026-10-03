package com.mobspawncontroller.loadout;

import net.minecraft.network.FriendlyByteBuf;

/**
 * How one equipment slot changes when a loadout preset is applied.
 * {@code item} uses the same syntax as the item argument of {@code /give}, or a full item SNBT compound.
 */
public record LoadoutSlotRule(Mode mode, String item, int count, DropMode dropMode, float dropChance) {

    public static final int MAX_ITEM_LENGTH = 1024;
    public static final LoadoutSlotRule KEEP = new LoadoutSlotRule(Mode.KEEP, "", 1, DropMode.DEFAULT, 0.0F);

    public LoadoutSlotRule {
        mode = mode == null ? Mode.KEEP : mode;
        item = item == null ? "" : item.trim();
        if (item.length() > MAX_ITEM_LENGTH) item = item.substring(0, MAX_ITEM_LENGTH);
        count = Math.max(1, Math.min(64, count));
        dropMode = dropMode == null ? DropMode.DEFAULT : dropMode;
        dropChance = Float.isFinite(dropChance) ? Math.max(0.0F, Math.min(1.0F, dropChance)) : 0.0F;
        if (mode != Mode.SET) {
            item = "";
            count = 1;
        }
        if (mode == Mode.CLEAR) dropMode = DropMode.DEFAULT;
        if (dropMode != DropMode.CUSTOM) dropChance = 0.0F;
    }

    /** A keep rule with the default drop chance changes nothing and is omitted from storage. */
    public boolean isDefault() {
        return mode == Mode.KEEP && dropMode == DropMode.DEFAULT;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeUtf(item, MAX_ITEM_LENGTH);
        buf.writeVarInt(count);
        buf.writeEnum(dropMode);
        buf.writeFloat(dropChance);
    }

    public static LoadoutSlotRule read(FriendlyByteBuf buf) {
        return new LoadoutSlotRule(buf.readEnum(Mode.class), buf.readUtf(MAX_ITEM_LENGTH), buf.readVarInt(),
                buf.readEnum(DropMode.class), buf.readFloat());
    }

    public enum Mode {
        KEEP, CLEAR, SET;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Mode previous() {
            return values()[(ordinal() - 1 + values().length) % values().length];
        }
    }

    /** NEVER/CUSTOM/ALWAYS map to vanilla drop chances 0, 0..1 and 2.0 (guaranteed, undamaged). */
    public enum DropMode {
        DEFAULT, NEVER, CUSTOM, ALWAYS;

        public DropMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public DropMode previous() {
            return values()[(ordinal() - 1 + values().length) % values().length];
        }
    }
}
