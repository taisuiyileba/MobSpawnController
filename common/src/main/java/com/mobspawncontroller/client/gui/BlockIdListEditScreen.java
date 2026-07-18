package com.mobspawncontroller.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Line-by-line editor for block IDs and block tags.
 * Each row accepts one resource ID or #tag; invalid rows are highlighted.
 */
public class BlockIdListEditScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int INPUT_H = 16;
    private static final int DELETE_W = 18;
    private static final int DELETE_H = 16;
    private static final int SCROLLBAR_W = 6;
    private static final int PANEL_BG = 0xF015171B;
    private static final int ACCENT_COLOR = 0xFF63B3ED;
    private static final int ERROR_COLOR = 0xFFEF4444;
    private static final int OK_COLOR = 0xFF22C55E;

    private final Screen parent;
    private final Consumer<List<String>> onDone;
    private final List<String> rows = new ArrayList<>();
    private final List<EditBox> editBoxes = new ArrayList<>();

    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int listTop;
    private int listBottom;
    private int inputWidth;
    private double scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private double dragStartY;
    private double dragStartOffset;
    private String statusText = "";
    private int statusColor = 0xFF94A3B8;

    public BlockIdListEditScreen(Screen parent, Component title, List<String> initial,
                                 Consumer<List<String>> onDone) {
        super(title);
        this.parent = parent;
        this.onDone = onDone;
        for (String value : initial) {
            this.rows.add(value);
        }
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(360, Math.min(this.width - 32, 460));
        int panelHeight = Math.max(200, Math.min(this.height - 32, 380));
        panelLeft = (this.width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = (this.height - panelHeight) / 2;
        panelBottom = panelTop + panelHeight;
        listTop = panelTop + 34;
        listBottom = panelBottom - 44;
        inputWidth = panelWidth - 32 - DELETE_W - 6;

        editBoxes.clear();
        for (String value : rows) {
            editBoxes.add(createEditBox(value));
        }
        layoutEditBoxes();

        int row2Y = panelBottom - 30;
        int btnH = 18;

        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.natural.block_editor.add_held"),
                        button -> addHeldBlockId())
                .bounds(panelLeft + 8, row2Y, 64, btnH).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.natural.block_editor.add_viewed"),
                        button -> addViewedBlockId())
                .bounds(panelLeft + 76, row2Y, 64, btnH).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.natural.block_editor.add_below"),
                        button -> addBelowBlockId())
                .bounds(panelLeft + 144, row2Y, 64, btnH).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.cancel"),
                        button -> Minecraft.getInstance().setScreen(parent))
                .bounds(panelRight - 148, row2Y, 64, btnH).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.save"),
                        button -> saveAndClose())
                .bounds(panelRight - 76, row2Y, 64, btnH).build());
    }

    private EditBox createEditBox(String value) {
        EditBox box = new EditBox(font, panelLeft + 12, 0, inputWidth, INPUT_H, Component.empty());
        box.setValue(value);
        box.setMaxLength(160);
        return box;
    }

    private void layoutEditBoxes() {
        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < editBoxes.size(); i++) {
            editBoxes.get(i).setY(y + i * ROW_HEIGHT + 2);
        }
    }

    private void addEmptyRow() {
        addRow("", false);
    }

    private void addRow(String value, boolean fromActionButton) {
        syncRowsFromEditBoxes();
        String trimmed = value.trim();
        if (fromActionButton && !trimmed.isEmpty() && rows.contains(trimmed)) {
            setStatus("gui.mobspawncontroller.natural.block_editor.already_exists", ERROR_COLOR);
            return;
        }
        rows.add(value);
        EditBox box = createEditBox(value);
        box.setFocused(true);
        editBoxes.add(box);
        for (EditBox other : editBoxes) {
            if (other != box) {
                other.setFocused(false);
            }
        }
        layoutEditBoxes();
        scrollToBottom();
        if (fromActionButton) {
            setStatus("gui.mobspawncontroller.natural.block_editor.added", OK_COLOR);
        } else {
            statusText = "";
        }
    }

    private void addHeldBlockId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem blockItem)) {
            setStatus("gui.mobspawncontroller.natural.block_editor.not_block", ERROR_COLOR);
            return;
        }
        addRow(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString(), true);
    }

    private void addViewedBlockId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            setStatus("gui.mobspawncontroller.natural.block_editor.no_block_viewed", ERROR_COLOR);
            return;
        }
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.is(Blocks.AIR)) {
            setStatus("gui.mobspawncontroller.natural.block_editor.no_block_viewed", ERROR_COLOR);
            return;
        }
        addRow(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), true);
    }

    private void addBelowBlockId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        BlockPos pos = mc.player.blockPosition().below();
        BlockState state = mc.level.getBlockState(pos);
        if (state.is(Blocks.AIR)) {
            setStatus("gui.mobspawncontroller.natural.block_editor.no_block_below", ERROR_COLOR);
            return;
        }
        addRow(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), true);
    }

    private void setStatus(String key, int color, Object... args) {
        statusText = Component.translatable(key, args).getString();
        statusColor = color;
    }

    private void removeRow(int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        syncRowsFromEditBoxes();
        rows.remove(index);
        EditBox removed = editBoxes.remove(index);
        removed.setFocused(false);
        layoutEditBoxes();
        if (index < editBoxes.size()) {
            editBoxes.get(index).setFocused(true);
        } else if (!editBoxes.isEmpty()) {
            editBoxes.get(editBoxes.size() - 1).setFocused(true);
        }
        statusText = "";
    }

    private void scrollToBottom() {
        int viewHeight = listBottom - listTop;
        scrollOffset = Math.max(0, contentHeight() - viewHeight);
        layoutEditBoxes();
    }

    private void syncRowsFromEditBoxes() {
        for (int i = 0; i < editBoxes.size(); i++) {
            rows.set(i, editBoxes.get(i).getValue());
        }
    }

    private List<String> collectInvalid() {
        List<String> invalid = new ArrayList<>();
        for (String value : rows) {
            if (value.isBlank()) {
                continue;
            }
            if (!isValidBlockSelector(value)) {
                invalid.add(value.trim());
            }
        }
        return invalid;
    }

    static boolean isValidBlockSelector(String selector) {
        String trimmed = selector.trim();
        boolean tag = trimmed.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? trimmed.substring(1) : trimmed);
        if (id == null) {
            return false;
        }
        if (tag) {
            return BuiltInRegistries.BLOCK.getTagNames()
                    .anyMatch(tagKey -> tagKey.location().equals(id));
        }
        return BuiltInRegistries.BLOCK.containsKey(id);
    }

    private void saveAndClose() {
        syncRowsFromEditBoxes();
        List<String> invalid = collectInvalid();
        if (!invalid.isEmpty()) {
            setStatus("gui.mobspawncontroller.natural.block_editor.invalid", ERROR_COLOR,
                    String.join(", ", invalid.subList(0, Math.min(3, invalid.size()))));
            return;
        }
        List<String> result = new ArrayList<>();
        for (String value : rows) {
            String trimmed = value.trim();
            if (trimmed.isEmpty() || result.contains(trimmed)) {
                continue;
            }
            result.add(trimmed);
        }
        onDone.accept(result);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderPanel(graphics);
        renderTitle(graphics);

        graphics.enableScissor(panelLeft + 8, listTop, panelRight - 8, listBottom);
        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < editBoxes.size(); i++) {
            EditBox box = editBoxes.get(i);
            int rowY = y + i * ROW_HEIGHT;
            boolean visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            box.setVisible(visible);
            if (!visible && box.isFocused()) {
                box.setFocused(false);
            }
            if (visible) {
                renderRow(graphics, box, rowY, mouseX, mouseY, partialTick);
            }
        }
        int plusRowY = y + editBoxes.size() * ROW_HEIGHT;
        boolean plusVisible = plusRowY + ROW_HEIGHT > listTop && plusRowY < listBottom;
        if (plusVisible) {
            renderAddButton(graphics, plusRowY, mouseX, mouseY);
        }
        graphics.disableScissor();

        renderScrollbar(graphics);
        renderStatus(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics graphics, EditBox box, int rowY, int mouseX, int mouseY,
                           float partialTick) {
        boolean hovered = mouseX >= panelRight - 30 && mouseX < panelRight - 12
                && mouseY >= rowY + 2 && mouseY < rowY + 2 + DELETE_H;
        int deleteBg = hovered ? 0xFF7F1D1D : 0xFF374151;
        graphics.fill(panelRight - 30, rowY + 2, panelRight - 12, rowY + 2 + DELETE_H, deleteBg);
        graphics.renderOutline(panelRight - 30, rowY + 2, DELETE_W, DELETE_H,
                hovered ? 0xFFFCA5A5 : 0xFF64748B);
        graphics.drawCenteredString(font, "X", panelRight - 21,
                rowY + 6, hovered ? 0xFFFFFFFF : 0xFFD1D5DB);

        String value = box.getValue();
        int borderColor;
        if (value.isBlank()) {
            borderColor = box.isFocused() ? ACCENT_COLOR : 0xFF374151;
        } else if (isValidBlockSelector(value)) {
            borderColor = 0xFF15803D;
        } else {
            borderColor = ERROR_COLOR;
        }
        graphics.renderOutline(panelLeft + 11, rowY + 1, inputWidth + 2, INPUT_H + 2, borderColor);
        box.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderAddButton(GuiGraphics graphics, int rowY, int mouseX, int mouseY) {
        int x = panelLeft + 12;
        boolean hovered = mouseX >= x && mouseX < x + DELETE_W
                && mouseY >= rowY + 2 && mouseY < rowY + 2 + DELETE_H;
        int bg = hovered ? 0xFF2563EB : 0xFF374151;
        graphics.fill(x, rowY + 2, x + DELETE_W, rowY + 2 + DELETE_H, bg);
        graphics.renderOutline(x, rowY + 2, DELETE_W, DELETE_H,
                hovered ? ACCENT_COLOR : 0xFF64748B);
        graphics.drawCenteredString(font, "+", x + DELETE_W / 2,
                rowY + 6, 0xFFFFFFFF);
    }

    private void renderPanel(GuiGraphics graphics) {
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);
        graphics.renderOutline(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF4B5563);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + 3, panelBottom - 1, 0x6657A6FF);
    }

    private void renderTitle(GuiGraphics graphics) {
        graphics.drawCenteredString(font, title, (panelLeft + panelRight) / 2, panelTop + 8, 0xFFFFFFFF);
        Component hint = Component.translatable("gui.mobspawncontroller.natural.block_editor.description");
        graphics.drawCenteredString(font, hint, (panelLeft + panelRight) / 2, panelTop + 20, 0xFF94A3B8);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusText.isEmpty()) {
            return;
        }
        graphics.drawCenteredString(font, statusText, (panelLeft + panelRight) / 2, panelBottom - 48, statusColor);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int visible = listBottom - listTop;
        int content = contentHeight();
        int barHeight = Math.max(20, visible * visible / content);
        int track = visible - barHeight;
        int barY = listTop + (track > 0 ? (int) (scrollOffset / maxScroll * track) : 0);
        int trackX = panelRight - 9;
        int thumbX = panelRight - 9;

        graphics.fill(trackX, listTop, trackX + SCROLLBAR_W, listBottom, 0x40000000);
        graphics.fill(thumbX, barY, thumbX + SCROLLBAR_W, barY + barHeight, 0xCCFFFFFF);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (listBottom - listTop));
    }

    private int contentHeight() {
        return (rows.size() + 1) * ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        layoutEditBoxes();

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int visible = listBottom - listTop;
            int content = contentHeight();
            int barHeight = Math.max(20, visible * visible / content);
            int track = visible - barHeight;
            int barY = listTop + (track > 0 ? (int) (scrollOffset / maxScroll * track) : 0);
            int thumbX = panelRight - 9;
            if (mouseX >= thumbX && mouseX < thumbX + SCROLLBAR_W
                    && mouseY >= barY && mouseY < barY + barHeight) {
                draggingScrollbar = true;
                dragStartY = mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
            int trackX = panelRight - 9;
            if (mouseX >= trackX && mouseX < trackX + SCROLLBAR_W
                    && mouseY >= listTop && mouseY < listBottom) {
                scrollOffset = Math.max(0, Math.min((mouseY - listTop - barHeight / 2.0) / track * maxScroll, maxScroll));
                layoutEditBoxes();
                return true;
            }
        }

        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < editBoxes.size(); i++) {
            int rowY = y + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT <= listTop || rowY >= listBottom) {
                continue;
            }
            if (mouseX >= panelRight - 30 && mouseX < panelRight - 12
                    && mouseY >= rowY + 2 && mouseY < rowY + 2 + DELETE_H) {
                removeRow(i);
                return true;
            }
            EditBox box = editBoxes.get(i);
            if (box.isMouseOver(mouseX, mouseY)) {
                for (EditBox other : editBoxes) {
                    other.setFocused(false);
                }
                box.setFocused(true);
                return box.mouseClicked(mouseX, mouseY, button);
            }
        }
        int plusRowY = y + editBoxes.size() * ROW_HEIGHT;
        if (plusRowY + ROW_HEIGHT > listTop && plusRowY < listBottom) {
            int plusX = panelLeft + 12;
            if (mouseX >= plusX && mouseX < plusX + DELETE_W
                    && mouseY >= plusRowY + 2 && mouseY < plusRowY + 2 + DELETE_H) {
                addEmptyRow();
                return true;
            }
        }
        for (EditBox box : editBoxes) {
            box.setFocused(false);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            int visible = listBottom - listTop;
            int maxScroll = maxScroll();
            int content = contentHeight();
            int barHeight = Math.max(20, visible * visible / content);
            int track = visible - barHeight;
            if (track > 0) {
                scrollOffset = Math.max(0, Math.min(dragStartOffset
                        + (mouseY - dragStartY) / track * maxScroll, maxScroll));
                layoutEditBoxes();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - amount * ROW_HEIGHT * 2, maxScroll()));
            layoutEditBoxes();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox box : editBoxes) {
            if (box.isFocused() && box.isVisible()) {
                return box.charTyped(codePoint, modifiers);
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : editBoxes) {
            if (box.isFocused() && box.isVisible()) {
                return box.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
