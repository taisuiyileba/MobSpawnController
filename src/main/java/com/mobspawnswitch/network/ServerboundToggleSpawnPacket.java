package com.mobspawnswitch.network;

import com.mobspawnswitch.command.MobSpawnManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class ServerboundToggleSpawnPacket {

    private final ResourceLocation mobId;
    private final String spawnType;
    private final boolean allowed;

    public ServerboundToggleSpawnPacket(ResourceLocation mobId, String spawnType, boolean allowed) {
        this.mobId = mobId;
        this.spawnType = spawnType;
        this.allowed = allowed;
    }

    public ServerboundToggleSpawnPacket(FriendlyByteBuf buf) {
        this.mobId = buf.readResourceLocation();
        this.spawnType = buf.readUtf(64);
        this.allowed = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(mobId);
        buf.writeUtf(spawnType, 64);
        buf.writeBoolean(allowed);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (player == null || !player.hasPermissions(2)) {
            return;
        }

        if ("all".equalsIgnoreCase(spawnType)) {
            MobSpawnManager.setAllAllowed(mobId, allowed);
        } else {
            MobSpawnType type = parseSpawnType(spawnType);
            if (type != null) {
                MobSpawnManager.setAllowed(mobId, type, allowed);
            }
        }
        MobSpawnManager.save();

        Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> allRules = MobSpawnManager.getAllRules();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ClientboundSyncRulesPacket(allRules)
        );
    }

    private static MobSpawnType parseSpawnType(String name) {
        for (MobSpawnType t : MobSpawnType.values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}
