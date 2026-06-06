package com.mobspawncontroller.neoforge;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.command.MobSpawnCommand;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(MobSpawnController.MOD_ID)
public final class MobSpawnControllerNeoForge {

    public MobSpawnControllerNeoForge(IEventBus modBus) {
        MobSpawnController.init();
        NetworkBridge.setSender(new NeoForgePacketSender());

        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(this::onPositionCheck);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MobSpawnControllerNeoForgeClient.init();
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MobSpawnController.MOD_ID).versioned("1").optional();
        registrar.playToServer(ServerboundToggleSpawnPayload.TYPE, ServerboundToggleSpawnPayload.STREAM_CODEC,
                (payload, context) -> ServerboundToggleSpawnPayload.handle(payload, (ServerPlayer) context.player()));
        registrar.playToServer(ServerboundRequestRulesPayload.TYPE, ServerboundRequestRulesPayload.STREAM_CODEC,
                (payload, context) -> ServerboundRequestRulesPayload.handle(payload, (ServerPlayer) context.player()));
        registrar.playToServer(ServerboundRequestAttributesPayload.TYPE, ServerboundRequestAttributesPayload.STREAM_CODEC,
                (payload, context) -> ServerboundRequestAttributesPayload.handle(payload, (ServerPlayer) context.player()));
        registrar.playToServer(ServerboundSetAttributesPayload.TYPE, ServerboundSetAttributesPayload.STREAM_CODEC,
                (payload, context) -> ServerboundSetAttributesPayload.handle(payload, (ServerPlayer) context.player()));
        registrar.playToClient(ClientboundSyncRulesPayload.TYPE, ClientboundSyncRulesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientRuleSync.handle(payload)));
        registrar.playToClient(ClientboundSyncAttributesPayload.TYPE, ClientboundSyncAttributesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientRuleSync.handle(payload)));
    }

    private void onServerStarting(ServerStartingEvent event) {
        MobSpawnController.serverStarting(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        MobSpawnController.serverStopping();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MobSpawnCommand.register(event.getDispatcher());
    }

    private void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType());
        MobSpawnType type = event.getSpawnType();
        Boolean allowed = MobSpawnManager.getAllowed(mobId, type);
        if (allowed != null && !allowed) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        MobSpawnType type = event.getSpawnType();
        Boolean allowed = MobSpawnManager.getAllowed(mobId, type);
        if (allowed != null && !allowed) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    private static final class NeoForgePacketSender implements NetworkBridge.PacketSender {
        @Override
        public void sendToServer(CustomPacketPayload payload) {
            PacketDistributor.sendToServer(payload);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
