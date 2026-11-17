package com.mobspawncontroller.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** Vanilla HUD and item art, so attribute icons also follow the player's resource pack. */
final class AttributeIcons {
    static final int SIZE = 16;
    static final int GAP = 6;
    private static final ResourceLocation HUD_ICONS = minecraft("textures/gui/icons.png");
    private static final Map<Item, ItemStack> ITEM_ICONS = new HashMap<>();

    private AttributeIcons() {
    }

    static void render(GuiGraphics graphics, ResourceLocation attribute, int x, int y) {
        String namespace = attribute.getNamespace();
        String path = attribute.getPath();
        if (namespace.equals("minecraft")) {
            if (path.equals("generic.max_health") || path.equals("generic.max_absorption")) {
                graphics.blit(HUD_ICONS, x + 3, y + 3, 16, 0, 9, 9);
                graphics.blit(HUD_ICONS, x + 3, y + 3,
                        path.equals("generic.max_health") ? 52 : 160, 0, 9, 9);
                return;
            }
            if (path.equals("generic.armor")) {
                graphics.blit(HUD_ICONS, x + 3, y + 3, 34, 9, 9, 9);
                return;
            }
        }

        Item item = namespace.equals("minecraft") ? vanillaItem(path)
                : namespace.equals("forge") || namespace.equals("neoforge") ? platformItem(path) : null;
        if (item != null) {
            graphics.renderItem(ITEM_ICONS.computeIfAbsent(item, ItemStack::new), x, y);
        }
    }

    private static Item vanillaItem(String path) {
        return switch (path) {
            case "generic.armor_toughness" -> Items.DIAMOND_CHESTPLATE;
            case "generic.attack_damage" -> Items.IRON_SWORD;
            case "generic.attack_speed" -> Items.GOLDEN_SWORD;
            case "generic.attack_knockback" -> Items.PISTON;
            case "generic.knockback_resistance", "generic.explosion_knockback_resistance" -> Items.SHIELD;
            case "generic.movement_speed", "generic.safe_fall_distance" -> Items.FEATHER;
            case "generic.flying_speed" -> Items.ELYTRA;
            case "generic.follow_range" -> Items.SPYGLASS;
            case "generic.luck" -> Items.RABBIT_FOOT;
            case "horse.jump_strength", "generic.jump_strength" -> Items.SLIME_BALL;
            case "generic.scale" -> Items.ARMOR_STAND;
            case "generic.gravity" -> Items.ANVIL;
            case "generic.step_height", "generic.movement_efficiency", "player.sneaking_speed" -> Items.LEATHER_BOOTS;
            case "generic.fall_damage_multiplier" -> Items.HAY_BLOCK;
            case "generic.oxygen_bonus" -> Items.TURTLE_HELMET;
            case "generic.water_movement_efficiency" -> Items.TROPICAL_FISH;
            case "generic.burning_time" -> Items.BLAZE_POWDER;
            case "player.block_break_speed", "player.mining_efficiency" -> Items.IRON_PICKAXE;
            case "player.block_interaction_range", "player.entity_interaction_range" -> Items.STICK;
            case "player.submerged_mining_speed" -> Items.PRISMARINE_SHARD;
            case "zombie.spawn_reinforcements" -> Items.ROTTEN_FLESH;
            default -> null;
        };
    }

    private static Item platformItem(String path) {
        return switch (path) {
            case "swim_speed" -> Items.TROPICAL_FISH;
            case "entity_gravity" -> Items.ANVIL;
            case "step_height_addition" -> Items.LEATHER_BOOTS;
            case "reach_distance" -> Items.STICK;
            case "attack_range" -> Items.TRIDENT;
            case "nametag_distance" -> Items.NAME_TAG;
            default -> null;
        };
    }

    private static ResourceLocation minecraft(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
