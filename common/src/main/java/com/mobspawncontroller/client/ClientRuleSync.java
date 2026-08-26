package com.mobspawncontroller.client;

import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncStructuresPayload;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientRuleSync {

    private static List<String> cachedStructureEntries = List.of();
    private static List<String> cachedStructureTags = List.of();

    private ClientRuleSync() {
    }

    public static List<String> getCachedStructureEntries() {
        return cachedStructureEntries;
    }

    public static List<String> getCachedStructureTags() {
        return cachedStructureTags;
    }

    public static void handle(ClientboundSyncRulesPayload payload) {
        if (Minecraft.getInstance().screen instanceof Receiver receiver) {
            receiver.onRulesReceived(payload.rules());
            receiver.onAttributeModifiedMobsReceived(payload.attributeModifiedMobs());
            receiver.onNaturalSpawnSettingsReceived(payload.naturalSpawnSettings());
            receiver.onActiveSpawnSettingsReceived(payload.activeSpawnSettings());
        }
    }

    public static void handle(ClientboundSyncAttributesPayload payload) {
        if (Minecraft.getInstance().screen instanceof Receiver receiver) {
            receiver.onAttributesReceived(payload.mobId(), payload.controls());
        }
    }

    public static void handle(ClientboundSyncStructuresPayload payload) {
        cachedStructureEntries = List.copyOf(payload.entries());
        cachedStructureTags = List.copyOf(payload.tags());
        if (Minecraft.getInstance().screen instanceof Receiver receiver) {
            receiver.onStructuresReceived(payload.entries(), payload.tags());
        }
    }

    public interface Receiver {
        void onRulesReceived(java.util.Map<net.minecraft.resources.ResourceLocation,
                java.util.EnumMap<net.minecraft.world.entity.MobSpawnType, Boolean>> rules);

        default void onAttributeModifiedMobsReceived(java.util.Set<net.minecraft.resources.ResourceLocation> mobIds) {
        }

        default void onAttributesReceived(net.minecraft.resources.ResourceLocation mobId,
                                          java.util.List<com.mobspawncontroller.attribute.MobAttributeControl> controls) {
        }

        default void onNaturalSpawnSettingsReceived(java.util.Map<net.minecraft.resources.ResourceLocation,
                com.mobspawncontroller.natural.NaturalSpawnSettings> settings) {
        }

        default void onActiveSpawnSettingsReceived(java.util.Map<net.minecraft.resources.ResourceLocation,
                com.mobspawncontroller.active.ActiveSpawnSettings> settings) {
        }

        default void onStructuresReceived(List<String> entries, List<String> tags) {
        }
    }
}
