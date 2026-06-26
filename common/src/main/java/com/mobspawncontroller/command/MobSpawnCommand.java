package com.mobspawncontroller.command;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobSpawnCommand {

    private static final String ALL = "all";

    private static final SuggestionProvider<CommandSourceStack> MOB_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> SPAWN_TYPE_SUGGESTIONS = (context, builder) -> {
        List<String> types = new ArrayList<>();
        types.add(ALL);
        for (MobSpawnType type : MobSpawnType.values()) {
            types.add(type.name().toLowerCase(Locale.ROOT));
        }
        return SharedSuggestionProvider.suggest(types, builder);
    };

    private MobSpawnCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("mobspawncontroller")
                        .requires(source -> source.hasPermission(2))
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
                Commands.literal("msc")
                        .requires(source -> source.hasPermission(2))
                        .redirect(dispatcher.getRoot().getChild("mobspawncontroller")));
    }

    private static int setSpawn(CommandContext<CommandSourceStack> context) {
        ResourceLocation mobId = ResourceLocationArgument.getId(context, "mobId");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(mobId)) {
            context.getSource().sendFailure(Component.literal("Unknown mob: " + mobId).withStyle(ChatFormatting.RED));
            return 0;
        }

        String spawnTypeStr = StringArgumentType.getString(context, "spawnType");
        boolean allowed = BoolArgumentType.getBool(context, "allowed");

        if (ALL.equalsIgnoreCase(spawnTypeStr)) {
            MobSpawnManager.setAllAllowed(mobId, allowed);
            MobSpawnManager.save();
            context.getSource().sendSuccess(
                    () -> Component.literal("Mob " + mobId + " [all] -> " + allowed).withStyle(ChatFormatting.GREEN),
                    true);
            return 1;
        }

        MobSpawnType type = MobSpawnManager.parseSpawnType(spawnTypeStr);
        if (type == null) {
            context.getSource().sendFailure(Component.literal("Unknown spawn type: " + spawnTypeStr
                    + ". Valid: all, " + buildTypeList()).withStyle(ChatFormatting.RED));
            return 0;
        }

        MobSpawnManager.setAllowed(mobId, type, allowed);
        MobSpawnManager.save();
        String key = type.name().toLowerCase(Locale.ROOT);
        context.getSource().sendSuccess(
                () -> Component.literal("Mob " + mobId + " [" + key + "] -> " + allowed).withStyle(ChatFormatting.GREEN),
                true);
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
                                    "/mobspawncontroller " + mobId + " "))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to edit"))));
            return Component.literal("").append(name).append(Component.literal(":").withStyle(ChatFormatting.GRAY));
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

    private static String buildTypeList() {
        StringBuilder builder = new StringBuilder();
        for (MobSpawnType type : MobSpawnType.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(type.name().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }
}
