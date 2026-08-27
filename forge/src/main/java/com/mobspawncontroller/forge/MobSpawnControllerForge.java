package com.mobspawncontroller.forge;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.active.ActiveSpawner;
import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.command.MobSpawnCommand;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.network.ClientboundSyncAttributesPayload;
import com.mobspawncontroller.network.ClientboundSyncRulesPayload;
import com.mobspawncontroller.network.ClientboundSyncStructuresPayload;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.network.ServerboundRequestStructuresPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundSetActiveSpawnPayload;
import com.mobspawncontroller.network.ServerboundSetNaturalSpawnPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.natural.SpawnInterception;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mobspawncontroller.platform.ModCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

@Mod(MobSpawnController.MOD_ID)
public final class MobSpawnControllerForge {

    private static final String PROTOCOL_VERSION = "10";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MobSpawnController.id("main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    public MobSpawnControllerForge(FMLJavaModLoadingContext loadingContext) {
        ModCompat.setModLoadedChecker(modId -> ModList.get().isLoaded(modId));
        SpawnInterception.setPlatformHandlesFinalizeSpawn(true);
        MobSpawnController.setConfigDirectory(FMLPaths.CONFIGDIR.get());
        MobSpawnController.init();
        NetworkBridge.setToPlayerSender(MobSpawnControllerForge::sendToPlayer);

        IEventBus modBus = loadingContext.getModEventBus();
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onSpawnPlacementCheck);
        MinecraftForge.EVENT_BUS.addListener(this::onFinalizeSpawn);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelTick);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        registerPayloads();
    }

    private static void registerPayloads() {
        int id = 0;
        CHANNEL.messageBuilder(ServerboundToggleSpawnPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundToggleSpawnPayload::write)
                .decoder(ServerboundToggleSpawnPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundToggleSpawnPayload::handle))
                .add();
        CHANNEL.messageBuilder(ServerboundRequestRulesPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRequestRulesPayload::write)
                .decoder(ServerboundRequestRulesPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundRequestRulesPayload::handle))
                .add();
        CHANNEL.messageBuilder(ServerboundRequestAttributesPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRequestAttributesPayload::write)
                .decoder(ServerboundRequestAttributesPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundRequestAttributesPayload::handle))
                .add();
        CHANNEL.messageBuilder(ServerboundSetAttributesPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundSetAttributesPayload::write)
                .decoder(ServerboundSetAttributesPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundSetAttributesPayload::handle))
                .add();
        CHANNEL.messageBuilder(ServerboundSetNaturalSpawnPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundSetNaturalSpawnPayload::write)
                .decoder(ServerboundSetNaturalSpawnPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundSetNaturalSpawnPayload::handle))
                .add();
        CHANNEL.messageBuilder(ServerboundSetActiveSpawnPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundSetActiveSpawnPayload::write)
                .decoder(ServerboundSetActiveSpawnPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundSetActiveSpawnPayload::handle))
                .add();
        CHANNEL.messageBuilder(ServerboundRequestStructuresPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRequestStructuresPayload::write)
                .decoder(ServerboundRequestStructuresPayload::read)
                .consumerMainThread((payload, context) -> handleServer(payload, context,
                        ServerboundRequestStructuresPayload::handle))
                .add();
        CHANNEL.messageBuilder(ClientboundSyncRulesPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundSyncRulesPayload::write)
                .decoder(ClientboundSyncRulesPayload::read)
                .consumerMainThread((payload, context) -> {
                    ClientRuleSync.handle(payload);
                    context.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(ClientboundSyncAttributesPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundSyncAttributesPayload::write)
                .decoder(ClientboundSyncAttributesPayload::read)
                .consumerMainThread((payload, context) -> {
                    ClientRuleSync.handle(payload);
                    context.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(ClientboundSyncStructuresPayload.class, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundSyncStructuresPayload::write)
                .decoder(ClientboundSyncStructuresPayload::read)
                .consumerMainThread((payload, context) -> {
                    ClientRuleSync.handle(payload);
                    context.get().setPacketHandled(true);
                })
                .add();
    }

    private static <T> void handleServer(T payload, Supplier<NetworkEvent.Context> context,
                                         ServerPayloadHandler<T> handler) {
        NetworkEvent.Context networkContext = context.get();
        handler.handle(payload, networkContext.getSender());
        networkContext.setPacketHandled(true);
    }

    private static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    private void onServerStarting(ServerStartingEvent event) {
        MobSpawnController.serverStarting();
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
            event.setResult(Event.Result.DENY);
        }
    }

    private void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (SpawnInterception.isPrechecked(event.getEntity())) {
            return;
        }
        if (!MobSpawnManager.isSpawnAllowed(event.getEntity(), event.getLevel(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
            return;
        }
        MobSpawnManager.applyAttributeOverrides(event.getEntity());
    }

    private void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            ActiveSpawner.tick(level);
        }
    }

    private interface ServerPayloadHandler<T> {
        void handle(T payload, ServerPlayer player);
    }
}
