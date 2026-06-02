package com.mobspawnswitch.network;

import com.mobspawnswitch.Mobspawnswitch;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Mobspawnswitch.MODID, "main"),
            () -> PROTOCOL_VERSION,
            s -> true,
            s -> true
    );

    private NetworkHandler() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ServerboundToggleSpawnPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundToggleSpawnPacket::encode)
                .decoder(ServerboundToggleSpawnPacket::new)
                .consumerMainThread(ServerboundToggleSpawnPacket::handle)
                .add();

        CHANNEL.messageBuilder(ServerboundRequestRulesPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRequestRulesPacket::encode)
                .decoder(ServerboundRequestRulesPacket::new)
                .consumerMainThread(ServerboundRequestRulesPacket::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundSyncRulesPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundSyncRulesPacket::encode)
                .decoder(ClientboundSyncRulesPacket::new)
                .consumerMainThread(ClientboundSyncRulesPacket::handle)
                .add();
    }
}
