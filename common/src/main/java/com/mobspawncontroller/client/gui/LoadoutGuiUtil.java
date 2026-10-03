package com.mobspawncontroller.client.gui;

import com.mobspawncontroller.loadout.LoadoutParsing;
import com.mobspawncontroller.loadout.LoadoutSources;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rendering and text helpers shared by the loadout tab and the preset editor. */
final class LoadoutGuiUtil {

    /** Parse result of an item string: either a stack or the syntax error to show. */
    record ItemCheck(ItemStack stack, Component error) {
        boolean valid() {
            return error == null;
        }
    }

    private static final int CHECK_CACHE_LIMIT = 128;
    private static final Map<String, ItemCheck> CHECK_CACHE = new HashMap<>();
    private static HolderLookup.Provider cacheRegistries;

    private LoadoutGuiUtil() {
    }

    /** The connected world's registries; item components such as enchantments resolve against them. */
    static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** GUI-thread cache so per-frame rendering does not re-parse item SNBT. */
    static ItemCheck checkItem(String text) {
        HolderLookup.Provider registries = registries();
        if (cacheRegistries != registries) {
            CHECK_CACHE.clear();
            cacheRegistries = registries;
        }
        String key = text == null ? "" : text.trim();
        ItemCheck cached = CHECK_CACHE.get(key);
        if (cached != null) return cached;
        if (CHECK_CACHE.size() > CHECK_CACHE_LIMIT) CHECK_CACHE.clear();
        ItemCheck check;
        if (key.isEmpty()) {
            check = new ItemCheck(ItemStack.EMPTY,
                    Component.translatable("gui.mobspawncontroller.loadout.error.item_required"));
        } else {
            try {
                check = new ItemCheck(LoadoutParsing.parseItem(key, registries), null);
            } catch (CommandSyntaxException exception) {
                check = new ItemCheck(ItemStack.EMPTY, LoadoutParsing.describeError(exception));
            } catch (RuntimeException exception) {
                check = new ItemCheck(ItemStack.EMPTY, Component.literal(String.valueOf(exception.getMessage())));
            }
        }
        CHECK_CACHE.put(key, check);
        return check;
    }

    static void renderEmptySlotIcon(GuiGraphics graphics, EquipmentSlot slot, int x, int y) {
        ResourceLocation icon = switch (slot) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            case OFFHAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            case MAINHAND, BODY -> null;
        };
        if (icon == null) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        graphics.blit(x, y, 0, 16, 16, sprite);
    }

    static Component slotName(EquipmentSlot slot) {
        return Component.translatable("gui.mobspawncontroller.loadout.slot." + slot.getName());
    }

    static Component sourceName(String source) {
        return source.equals(LoadoutSources.EXTRA_SPAWN)
                ? Component.translatable("gui.mobspawncontroller.loadout.source.extra_spawn")
                : Component.translatable("gui.mobspawncontroller.spawntype." + source);
    }

    /** Hover name plus up to two enchantments, e.g. "Iron Helmet \u00B7 Protection II". */
    static String describeStack(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        parts.add(stack.getHoverName().getString());
        stack.getEnchantments().entrySet().stream().limit(2)
                .forEach(entry -> parts.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString()));
        return String.join(" \u00B7 ", parts);
    }

    static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) return String.valueOf((long) Math.rint(value));
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
