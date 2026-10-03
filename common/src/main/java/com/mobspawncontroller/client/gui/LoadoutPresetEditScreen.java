package com.mobspawncontroller.client.gui;

import com.mobspawncontroller.loadout.LoadoutParsing;
import com.mobspawncontroller.loadout.LoadoutPreset;
import com.mobspawncontroller.loadout.LoadoutSlotRule;
import com.mobspawncontroller.loadout.LoadoutSources;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Edits one loadout preset: name/weight/conditions, six equipment slots with a live preview, and entity NBT. */
public class LoadoutPresetEditScreen extends Screen {

    private static final int PANEL_BG = 0xF015171B;
    private static final int ACCENT_COLOR = 0xFF63B3ED;
    private static final int ERROR_COLOR = 0xFFEF4444;
    private static final int OK_COLOR = 0xFF22C55E;
    private static final int WARN_COLOR = 0xFFFBBF24;
    private static final int MUTED_COLOR = 0xFF94A3B8;
    private static final int LABEL_COLOR = 0xFFE5E7EB;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int PREVIEW_W = 64;
    private static final int CONTROL_H = 16;
    private static final int SOURCE_MODE_W = 48;
    private static final int SOURCE_PICKER_W = 92;
    private static final int PAGE_TAB_W = 56;
    private static final int DROP_CYCLE_W = 84;
    private static final List<NbtToggle> NBT_TOGGLES = List.of(
            new NbtToggle("PersistenceRequired", "persistent"),
            new NbtToggle("Glowing", "glowing"),
            new NbtToggle("Silent", "silent"),
            new NbtToggle("NoAI", "no_ai"),
            new NbtToggle("Invulnerable", "invulnerable"));
    private static final NbtToggle NAME_VISIBLE_TOGGLE = new NbtToggle("CustomNameVisible", "name_visible");

    private record NbtToggle(String key, String label) {
    }

    private record ToggleBox(NbtToggle toggle, int x, int y, int width) {
    }

    private enum Page {
        EQUIPMENT,
        NBT
    }

    private static final class SlotState {
        private LoadoutSlotRule.Mode mode;
        private String item;
        private String count;
        private LoadoutSlotRule.DropMode dropMode;
        private String dropPercent;
    }

    private final Screen parent;
    private final EntityType<?> entityType;
    private final Consumer<LoadoutPreset> onDone;
    private final EnumMap<EquipmentSlot, SlotState> slots = new EnumMap<>(EquipmentSlot.class);
    private final List<ToggleBox> toggleBoxes = new ArrayList<>();
    private String name;
    private String weight;
    private String minDay;
    private String maxDay;
    private boolean sourceBlacklist;
    private List<String> sourceSelection;
    private String nbtText;
    private CompoundTag nbtParsed;
    private Component nbtError;
    private boolean syncingNbt;
    private Page page = Page.EQUIPMENT;
    private EquipmentSlot selectedSlot = EquipmentSlot.HEAD;
    private LivingEntity previewEntity;
    private boolean previewFailed;
    private Component statusText = Component.empty();
    private int statusColor = MUTED_COLOR;

    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int contentTop;
    private int contentBottom;
    private int rowAY;
    private int rowBY;
    private int nameLabelX;
    private int weightLabelX;
    private int dayLabelX;
    private int dayTildeX;
    private int sourceModeX;
    private int sourcePickerX;
    private int pageTabX;
    private int slotsX;
    private int slotsY;
    private int previewX;
    private int previewH;
    private int handsY;
    private int detailX;
    private int detailRight;
    private int controlX;
    private int dropLabelX;
    private int dropCycleX;
    private int nameRowY;
    private int snbtLabelY;

    private EditBox nameBox;
    private EditBox weightBox;
    private EditBox minDayBox;
    private EditBox maxDayBox;
    private EditBox itemBox;
    private EditBox countBox;
    private EditBox dropBox;
    private EditBox customNameBox;
    private MultiLineEditBox nbtBox;
    private Button heldButton;
    private Button slotItemButton;
    private Button importAllButton;

