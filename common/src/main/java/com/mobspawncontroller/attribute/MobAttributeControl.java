package com.mobspawncontroller.attribute;

import net.minecraft.resources.ResourceLocation;

public record MobAttributeControl(ResourceLocation id, String descriptionKey, String source,
                                  ControlType type, double value, double defaultValue,
                                  double minValue, double maxValue, boolean overridden) {

    public enum ControlType {
        BOOLEAN,
        NUMBER,
        PERCENT
    }
}
