package com.mobspawncontroller.network;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.command.MobSpawnManager;
import com.mobspawncontroller.loadout.LoadoutParsing;
import com.mobspawncontroller.loadout.LoadoutPreset;
import com.mobspawncontroller.loadout.LoadoutSlotRule;
import com.mobspawncontroller.loadout.MobLoadout;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundSetLoadoutPayload(ResourceLocation mobId, MobLoadout loadout) {

    public static final ResourceLocation ID = MobSpawnController.id("set_loadout");

    public static ServerboundSetLoadoutPayload read(FriendlyByteBuf buf) {
        return new ServerboundSetLoadoutPayload(buf.readResourceLocation(), MobLoadout.read(buf));
    }

    public static void write(ServerboundSetLoadoutPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.mobId);
        payload.loadout.write(buf);
    }

    public static void handle(ServerboundSetLoadoutPayload payload, ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        Component error = validate(payload.loadout);
        if (error != null) {
            player.displayClientMessage(Component.translatable("gui.mobspawncontroller.loadout.error.rejected",
                    error).withStyle(ChatFormatting.RED), false);
        } else {
            MobSpawnManager.setLoadout(payload.mobId, payload.loadout);
            MobSpawnManager.save();
        }
        NetworkBridge.sendToPlayer(player, new ClientboundSyncLoadoutPayload(payload.mobId,
                MobSpawnManager.getLoadout(payload.mobId)));
        NetworkBridge.sendToPlayer(player, ClientboundSyncRulesPayload.current());
    }

    /** The GUI validates too, but the server re-parses so a stale or modified client cannot store broken data. */
    private static Component validate(MobLoadout loadout) {
        for (LoadoutPreset preset : loadout.presets()) {
            try {
                for (LoadoutSlotRule rule : preset.slots().values()) {
                    if (rule.mode() == LoadoutSlotRule.Mode.SET) LoadoutParsing.parseItem(rule.item());
                }
                LoadoutParsing.parseNbt(preset.nbt());
            } catch (CommandSyntaxException exception) {
                return Component.literal(preset.name() + ": ").append(LoadoutParsing.describeError(exception));
            }
        }
        return null;
    }
}