    public LoadoutPresetEditScreen(Screen parent, EntityType<?> entityType, Component mobName, LoadoutPreset preset,
                                   Consumer<LoadoutPreset> onDone) {
        super(Component.translatable("gui.mobspawncontroller.loadout.editor.title", mobName));
        this.parent = parent;
        this.entityType = entityType;
        this.onDone = onDone;
        this.name = preset.name();
        this.weight = String.valueOf(preset.weight());
        this.minDay = preset.minDay() == null ? "" : String.valueOf(preset.minDay());
        this.maxDay = preset.maxDay() == null ? "" : String.valueOf(preset.maxDay());
        this.sourceBlacklist = preset.sources().isEmpty() && !preset.excludedSources().isEmpty();
        this.sourceSelection = new ArrayList<>(sourceBlacklist ? preset.excludedSources() : preset.sources());
        for (EquipmentSlot slot : LoadoutPreset.SLOT_ORDER) {
            LoadoutSlotRule rule = preset.slot(slot);
            SlotState state = new SlotState();
            state.mode = rule.mode();
            state.item = rule.item();
            state.count = String.valueOf(rule.count());
            state.dropMode = rule.dropMode();
            state.dropPercent = rule.dropMode() == LoadoutSlotRule.DropMode.CUSTOM
                    ? LoadoutGuiUtil.formatNumber(rule.dropChance() * 100.0) : "";
            slots.put(slot, state);
        }
        this.nbtText = preset.nbt();
        reparseNbt();
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(340, Math.min(width - 24, 470));
        int panelHeight = Math.max(236, Math.min(height - 16, 300));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = (height - panelHeight) / 2;
        panelBottom = panelTop + panelHeight;
        contentTop = panelTop + 68;
        contentBottom = panelBottom - 30;
        rowAY = panelTop + 22;
        rowBY = panelTop + 44;

        int x = panelLeft + 10;
        nameLabelX = x;
        x += font.width(text("name")) + 4;
        nameBox = addBox(x, rowAY, 96, LoadoutPreset.MAX_NAME_LENGTH, name, value -> true, value -> name = value);
        x += 96 + 10;
        weightLabelX = x;
        x += font.width(text("weight")) + 4;
        weightBox = addBox(x, rowAY, 32, 4, weight, LoadoutPresetEditScreen::isDigits, value -> weight = value);
        x += 32 + 10;
        dayLabelX = x;
        x += font.width(text("day_range")) + 4;
        minDayBox = addBox(x, rowAY, 40, 9, minDay, LoadoutPresetEditScreen::isDigits, value -> minDay = value);
        x += 40;
        dayTildeX = x + 5;
        x += 10;
        maxDayBox = addBox(x, rowAY, 40, 9, maxDay, LoadoutPresetEditScreen::isDigits, value -> maxDay = value);

        sourceModeX = panelLeft + 10 + font.width(text("sources")) + 4;
        sourcePickerX = sourceModeX + SOURCE_MODE_W + 4;
        pageTabX = panelRight - 10 - PAGE_TAB_W * 2 - 4;

        initEquipmentPage();
        initNbtPage();

        int buttonY = panelBottom - 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.cancel"),
                        button -> onClose())
                .bounds(panelRight - 10 - 56 * 2 - 4, buttonY, 56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.loadout.editor.done"),
                        button -> finish())
                .bounds(panelRight - 10 - 56, buttonY, 56, 18).build());
        applyVisibility();
    }

    private void initEquipmentPage() {
        slotsX = panelLeft + 12;
        slotsY = contentTop + 4;
        previewX = slotsX + SLOT_SIZE + 6;
        previewH = 4 * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        handsY = slotsY + 4 * (SLOT_SIZE + SLOT_GAP) + 2;
        detailX = previewX + PREVIEW_W + 12;
        detailRight = panelRight - 10;
        int labelWidth = 0;
        for (String key : List.of("mode", "item", "drop")) {
            labelWidth = Math.max(labelWidth, font.width(text(key)));
        }
        controlX = detailX + labelWidth + 6;

        SlotState state = slots.get(selectedSlot);
        int countWidth = 26;
        int itemWidth = detailRight - controlX - countWidth - 12;
        itemBox = addBox(controlX, contentTop + 38, itemWidth, LoadoutSlotRule.MAX_ITEM_LENGTH, state.item,
                value -> true, value -> slots.get(selectedSlot).item = value);
        countBox = addBox(detailRight - countWidth, contentTop + 38, countWidth, 2, state.count,
                LoadoutPresetEditScreen::isDigits, value -> slots.get(selectedSlot).count = value);
        int halfWidth = (detailRight - controlX - 4) / 2;
        heldButton = addRenderableWidget(Button.builder(text("use_held"), button -> importHeldItem())
                .bounds(controlX, contentTop + 58, halfWidth, CONTROL_H).build());
        slotItemButton = addRenderableWidget(Button.builder(text("use_worn"), button -> importWornItem())
                .bounds(controlX + halfWidth + 4, contentTop + 58, halfWidth, CONTROL_H).build());
        dropLabelX = detailX;
        dropCycleX = controlX;
        dropBox = addBox(dropCycleX + DROP_CYCLE_W + 4, contentTop + 78, 32, 6, state.dropPercent,
                LoadoutPresetEditScreen::isDecimal, value -> slots.get(selectedSlot).dropPercent = value);
        importAllButton = addRenderableWidget(Button.builder(text("import_all"), button -> importAllWorn())
                .bounds(slotsX, handsY + SLOT_SIZE + 6, SLOT_SIZE + 6 + PREVIEW_W, CONTROL_H).build());
    }

    private void initNbtPage() {
        nameRowY = contentTop + 16;
        int nameX = panelLeft + 12 + font.width(text("custom_name")) + 4;
        customNameBox = addBox(nameX, nameRowY, 110, 64, nbtParsed == null ? "" : customNameOf(nbtParsed),
                value -> true, this::onCustomNameChanged);

        toggleBoxes.clear();
        toggleBoxes.add(new ToggleBox(NAME_VISIBLE_TOGGLE, nameX + 110 + 10, nameRowY + 3,
                toggleWidth(NAME_VISIBLE_TOGGLE)));
        int x = panelLeft + 12;
        int y = nameRowY + 22;
        for (NbtToggle toggle : NBT_TOGGLES) {
            int toggleWidth = toggleWidth(toggle);
            if (x + toggleWidth > panelRight - 10 && x > panelLeft + 12) {
                x = panelLeft + 12;
                y += 14;
            }
            toggleBoxes.add(new ToggleBox(toggle, x, y, toggleWidth));
            x += toggleWidth + 8;
        }
        snbtLabelY = y + 16;
        int boxTop = snbtLabelY + 11;
        nbtBox = new MultiLineEditBox(font, panelLeft + 12, boxTop, panelRight - panelLeft - 24,
                Math.max(28, contentBottom - 14 - boxTop), text("snbt_placeholder"), text("snbt")) {
            // Vanilla still seeks the cursor on clicks while hidden, which would steal clicks on the other page.
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return visible && super.mouseClicked(mouseX, mouseY, button);
            }
        };
        nbtBox.setCharacterLimit(LoadoutPreset.MAX_NBT_LENGTH);
        nbtBox.setValue(nbtText);
        nbtBox.setValueListener(this::onNbtTextChanged);
        addRenderableWidget(nbtBox);
    }

    private EditBox addBox(int x, int y, int width, int maxLength, String value, Predicate<String> filter,
                           Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, width, CONTROL_H, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setFilter(filter);
        box.setResponder(responder);
        return addRenderableWidget(box);
    }

    private int toggleWidth(NbtToggle toggle) {
        return 14 + font.width(text("nbt." + toggle.label()));
    }

    private void applyVisibility() {
        boolean equipment = page == Page.EQUIPMENT;
        SlotState state = slots.get(selectedSlot);
        boolean set = state.mode == LoadoutSlotRule.Mode.SET;
        itemBox.setVisible(equipment && set);
        countBox.setVisible(equipment && set);
        heldButton.visible = equipment && set;
        slotItemButton.visible = equipment && set;
        dropBox.setVisible(equipment && state.mode != LoadoutSlotRule.Mode.CLEAR
                && state.dropMode == LoadoutSlotRule.DropMode.CUSTOM);
        importAllButton.visible = equipment;
        customNameBox.setVisible(!equipment);
        nbtBox.visible = !equipment;
        if (getFocused() instanceof AbstractWidget widget && !widget.visible) {
            setFocused(null);
        }
    }

    /** Selecting a slot first, then pushing its values, keeps the responders writing to the right slot. */
    private void selectSlot(EquipmentSlot slot) {
        selectedSlot = slot;
        SlotState state = slots.get(slot);
        itemBox.setValue(state.item);
        countBox.setValue(state.count);
        dropBox.setValue(state.dropPercent);
        applyVisibility();
    }

    private void importHeldItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) importStack(selectedSlot, mc.player.getMainHandItem(), "held_empty");
    }

    private void importWornItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) importStack(selectedSlot, mc.player.getItemBySlot(selectedSlot), "worn_empty");
    }

    private void importAllWorn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int imported = 0;
        for (EquipmentSlot slot : LoadoutPreset.SLOT_ORDER) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty() && fitsItemLength(stack)) {
                applyStack(slot, stack);
                imported++;
            }
        }
        selectSlot(selectedSlot);
        setStatus(imported > 0 ? Component.translatable("gui.mobspawncontroller.loadout.status.imported", imported)
                : text("status.nothing_worn"), imported > 0 ? OK_COLOR : ERROR_COLOR);
    }

    private void importStack(EquipmentSlot slot, ItemStack stack, String emptyKey) {
        if (stack.isEmpty()) {
            setStatus(text("status." + emptyKey), ERROR_COLOR);
            return;
        }
        if (!fitsItemLength(stack)) {
            setStatus(text("status.item_too_long"), ERROR_COLOR);
            return;
        }
        applyStack(slot, stack);
        selectSlot(slot);
        setStatus(Component.translatable("gui.mobspawncontroller.loadout.status.item_set",
                LoadoutGuiUtil.slotName(slot), stack.getHoverName()), OK_COLOR);
    }

    private static boolean fitsItemLength(ItemStack stack) {
        return LoadoutParsing.toItemString(stack).length() <= LoadoutSlotRule.MAX_ITEM_LENGTH;
    }

    private void applyStack(EquipmentSlot slot, ItemStack stack) {
        SlotState state = slots.get(slot);
        state.mode = LoadoutSlotRule.Mode.SET;
        state.item = LoadoutParsing.toItemString(stack);
        state.count = String.valueOf(stack.getCount());
    }

    private void cycleSlotMode(int direction) {
        SlotState state = slots.get(selectedSlot);
        state.mode = direction < 0 ? state.mode.previous() : state.mode.next();
        applyVisibility();
    }

    private void cycleDropMode(int direction) {
        SlotState state = slots.get(selectedSlot);
        state.dropMode = direction < 0 ? state.dropMode.previous() : state.dropMode.next();
        if (state.dropMode == LoadoutSlotRule.DropMode.CUSTOM && state.dropPercent.isBlank()) {
            dropBox.setValue("8.5");
        }
        applyVisibility();
    }

    private void toggleSourceMode() {
        sourceBlacklist = !sourceBlacklist;
    }

    private void openSourcePicker() {
        List<NaturalRegistryPickerScreen.Option> options = new ArrayList<>();
        for (String source : LoadoutSources.all()) {
            options.add(new NaturalRegistryPickerScreen.Option(source, LoadoutGuiUtil.sourceName(source).getString()));
        }
        Minecraft.getInstance().setScreen(new NaturalRegistryPickerScreen(this, text("sources_title"), options,
                sourceSelection, selected -> sourceSelection = new ArrayList<>(selected)));
    }

    private void setPage(Page newPage) {
        page = newPage;
        applyVisibility();
    }

    private void onNbtTextChanged(String text) {
        nbtText = text;
        reparseNbt();
        if (!syncingNbt && nbtParsed != null && customNameBox != null) {
            String fromTag = customNameOf(nbtParsed);
            if (!customNameBox.getValue().equals(fromTag)) {
                syncingNbt = true;
                customNameBox.setValue(fromTag);
                syncingNbt = false;
            }
        }
    }

    private void reparseNbt() {
        try {
            nbtParsed = LoadoutParsing.parseNbt(nbtText);
            nbtError = null;
        } catch (CommandSyntaxException exception) {
            nbtParsed = null;
            nbtError = LoadoutParsing.describeError(exception);
        }
    }

    private void onCustomNameChanged(String value) {
        if (syncingNbt) return;
        if (nbtParsed == null) {
            setStatus(text("status.fix_snbt_first"), ERROR_COLOR);
            return;
        }
        CompoundTag tag = nbtParsed.copy();
        if (value.isBlank()) {
            tag.remove("CustomName");
        } else {
            tag.putString("CustomName", Component.Serializer.toJson(Component.literal(value)));
        }
        writeNbt(tag);
    }

    private void toggleNbtFlag(NbtToggle toggle) {
        if (nbtParsed == null) {
            setStatus(text("status.fix_snbt_first"), ERROR_COLOR);
            return;
        }
        CompoundTag tag = nbtParsed.copy();
        if (tag.getBoolean(toggle.key())) {
            tag.remove(toggle.key());
        } else {
            tag.putBoolean(toggle.key(), true);
        }
        writeNbt(tag);
    }

    /** The SNBT text stays the single source of truth; quick options only rewrite it. */
    private void writeNbt(CompoundTag tag) {
        String text = tag.isEmpty() ? "" : tag.toString();
        if (text.length() > LoadoutPreset.MAX_NBT_LENGTH) {
            setStatus(text("status.nbt_too_long"), ERROR_COLOR);
            return;
        }
        syncingNbt = true;
        nbtBox.setValue(text);
        syncingNbt = false;
        nbtText = text;
        reparseNbt();
    }

    private static String customNameOf(CompoundTag tag) {
        if (!tag.contains("CustomName", Tag.TAG_STRING)) return "";
        String json = tag.getString("CustomName");
        try {
            Component component = Component.Serializer.fromJson(json);
            return component == null ? "" : component.getString();
        } catch (RuntimeException exception) {
            return json;
        }
    }

    private void finish() {
        Integer parsedWeight = parseInt(weight, 1, LoadoutPreset.MAX_WEIGHT, 1);
        if (parsedWeight == null) {
            setStatus(text("error.weight"), ERROR_COLOR);
            return;
        }
        Integer parsedMinDay = parseInt(minDay, 0, Integer.MAX_VALUE, null);
        Integer parsedMaxDay = parseInt(maxDay, 0, Integer.MAX_VALUE, null);
        if ((!minDay.isBlank() && parsedMinDay == null) || (!maxDay.isBlank() && parsedMaxDay == null)
                || (parsedMinDay != null && parsedMaxDay != null && parsedMinDay > parsedMaxDay)) {
            setStatus(text("error.day_range"), ERROR_COLOR);
            return;
        }

        EnumMap<EquipmentSlot, LoadoutSlotRule> rules = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : LoadoutPreset.SLOT_ORDER) {
            SlotState state = slots.get(slot);
            Integer count = parseInt(state.count, 1, 64, 1);
            Double dropPercent = parseDecimal(state.dropPercent);
            Component error = null;
            if (state.mode == LoadoutSlotRule.Mode.SET) {
                LoadoutGuiUtil.ItemCheck check = LoadoutGuiUtil.checkItem(state.item);
                if (!check.valid()) error = check.error();
                else if (count == null) error = text("error.count");
            }
            if (error == null && state.mode != LoadoutSlotRule.Mode.CLEAR
                    && state.dropMode == LoadoutSlotRule.DropMode.CUSTOM
                    && (dropPercent == null || dropPercent < 0.0 || dropPercent > 100.0)) {
                error = text("error.drop_chance");
            }
            if (error != null) {
                setPage(Page.EQUIPMENT);
                selectSlot(slot);
                setStatus(LoadoutGuiUtil.slotName(slot).copy().append(": ").append(error), ERROR_COLOR);
                return;
            }
            rules.put(slot, new LoadoutSlotRule(state.mode, state.item, count == null ? 1 : count, state.dropMode,
                    dropPercent == null ? 0.0F : (float) (dropPercent / 100.0)));
        }
        if (nbtError != null) {
            setPage(Page.NBT);
            setStatus(Component.translatable("gui.mobspawncontroller.loadout.error.nbt", nbtError), ERROR_COLOR);
            return;
        }

        onDone.accept(new LoadoutPreset(name, parsedWeight,
                sourceBlacklist ? List.of() : sourceSelection, sourceBlacklist ? sourceSelection : List.of(),
                parsedMinDay, parsedMaxDay, rules, nbtText));
        Minecraft.getInstance().setScreen(parent);
    }

    private static Integer parseInt(String value, int min, int max, Integer blankValue) {
        if (value == null || value.isBlank()) return blankValue;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean isDigits(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private static boolean isDecimal(String value) {
        return value.chars().allMatch(chr -> Character.isDigit(chr) || chr == '.');
    }

    private void setStatus(Component text, int color) {
        statusText = text;
        statusColor = color;
    }

    private static Component text(String key) {
        return Component.translatable("gui.mobspawncontroller.loadout.editor." + key);
    }

    @Override
    public void tick() {
        for (EditBox box : List.of(nameBox, weightBox, minDayBox, maxDayBox, itemBox, countBox, dropBox,
                customNameBox)) {
            box.tick();
        }
        nbtBox.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderPanel(graphics);
        graphics.drawCenteredString(font, title, (panelLeft + panelRight) / 2, panelTop + 7, 0xFFFFFFFF);

        int labelY = rowAY + 4;
        graphics.drawString(font, text("name"), nameLabelX, labelY, LABEL_COLOR);
        graphics.drawString(font, text("weight"), weightLabelX, labelY, LABEL_COLOR);
        graphics.drawString(font, text("day_range"), dayLabelX, labelY, LABEL_COLOR);
        graphics.drawCenteredString(font, "~", dayTildeX, labelY, MUTED_COLOR);
        renderSourceRow(graphics, mouseX, mouseY);
        renderPageTabs(graphics, mouseX, mouseY);
        graphics.fill(panelLeft + 8, contentTop - 4, panelRight - 8, contentTop - 3, 0xFF303742);
        graphics.fill(panelLeft + 8, contentBottom + 2, panelRight - 8, contentBottom + 3, 0xFF303742);

        if (page == Page.EQUIPMENT) {
            renderEquipmentPage(graphics, mouseX, mouseY);
        } else {
            renderNbtPage(graphics, mouseX, mouseY);
        }

        int statusWidth = panelRight - 10 - 56 * 2 - 12 - (panelLeft + 10);
        List<FormattedCharSequence> statusLines = font.split(statusText, statusWidth);
        for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
            graphics.drawString(font, statusLines.get(i), panelLeft + 10,
                    panelBottom - 24 + i * 10 - (statusLines.size() > 1 ? 1 : -4), statusColor);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (page == Page.EQUIPMENT) {
            renderItemBoxOutline(graphics);
            renderSlotTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderPanel(GuiGraphics graphics) {
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);
        graphics.renderOutline(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF4B5563);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + 3, panelBottom - 1, 0x66F472B6);
    }

    private void renderSourceRow(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, text("sources"), panelLeft + 10, rowBY + 4, LABEL_COLOR);
        boolean modeHovered = inside(mouseX, mouseY, sourceModeX, rowBY, SOURCE_MODE_W, CONTROL_H);
        int modeColor = sourceBlacklist ? 0xFF7F1D1D : 0xFF166534;
        graphics.fill(sourceModeX, rowBY, sourceModeX + SOURCE_MODE_W, rowBY + CONTROL_H,
                modeHovered ? brighten(modeColor) : modeColor);
        graphics.renderOutline(sourceModeX, rowBY, SOURCE_MODE_W, CONTROL_H,
                sourceBlacklist ? 0xFFFCA5A5 : 0xFF86EFAC);
        graphics.drawCenteredString(font, Component.translatable("gui.mobspawncontroller.natural.option."
                + (sourceBlacklist ? "blacklist" : "whitelist")), sourceModeX + SOURCE_MODE_W / 2, rowBY + 4,
                0xFFFFFFFF);

        boolean pickerHovered = inside(mouseX, mouseY, sourcePickerX, rowBY, SOURCE_PICKER_W, CONTROL_H);
        graphics.fill(sourcePickerX, rowBY, sourcePickerX + SOURCE_PICKER_W, rowBY + CONTROL_H, 0xFF111827);
        graphics.renderOutline(sourcePickerX, rowBY, SOURCE_PICKER_W, CONTROL_H,
                pickerHovered ? ACCENT_COLOR : 0xFF374151);
        Component label = sourceSelection.isEmpty() && !sourceBlacklist ? text("all_sources")
                : Component.translatable("gui.mobspawncontroller.natural.selected_count", sourceSelection.size());
        graphics.drawString(font, trim(label.getString(), SOURCE_PICKER_W - 16), sourcePickerX + 4, rowBY + 4,
                LABEL_COLOR);
        graphics.drawString(font, ">", sourcePickerX + SOURCE_PICKER_W - 10, rowBY + 4, 0xFF7DD3FC);
    }

    private void renderPageTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Page tab : Page.values()) {
            int x = pageTabX + tab.ordinal() * (PAGE_TAB_W + 4);
            boolean active = page == tab;
            boolean hovered = inside(mouseX, mouseY, x, rowBY, PAGE_TAB_W, CONTROL_H);
            graphics.fill(x, rowBY, x + PAGE_TAB_W, rowBY + CONTROL_H,
                    active ? 0xFF263445 : hovered ? 0xFF202936 : 0xCC111827);
            graphics.fill(x, rowBY + CONTROL_H - 1, x + PAGE_TAB_W, rowBY + CONTROL_H,
                    active ? ACCENT_COLOR : 0xFF374151);
            graphics.drawCenteredString(font, text("page." + tab.name().toLowerCase(Locale.ROOT)),
                    x + PAGE_TAB_W / 2, rowBY + 4, active ? 0xFFFFFFFF : 0xFFB6C2D0);
        }
    }

    private void renderEquipmentPage(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < 4; i++) {
            renderSlot(graphics, LoadoutPreset.SLOT_ORDER.get(i), slotsX, slotsY + i * (SLOT_SIZE + SLOT_GAP),
                    mouseX, mouseY);
        }
        renderSlot(graphics, EquipmentSlot.MAINHAND, slotsX, handsY, mouseX, mouseY);
        renderSlot(graphics, EquipmentSlot.OFFHAND, previewX, handsY, mouseX, mouseY);
        graphics.drawString(font, trim(text("hands").getString(), PREVIEW_W - SLOT_SIZE - 4),
                previewX + SLOT_SIZE + 4, handsY + 6, MUTED_COLOR);
        renderPreview(graphics, mouseX, mouseY);

        SlotState state = slots.get(selectedSlot);
        graphics.drawString(font, LoadoutGuiUtil.slotName(selectedSlot), detailX, contentTop + 4, 0xFF7DD3FC);
        graphics.drawString(font, text("mode"), detailX, contentTop + 22, LABEL_COLOR);
        renderCycle(graphics, controlX, contentTop + 18, detailRight - controlX,
                text("mode." + state.mode.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);

        if (state.mode == LoadoutSlotRule.Mode.SET) {
            graphics.drawString(font, text("item"), detailX, contentTop + 42, LABEL_COLOR);
            graphics.drawString(font, "\u00D7", countBox.getX() - 8, contentTop + 42, MUTED_COLOR);
        } else {
            List<FormattedCharSequence> hint = font.split(text("mode_hint." + state.mode.name()
                    .toLowerCase(Locale.ROOT)), detailRight - detailX);
            for (int i = 0; i < Math.min(3, hint.size()); i++) {
                graphics.drawString(font, hint.get(i), detailX, contentTop + 40 + i * 10, MUTED_COLOR);
            }
        }

        if (state.mode != LoadoutSlotRule.Mode.CLEAR) {
            graphics.drawString(font, text("drop"), dropLabelX, contentTop + 82, LABEL_COLOR);
            renderCycle(graphics, dropCycleX, contentTop + 78, DROP_CYCLE_W,
                    text("drop." + state.dropMode.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
            if (state.dropMode == LoadoutSlotRule.DropMode.CUSTOM) {
                graphics.drawString(font, "%", dropBox.getX() + dropBox.getWidth() + 3, contentTop + 82,
                        MUTED_COLOR);
            }
        }

        if (state.mode == LoadoutSlotRule.Mode.SET) {
            LoadoutGuiUtil.ItemCheck check = LoadoutGuiUtil.checkItem(state.item);
            Component line = check.valid()
                    ? Component.literal("\u2714 " + LoadoutGuiUtil.describeStack(check.stack()))
                    : Component.literal("\u2716 ").append(check.error());
            List<FormattedCharSequence> lines = font.split(line, detailRight - detailX);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), detailX, contentTop + 100 + i * 10,
                        check.valid() ? OK_COLOR : ERROR_COLOR);
            }
        }
    }

    private void renderSlot(GuiGraphics graphics, EquipmentSlot slot, int x, int y, int mouseX, int mouseY) {
        SlotState state = slots.get(slot);
        boolean hovered = inside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hovered ? 0xFF1E293B : 0xFF111827);
        int border = 0xFF374151;
        switch (state.mode) {
            case KEEP -> LoadoutGuiUtil.renderEmptySlotIcon(graphics, slot, x + 2, y + 2);
            case CLEAR -> {
                border = 0xFFB91C1C;
                graphics.drawCenteredString(font, "\u2716", x + SLOT_SIZE / 2, y + 6, 0xFFFCA5A5);
            }
            case SET -> {
                LoadoutGuiUtil.ItemCheck check = LoadoutGuiUtil.checkItem(state.item);
                if (check.valid()) {
                    border = 0xFF15803D;
                    Integer count = parseInt(state.count, 1, 64, 1);
                    ItemStack stack = check.stack().copyWithCount(count == null ? 1 : count);
                    graphics.renderItem(stack, x + 2, y + 2);
                    graphics.renderItemDecorations(font, stack, x + 2, y + 2);
                } else {
                    border = ERROR_COLOR;
                    graphics.drawCenteredString(font, "?", x + SLOT_SIZE / 2, y + 6, 0xFFFCA5A5);
                }
            }
        }
        graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, slot == selectedSlot ? ACCENT_COLOR : border);
        if (slot == selectedSlot) {
            graphics.renderOutline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, ACCENT_COLOR);
        }
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = slotsY;
        graphics.fill(previewX, top, previewX + PREVIEW_W, top + previewH, 0xFF0B1220);
        graphics.renderOutline(previewX, top, PREVIEW_W, previewH, 0xFF303742);
        LivingEntity entity = previewEntity();
        if (entity == null) {
            graphics.drawCenteredString(font, text("no_preview"), previewX + PREVIEW_W / 2, top + previewH / 2 - 4,
                    MUTED_COLOR);
            return;
        }
        syncPreviewEquipment(entity);
        float size = Math.max(entity.getBbHeight(), entity.getBbWidth());
        int scale = (int) Math.max(4, Math.min(36, (previewH - 14) / Math.max(0.5F, size)));
        int centerX = previewX + PREVIEW_W / 2;
        int feetY = top + previewH - 6;
        graphics.enableScissor(previewX + 1, top + 1, previewX + PREVIEW_W - 1, top + previewH - 1);
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, centerX, feetY, scale,
                    centerX - mouseX, feetY - previewH / 2.0F - mouseY, entity);
        } catch (RuntimeException exception) {
            previewFailed = true;
            previewEntity = null;
        } finally {
            graphics.disableScissor();
        }
    }

    private LivingEntity previewEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (previewEntity == null && !previewFailed && entityType != null && mc.level != null) {
            try {
                Entity entity = entityType.create(mc.level);
                if (entity instanceof LivingEntity living) {
                    previewEntity = living;
                } else {
                    previewFailed = true;
                }
            } catch (RuntimeException exception) {
                previewFailed = true;
            }
        }
        return previewEntity;
    }

    /** Keep-slots show empty because vanilla equipment is only rolled on the server at spawn time. */
    private void syncPreviewEquipment(LivingEntity entity) {
        for (EquipmentSlot slot : LoadoutPreset.SLOT_ORDER) {
            SlotState state = slots.get(slot);
            ItemStack desired = state.mode == LoadoutSlotRule.Mode.SET
                    ? LoadoutGuiUtil.checkItem(state.item).stack() : ItemStack.EMPTY;
            if (!ItemStack.isSameItemSameTags(entity.getItemBySlot(slot), desired)) {
                entity.setItemSlot(slot, desired.copy());
            }
        }
    }

    private void renderItemBoxOutline(GuiGraphics graphics) {
        if (!itemBox.isVisible()) return;
        boolean valid = LoadoutGuiUtil.checkItem(itemBox.getValue()).valid();
        graphics.renderOutline(itemBox.getX() - 1, itemBox.getY() - 1, itemBox.getWidth() + 2,
                itemBox.getHeight() + 2, valid ? 0xFF15803D : ERROR_COLOR);
    }

    private void renderSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        EquipmentSlot slot = slotAt(mouseX, mouseY);
        if (slot == null) return;
        SlotState state = slots.get(slot);
        if (state.mode == LoadoutSlotRule.Mode.SET) {
            LoadoutGuiUtil.ItemCheck check = LoadoutGuiUtil.checkItem(state.item);
            if (check.valid()) {
                graphics.renderTooltip(font, check.stack(), mouseX, mouseY);
                return;
            }
        }
        List<Component> lines = List.of(LoadoutGuiUtil.slotName(slot),
                text("mode." + state.mode.name().toLowerCase(Locale.ROOT)));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderNbtPage(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, text("quick_options"), panelLeft + 12, contentTop + 2, MUTED_COLOR);
        graphics.drawString(font, text("custom_name"), panelLeft + 12, nameRowY + 4, LABEL_COLOR);
        for (ToggleBox box : toggleBoxes) {
            renderToggle(graphics, box, mouseX, mouseY);
        }
        graphics.drawString(font, text("snbt"), panelLeft + 12, snbtLabelY, MUTED_COLOR);

        Component status;
        int color;
        if (nbtError != null) {
            status = Component.literal("\u2716 ").append(nbtError);
            color = ERROR_COLOR;
        } else if (nbtParsed == null || nbtParsed.isEmpty()) {
            status = text("nbt_status.empty");
            color = MUTED_COLOR;
        } else {
            List<String> warnings = new ArrayList<>();
            List<String> ignored = LoadoutParsing.ignoredKeys(nbtParsed);
            if (!ignored.isEmpty()) {
                warnings.add(Component.translatable("gui.mobspawncontroller.loadout.editor.nbt_status.ignored",
                        String.join(", ", ignored)).getString());
            }
            if (nbtParsed.contains("Attributes")) warnings.add(text("nbt_status.attributes").getString());
            if (warnings.isEmpty()) {
                status = Component.translatable("gui.mobspawncontroller.loadout.editor.nbt_status.valid",
                        nbtParsed.size());
                color = OK_COLOR;
            } else {
                status = Component.literal("\u26A0 " + String.join(" ", warnings));
                color = WARN_COLOR;
            }
        }
        graphics.drawString(font, trim(status.getString(), panelRight - panelLeft - 24), panelLeft + 12,
                contentBottom - 9, color);
    }

    private void renderToggle(GuiGraphics graphics, ToggleBox box, int mouseX, int mouseY) {
        boolean enabled = nbtParsed != null;
        boolean checked = enabled && nbtParsed.getBoolean(box.toggle().key());
        boolean hovered = enabled && inside(mouseX, mouseY, box.x(), box.y(), box.width(), 10);
        graphics.fill(box.x(), box.y(), box.x() + 10, box.y() + 10, 0xFF111827);
        graphics.renderOutline(box.x(), box.y(), 10, 10, hovered ? ACCENT_COLOR : checked ? 0xFF86EFAC : 0xFF4B5563);
        if (checked) graphics.fill(box.x() + 2, box.y() + 2, box.x() + 8, box.y() + 8, 0xFF22C55E);
        graphics.drawString(font, text("nbt." + box.toggle().label()), box.x() + 14, box.y() + 1,
                enabled ? LABEL_COLOR : 0xFF6B7280);
    }

    private void renderCycle(GuiGraphics graphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        int arrowW = 14;
        boolean hovered = inside(mouseX, mouseY, x, y, width, CONTROL_H);
        boolean leftHovered = hovered && mouseX < x + arrowW;
        boolean rightHovered = hovered && mouseX >= x + width - arrowW;
        graphics.fill(x, y, x + width, y + CONTROL_H, 0xFF111827);
        graphics.renderOutline(x, y, width, CONTROL_H, hovered ? 0xFF64748B : 0xFF374151);
        graphics.fill(x, y, x + arrowW, y + CONTROL_H, leftHovered ? 0xFF5A9CC0 : 0xFF4A7C9B);
        graphics.fill(x + width - arrowW, y, x + width, y + CONTROL_H, rightHovered ? 0xFF5A9CC0 : 0xFF4A7C9B);
        graphics.drawCenteredString(font, "<", x + arrowW / 2, y + 4, 0xFFFFFFFF);
        graphics.drawCenteredString(font, ">", x + width - arrowW / 2, y + 4, 0xFFFFFFFF);
        graphics.drawCenteredString(font, trim(label.getString(), width - arrowW * 2 - 4), x + width / 2, y + 4,
                LABEL_COLOR);
    }

    private EquipmentSlot slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < 4; i++) {
            if (inside(mouseX, mouseY, slotsX, slotsY + i * (SLOT_SIZE + SLOT_GAP), SLOT_SIZE, SLOT_SIZE)) {
                return LoadoutPreset.SLOT_ORDER.get(i);
            }
        }
        if (inside(mouseX, mouseY, slotsX, handsY, SLOT_SIZE, SLOT_SIZE)) return EquipmentSlot.MAINHAND;
        if (inside(mouseX, mouseY, previewX, handsY, SLOT_SIZE, SLOT_SIZE)) return EquipmentSlot.OFFHAND;
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        setFocused(null);
        if (button != 0) return false;

        if (inside(mouseX, mouseY, sourceModeX, rowBY, SOURCE_MODE_W, CONTROL_H)) {
            toggleSourceMode();
            return true;
        }
        if (inside(mouseX, mouseY, sourcePickerX, rowBY, SOURCE_PICKER_W, CONTROL_H)) {
            openSourcePicker();
            return true;
        }
        for (Page tab : Page.values()) {
            if (inside(mouseX, mouseY, pageTabX + tab.ordinal() * (PAGE_TAB_W + 4), rowBY, PAGE_TAB_W, CONTROL_H)) {
                setPage(tab);
                return true;
            }
        }

        if (page == Page.EQUIPMENT) {
            EquipmentSlot slot = slotAt(mouseX, mouseY);
            if (slot != null) {
                selectSlot(slot);
                return true;
            }
            int modeWidth = detailRight - controlX;
            if (inside(mouseX, mouseY, controlX, contentTop + 18, modeWidth, CONTROL_H)) {
                cycleSlotMode(mouseX < controlX + modeWidth / 2.0 ? -1 : 1);
                return true;
            }
            SlotState state = slots.get(selectedSlot);
            if (state.mode != LoadoutSlotRule.Mode.CLEAR
                    && inside(mouseX, mouseY, dropCycleX, contentTop + 78, DROP_CYCLE_W, CONTROL_H)) {
                cycleDropMode(mouseX < dropCycleX + DROP_CYCLE_W / 2.0 ? -1 : 1);
                return true;
            }
        } else {
            for (ToggleBox box : toggleBoxes) {
                if (inside(mouseX, mouseY, box.x(), box.y() - 1, box.width(), 12)) {
                    toggleNbtFlag(box.toggle());
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int brighten(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = Math.min(255, ((color >> 16) & 0xFF) + 30);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 30);
        int blue = Math.min(255, (color & 0xFF) + 30);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
