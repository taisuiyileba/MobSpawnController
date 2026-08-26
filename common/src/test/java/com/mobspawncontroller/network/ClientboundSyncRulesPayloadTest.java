package com.mobspawncontroller.network;

import com.mobspawncontroller.active.ActiveSpawnSettings;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientboundSyncRulesPayloadTest {

    @Test
    void snapshotsMutableRuleStateBeforeDeferredEncoding() {
        ResourceLocation mobId = ResourceLocation.fromNamespaceAndPath("minecraft", "allay");
        EnumMap<MobSpawnType, Boolean> mobRules = new EnumMap<>(MobSpawnType.class);
        mobRules.put(MobSpawnType.NATURAL, false);
        Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules = new HashMap<>();
        rules.put(mobId, mobRules);
        Set<ResourceLocation> attributeMobs = new HashSet<>();
        attributeMobs.add(mobId);
        Map<ResourceLocation, ActiveSpawnSettings> activeSettings = new HashMap<>();
        activeSettings.put(mobId, ActiveSpawnSettings.defaults());

        ClientboundSyncRulesPayload payload = new ClientboundSyncRulesPayload(
                rules, attributeMobs, Map.of(), activeSettings);

        mobRules.put(MobSpawnType.COMMAND, false);
        rules.clear();
        attributeMobs.clear();
        activeSettings.clear();

        assertTrue(payload.rules().containsKey(mobId));
        assertFalse(payload.rules().get(mobId).containsKey(MobSpawnType.COMMAND));
        assertTrue(payload.attributeModifiedMobs().contains(mobId));
        assertTrue(payload.activeSpawnSettings().containsKey(mobId));

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundSyncRulesPayload.STREAM_CODEC.encode(buffer, payload);
            ClientboundSyncRulesPayload decoded = ClientboundSyncRulesPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
        } finally {
            buffer.release();
        }
    }
}
