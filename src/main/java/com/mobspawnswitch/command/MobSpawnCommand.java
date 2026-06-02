package com.mobspawnswitch.command;

import com.mobspawnswitch.Mobspawnswitch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@Mod.EventBusSubscriber(modid = Mobspawnswitch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobSpawnCommand {

    private static final String ALL = "all";

    private static final SuggestionProvider<CommandSourceStack> MOB_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(
                ForgeRegistries.ENTITY_TYPES.getKeys().stream().map(ResourceLocation::toString),
                builder
        );
    };

    private static final SuggestionProvider<CommandSourceStack> SPAWN_TYPE_SUGGESTIONS = (context, builder) -> {
        List<String> types = new ArrayList<>();
        types.add(ALL);
        for (MobSpawnType type : MobSpawnType.values()) {
            types.add(type.name().toLowerCase(Locale.ROOT));
        }
        return SharedSuggestionProvider.suggest(types, builder);
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("mobspawnswitch")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(MobSpawnCommand::listAll)
                                .then(Commands.argument("mobId", ResourceLocationArgument.id())
                                        .suggests(MOB_SUGGESTIONS)
                                        .executes(MobSpawnCommand::listMob)))
                        .then(Commands.literal("clear")
                                .then(Commands.literal(ALL)
                                        .executes(MobSpawnCommand::clearAll))
                                .then(Commands.argument("mobId", ResourceLocationArgument.id())
                                        .suggests(MOB_SUGGESTIONS)
                                        .executes(MobSpawnCommand::clearMob)))
                        .then(Commands.argument("mobId", ResourceLocationArgument.id())
                                .suggests(MOB_SUGGESTIONS)
                                .then(Commands.argument("spawnType", StringArgumentType.word())
                                        .suggests(SPAWN_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("allowed", BoolArgumentType.bool())
                                                .executes(MobSpawnCommand::setSpawn)))));
        dispatcher.register(
                Commands.literal("mss")
                        .requires(src -> src.hasPermission(2))
                        .redirect(dispatcher.getRoot().getChild("mobspawnswitch")));
    }

    private static int setSpawn(CommandContext<CommandSourceStack> context) {
        ResourceLocation mobId;
        try {
            mobId = ResourceLocationArgument.getId(context, "mobId");
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Invalid mob id.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!ForgeRegistries.ENTITY_TYPES.containsKey(mobId)) {
            context.getSource().sendFailure(
                    Component.literal("Unknown mob: " + mobId).withStyle(ChatFormatting.RED));
            return 0;
        }

        String spawnTypeStr = StringArgumentType.getString(context, "spawnType");
        boolean allowed = BoolArgumentType.getBool(context, "allowed");

        if (ALL.equalsIgnoreCase(spawnTypeStr)) {
            MobSpawnManager.setAllAllowed(mobId, allowed);
            MobSpawnManager.save();
            final String msg = "Mob " + mobId + " [all] -> " + allowed;
            context.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        MobSpawnType type = parseSpawnType(spawnTypeStr);
        if (type == null) {
            context.getSource().sendFailure(Component.literal("Unknown spawn type: " + spawnTypeStr
                    + ". Valid: all, " + buildTypeList()).withStyle(ChatFormatting.RED));
            return 0;
        }

        MobSpawnManager.setAllowed(mobId, type, allowed);
        MobSpawnManager.save();
        final String key = type.name().toLowerCase(Locale.ROOT);
        final String msg = "Mob " + mobId + " [" + key + "] -> " + allowed;
        context.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> context) {
        Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> all = MobSpawnManager.getAllRules();
        if (all.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("No spawn rules set.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        all.forEach((mobId, map) -> sendMobRules(context.getSource(), mobId, map));
        return 1;
    }

    private static int listMob(CommandContext<CommandSourceStack> context) {
        ResourceLocation mobId = ResourceLocationArgument.getId(context, "mobId");
        Map<MobSpawnType, Boolean> rules = MobSpawnManager.getRules(mobId);
        if (rules == null || rules.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("No spawn rules for " + mobId).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        sendMobRules(context.getSource(), mobId, rules);
        return 1;
    }

    private static void sendMobRules(CommandSourceStack source, ResourceLocation mobId, Map<MobSpawnType, Boolean> rules) {
        source.sendSuccess(() -> {
            Component name = Component.literal(mobId.toString())
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/mobspawnswitch " + mobId + " "))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to edit"))));
            Component header = Component.literal("").append(name).append(Component.literal(":").withStyle(ChatFormatting.GRAY));
            return header;
        }, false);
        rules.forEach((type, allowed) -> {
            String typeName = type.name().toLowerCase(Locale.ROOT);
            ChatFormatting color = allowed ? ChatFormatting.GREEN : ChatFormatting.RED;
            source.sendSuccess(() -> Component.literal("  " + typeName + " = " + allowed).withStyle(color), false);
        });
    }

    private static int clearMob(CommandContext<CommandSourceStack> context) {
        ResourceLocation mobId = ResourceLocationArgument.getId(context, "mobId");
        MobSpawnManager.clear(mobId);
        MobSpawnManager.save();
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared rules for " + mobId).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> context) {
        MobSpawnManager.clearAll();
        MobSpawnManager.save();
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared all spawn rules.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static MobSpawnType parseSpawnType(String name) {
        for (MobSpawnType t : MobSpawnType.values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    private static String buildTypeList() {
        StringBuilder sb = new StringBuilder();
        for (MobSpawnType t : MobSpawnType.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
