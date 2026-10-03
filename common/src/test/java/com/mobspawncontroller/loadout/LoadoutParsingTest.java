package com.mobspawncontroller.loadout;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutParsingTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void parsesGiveSyntaxWithComponents() throws CommandSyntaxException {
        ItemStack stack = LoadoutParsing.parseItem("minecraft:iron_sword[damage=5]", registries);

        assertTrue(stack.is(Items.IRON_SWORD));
        assertEquals(1, stack.getCount());
        assertEquals(5, stack.getDamageValue());
    }

    @Test
    void parsesSavedItemCompoundAndIgnoresItsCount() throws CommandSyntaxException {
        ItemStack stack = LoadoutParsing.parseItem(
                "{id:\"minecraft:shield\",count:3,components:{\"minecraft:damage\":7}}", registries);

        assertTrue(stack.is(Items.SHIELD));
        assertEquals(1, stack.getCount());
        assertEquals(7, stack.getDamageValue());
    }

    @Test
    void rejectsUnknownAirAndTrailingInput() {
        assertThrows(CommandSyntaxException.class, () -> LoadoutParsing.parseItem("minecraft:nope", registries));
        assertThrows(CommandSyntaxException.class, () -> LoadoutParsing.parseItem("minecraft:air", registries));
        assertThrows(CommandSyntaxException.class,
                () -> LoadoutParsing.parseItem("minecraft:stone extra", registries));
        assertThrows(CommandSyntaxException.class, () -> LoadoutParsing.parseItem("{id:\"minecraft:air\"}", registries));
    }

    @Test
    void heldItemsConvertToGiveSyntaxThatParsesBack() throws CommandSyntaxException {
        ItemStack held = new ItemStack(Items.DIAMOND_SWORD, 2);
        held.set(DataComponents.CUSTOM_NAME, Component.literal("Blade"));
        held.setDamageValue(4);

        String text = LoadoutParsing.toItemString(held, registries);

        assertTrue(text.startsWith("minecraft:diamond_sword["), text);
        assertTrue(ItemStack.isSameItemSameComponents(held, LoadoutParsing.parseItem(text, registries)));
    }

    @Test
    void removedComponentsSurviveTheRoundTrip() throws CommandSyntaxException {
        ItemStack held = new ItemStack(Items.APPLE);
        held.remove(DataComponents.FOOD);

        String text = LoadoutParsing.toItemString(held, registries);

        assertEquals("minecraft:apple[!minecraft:food]", text);
        assertTrue(ItemStack.isSameItemSameComponents(held, LoadoutParsing.parseItem(text, registries)));
    }

    @Test
    void savedItemCompoundParsesToTheSameStack() throws CommandSyntaxException {
        ItemStack held = new ItemStack(Items.SHIELD);
        held.setDamageValue(9);
        String compound = held.save(registries).toString();

        assertTrue(ItemStack.isSameItemSameComponents(held, LoadoutParsing.parseItem(compound, registries)));
    }

    @Test
    void nbtMergeDropsIdentityAndPositionKeys() throws CommandSyntaxException {
        CompoundTag tag = LoadoutParsing.parseNbt("{UUID:[I;1,2,3,4],Pos:[0d,0d,0d],Glowing:1b}");

        assertEquals(java.util.List.of("Pos", "UUID"), LoadoutParsing.ignoredKeys(tag));
        CompoundTag cleaned = LoadoutParsing.withoutIgnoredKeys(tag);
        assertEquals(1, cleaned.size());
        assertTrue(cleaned.getBoolean("Glowing"));
    }
}
