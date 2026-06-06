package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.attribute.MobAttributeControl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSyncAttributesPayload(ResourceLocation mobId, List<MobAttributeControl> controls) {

    public static final ResourceLocation ID = MobSpawnController.id("sync_attributes");

    public static ClientboundSyncAttributesPayload read(FriendlyByteBuf buf) {
        ResourceLocation mobId = buf.readResourceLocation();
        int size = buf.readVarInt();
        List<MobAttributeControl> controls = new ArrayList<>(size);
        MobAttributeControl.ControlType[] types = MobAttributeControl.ControlType.values();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String descriptionKey = buf.readUtf(256);
            String source = buf.readUtf(64);
            int typeOrdinal = buf.readVarInt();
            MobAttributeControl.ControlType type = typeOrdinal >= 0 && typeOrdinal < types.length
                    ? types[typeOrdinal] : MobAttributeControl.ControlType.NUMBER;
            double value = buf.readDouble();
            double defaultValue = buf.readDouble();
            double minValue = buf.readDouble();
            double maxValue = buf.readDouble();
            boolean overridden = buf.readBoolean();
            controls.add(new MobAttributeControl(id, descriptionKey, source, type, value, defaultValue,
                    minValue, maxValue, overridden));
        }
        return new ClientboundSyncAttributesPayload(mobId, controls);
    }

    public static void write(ClientboundSyncAttributesPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.mobId);
        buf.writeVarInt(payload.controls.size());
        for (MobAttributeControl control : payload.controls) {
            buf.writeResourceLocation(control.id());
            buf.writeUtf(control.descriptionKey(), 256);
            buf.writeUtf(control.source(), 64);
            buf.writeVarInt(control.type().ordinal());
            buf.writeDouble(control.value());
            buf.writeDouble(control.defaultValue());
            buf.writeDouble(control.minValue());
            buf.writeDouble(control.maxValue());
            buf.writeBoolean(control.overridden());
        }
    }
}
