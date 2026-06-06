package com.mobspawncontroller.attribute;

import com.mobspawncontroller.command.MobSpawnManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MobAttributeDiscovery {

    private MobAttributeDiscovery() {
    }

    public static List<MobAttributeControl> discover(ServerLevel level, ResourceLocation mobId) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);
        Entity entity = entityType.create(level);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return List.of();
        }

        AttributeMap attributeMap = livingEntity.getAttributes();
        List<MobAttributeControl> controls = new ArrayList<>();
        BuiltInRegistries.ATTRIBUTE.entrySet().forEach(entry ->
                BuiltInRegistries.ATTRIBUTE.getHolder(entry.getKey()).ifPresent(holder -> {
                    if (attributeMap.hasAttribute(holder)) {
                        controls.add(createControl(mobId, entry.getKey().location(), holder, attributeMap));
                    }
                }));

        controls.sort(Comparator.comparing(control -> control.id().toString()));
        return controls;
    }

    private static MobAttributeControl createControl(ResourceLocation mobId, ResourceLocation id, Holder<Attribute> holder,
                                                    AttributeMap attributeMap) {
        Attribute attribute = holder.value();
        AttributeInstance instance = attributeMap.getInstance(holder);
        double value = instance != null ? instance.getValue() : attributeMap.getValue(holder);
        double baseValue = instance != null ? instance.getBaseValue() : attributeMap.getBaseValue(holder);
        Double overrideValue = MobSpawnManager.getAttributeOverride(mobId, id);
        double defaultValue = attribute.getDefaultValue();
        double minValue = 0.0;
        double maxValue = Math.max(1.0, Math.max(defaultValue * 2.0, value * 2.0));

        if (attribute instanceof RangedAttribute rangedAttribute) {
            minValue = rangedAttribute.getMinValue();
            maxValue = rangedAttribute.getMaxValue();
        }

        return new MobAttributeControl(id, attribute.getDescriptionId(), id.getNamespace(),
                controlType(attribute), overrideValue != null ? overrideValue : baseValue,
                defaultValue, minValue, maxValue, overrideValue != null);
    }

    private static MobAttributeControl.ControlType controlType(Attribute attribute) {
        String className = attribute.getClass().getName();
        if (className.endsWith(".BooleanAttribute")) {
            return MobAttributeControl.ControlType.BOOLEAN;
        }
        if (className.endsWith(".PercentageAttribute")) {
            return MobAttributeControl.ControlType.PERCENT;
        }
        return MobAttributeControl.ControlType.NUMBER;
    }
}
