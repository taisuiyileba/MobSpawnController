package com.mobspawnswitch.network;

import com.mobspawnswitch.command.MobSpawnManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class ServerboundRequestRulesPacket {

    public ServerboundRequestRulesPacket() {
    }

    public ServerboundRequestRulesPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> allRules = MobSpawnManager.getAllRules();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ClientboundSyncRulesPacket(allRules)
        );
    }
}
