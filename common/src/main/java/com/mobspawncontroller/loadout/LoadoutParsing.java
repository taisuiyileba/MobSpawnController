package com.mobspawncontroller.loadout;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses loadout item strings and entity SNBT; shared by the GUI validation and the server.
 * Item components reference data-driven registries such as enchantments, so item parsing always needs the
 * registries of the side doing the parsing.
 */
public final class LoadoutParsing {

    /** Keys that would move, duplicate or re-identify the entity; they are dropped before merging. */
    public static final Set<String> IGNORED_NBT_KEYS = Set.of("UUID", "Pos", "Motion", "Rotation", "id",
            "Passengers");

    private static final DynamicCommandExceptionType ERROR_INVALID_ITEM = new DynamicCommandExceptionType(
            message -> Component.translatable("gui.mobspawncontroller.loadout.error.invalid_item", message));
    private static final SimpleCommandExceptionType ERROR_EMPTY_ITEM = new SimpleCommandExceptionType(
            Component.translatable("gui.mobspawncontroller.loadout.error.empty_item"));
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, Optional<ItemStack>> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<CompoundTag>> NBT_CACHE = new ConcurrentHashMap<>();
    private static volatile HolderLookup.Provider cacheRegistries;

    private LoadoutParsing() {
    }

    /**
     * Accepts {@code /give} item syntax ({@code minecraft:iron_sword[damage=5]}) or a saved item compound
     * ({@code {id:"minecraft:iron_sword",components:{...}}}). The count is taken from the slot rule instead.
     */
    public static ItemStack parseItem(String text, HolderLookup.Provider registries) throws CommandSyntaxException {
        String trimmed = text.trim();
        ItemStack stack;
        if (trimmed.startsWith("{")) {
            CompoundTag tag = TagParser.parseTag(trimmed);
            DataResult<ItemStack> result = ItemStack.CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE), tag);
            Optional<ItemStack> parsed = result.result();
            if (parsed.isEmpty()) {
                throw ERROR_INVALID_ITEM.create(result.error().map(DataResult.Error::message).orElse(trimmed));
            }
            stack = parsed.get().copyWithCount(1);
        } else {
            StringReader reader = new StringReader(trimmed);
            ItemParser.ItemResult result = new ItemParser(registries).parse(reader);
            if (reader.canRead()) throw TagParser.ERROR_TRAILING_DATA.createWithContext(reader);
            stack = new ItemStack(result.item(), 1, result.components());
        }
        if (stack.isEmpty()) throw ERROR_EMPTY_ITEM.create();
        return stack;
    }

    /** Returns an empty compound for blank text. */
    public static CompoundTag parseNbt(String text) throws CommandSyntaxException {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? new CompoundTag() : TagParser.parseTag(trimmed);
    }

    /** Cached template for hot paths; callers must copy it. Invalid text yields {@link ItemStack#EMPTY}. */
    public static ItemStack cachedItem(String text, HolderLookup.Provider registries) {
        if (text == null || text.isBlank()) return ItemStack.EMPTY;
        // Templates hold registry holders, so they must not outlive the registries they were parsed with.
        if (cacheRegistries != registries) {
            ITEM_CACHE.clear();
            cacheRegistries = registries;
        }
        Optional<ItemStack> cached = ITEM_CACHE.get(text);
        if (cached == null) {
            if (ITEM_CACHE.size() > CACHE_LIMIT) ITEM_CACHE.clear();
            try {
                cached = Optional.of(parseItem(text, registries));
            } catch (CommandSyntaxException | RuntimeException exception) {
                cached = Optional.empty();
            }
            ITEM_CACHE.put(text, cached);
        }
        return cached.orElse(ItemStack.EMPTY);
    }

    /** Cached compound for hot paths; callers must not modify it. Blank or invalid text yields null. */
    public static CompoundTag cachedNbt(String text) {
        if (text == null || text.isBlank()) return null;
        Optional<CompoundTag> cached = NBT_CACHE.get(text);
        if (cached == null) {
            if (NBT_CACHE.size() > CACHE_LIMIT) NBT_CACHE.clear();
            try {
                cached = Optional.of(withoutIgnoredKeys(parseNbt(text)));
            } catch (CommandSyntaxException | RuntimeException exception) {
                cached = Optional.empty();
            }
            NBT_CACHE.put(text, cached);
        }
        return cached.orElse(null);
    }

    public static CompoundTag withoutIgnoredKeys(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        IGNORED_NBT_KEYS.forEach(copy::remove);
        return copy;
    }

    public static List<String> ignoredKeys(CompoundTag tag) {
        return tag.getAllKeys().stream().filter(IGNORED_NBT_KEYS::contains).sorted().toList();
    }

    /**
     * Converts an existing stack into {@code /give} item syntax. The result is re-parsed, and the lossless
     * saved-compound form is used instead whenever the string does not reproduce the stack exactly.
     */
    public static String toItemString(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return "";
        String give = new ItemInput(stack.getItemHolder(), stack.getComponentsPatch()).serialize(registries);
        try {
            if (ItemStack.isSameItemSameComponents(parseItem(give, registries), stack)) return give;
        } catch (CommandSyntaxException | RuntimeException ignored) {
        }
        return stack.copyWithCount(1).save(registries).toString();
    }

    /** A readable message that keeps the {@code <--[HERE]} context of a syntax error. */
    public static MutableComponent describeError(CommandSyntaxException exception) {
        MutableComponent message = Component.empty().append(ComponentUtils.fromMessage(exception.getRawMessage()));
        String context = exception.getContext();
        return context == null ? message : message.append(": " + context);
    }

    public static void clearCache() {
        ITEM_CACHE.clear();
        NBT_CACHE.clear();
    }
}
