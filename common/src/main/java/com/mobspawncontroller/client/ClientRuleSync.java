package com.mobspawncontroller.client;

import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import net.minecraft.client.Minecraft;

public final class ClientRuleSync {

    private ClientRuleSync() {
    }

    public static void handle(ClientboundSyncRulesPayload payload) {
        if (Minecraft.getInstance().screen instanceof Receiver receiver) {
            receiver.onRulesReceived(payload.rules());
            receiver.onAttributeModifiedMobsReceived(payload.attributeModifiedMobs());
        }
    }

    public static void handle(ClientboundSyncAttributesPayload payload) {
        if (Minecraft.getInstance().screen instanceof Receiver receiver) {
            receiver.onAttributesReceived(payload.mobId(), payload.controls());
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
    }
}
