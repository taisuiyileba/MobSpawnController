package com.mobspawncontroller.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Searchable multi-select list backed by the current client registry data. */
public class NaturalRegistryPickerScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_BG = 0xF015171B;
    private static final int ACCENT_COLOR = 0xFF63B3ED;
    private final Screen parent;
    private final List<Option> allOptions;
    private final Set<String> selected;
    private final Consumer<List<String>> onDone;
    private List<Option> filteredOptions = new ArrayList<>();
    private EditBox searchBox;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int listTop;
    private int listBottom;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private double dragStartY;
    private double dragStartOffset;

    public NaturalRegistryPickerScreen(Screen parent, Component title, List<Option> options,
                                       List<String> initialSelection, Consumer<List<String>> onDone) {
        super(title);
        this.parent = parent;
        this.allOptions = new ArrayList<>(options);
        this.selected = new HashSet<>(initialSelection);
        this.onDone = onDone;
        Set<String> known = new HashSet<>();
        this.allOptions.forEach(option -> known.add(option.value));
        initialSelection.stream().filter(value -> !known.contains(value))
                .forEach(value -> this.allOptions.add(new Option(value, value)));
        this.allOptions.sort(Comparator.comparing(Option::value));
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(280, Math.min(width - 32, 500));
        int panelHeight = Math.max(190, height - 32);
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = (height - panelHeight) / 2;
        panelBottom = panelTop + panelHeight;
        listTop = panelTop + 58;
        listBottom = panelBottom - 34;

        searchBox = new EditBox(font, panelLeft + 12, panelTop + 30, panelWidth - 24, 18,
                Component.translatable("gui.mobspawncontroller.natural.picker.search"));
        searchBox.setHint(Component.translatable("gui.mobspawncontroller.natural.picker.search"));
        searchBox.setMaxLength(128);
        searchBox.setResponder(value -> applyFilter());
        addRenderableWidget(searchBox);

        int buttonY = panelBottom - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.natural.picker.select_visible"),
                        button -> filteredOptions.forEach(option -> selected.add(option.value)))
                .bounds(panelLeft + 8, buttonY, 58, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.natural.picker.clear"),
                        button -> selected.clear())
                .bounds(panelLeft + 70, buttonY, 58, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.cancel"),
                        button -> Minecraft.getInstance().setScreen(parent))
                .bounds(panelRight - 148, buttonY, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.save"), button -> {
                    List<String> result = selected.stream().sorted().toList();
                    onDone.accept(result);
                    Minecraft.getInstance().setScreen(parent);
                }).bounds(panelRight - 76, buttonY, 64, 18).build());
        applyFilter();
    }

    private void applyFilter() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        filteredOptions = allOptions.stream()
                .filter(option -> query.isEmpty() || option.value.toLowerCase(Locale.ROOT).contains(query)
                        || option.label.toLowerCase(Locale.ROOT).contains(query))
                .toList();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private int maxScroll() {
        return Math.max(0, filteredOptions.size() * ROW_HEIGHT - (listBottom - listTop));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);
        graphics.renderOutline(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF4B5563);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + 3, panelBottom - 1, 0x6657A6FF);
        Component heading = title.copy().append(" · ")
                .append(Component.translatable("gui.mobspawncontroller.natural.selected_count", selected.size()));
        graphics.drawCenteredString(font, heading, width / 2, panelTop + 10, 0xFFFFFFFF);

        graphics.enableScissor(panelLeft + 8, listTop, panelRight - 8, listBottom);
        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < filteredOptions.size(); i++) {
            Option option = filteredOptions.get(i);
            int rowY = y + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) continue;
            boolean checked = selected.contains(option.value);
            boolean hovered = mouseX >= panelLeft + 10 && mouseX < panelRight - 10
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            graphics.fill(panelLeft + 10, rowY, panelRight - 10, rowY + ROW_HEIGHT - 1,
                    hovered ? 0x3F63B3ED : i % 2 == 0 ? 0x12FFFFFF : 0x08000000);
            int boxX = panelLeft + 15;
            graphics.fill(boxX, rowY + 4, boxX + 12, rowY + 16, checked ? 0xFF167C4B : 0xFF111827);
            graphics.renderOutline(boxX, rowY + 4, 12, 12, checked ? 0xFF86EFAC : 0xFF64748B);
            if (checked) graphics.drawString(font, "\u2713", boxX + 2, rowY + 6, 0xFFFFFFFF);
            graphics.drawString(font, trim(option.label, panelRight - boxX - 42), boxX + 18, rowY + 6,
                    checked ? 0xFFFFFFFF : 0xFFD1D5DB);
        }
        graphics.disableScissor();

        if (maxScroll() > 0) {
            int visible = listBottom - listTop;
            int barHeight = Math.max(20, visible * visible / (filteredOptions.size() * ROW_HEIGHT));
            int barY = listTop + (int) (scrollOffset / maxScroll() * (visible - barHeight));
            graphics.fill(panelRight - 7, listTop, panelRight - 3, listBottom, 0x40000000);
            graphics.fill(panelRight - 7, barY, panelRight - 3, barY + barHeight, 0xAAFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (maxScroll() > 0) {
            int visible = listBottom - listTop;
            int barHeight = Math.max(20, visible * visible / (filteredOptions.size() * ROW_HEIGHT));
            int barY = listTop + (int) (scrollOffset / maxScroll() * (visible - barHeight));
            if (mouseX >= panelRight - 10 && mouseX <= panelRight
                    && mouseY >= barY && mouseY < barY + barHeight) {
                draggingScrollbar = true;
                dragStartY = mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
        }
        if (mouseX >= panelLeft + 10 && mouseX < panelRight - 10 && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scrollOffset) / ROW_HEIGHT);
            if (index >= 0 && index < filteredOptions.size()) {
                String value = filteredOptions.get(index).value;
                if (!selected.add(value)) selected.remove(value);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            int visible = listBottom - listTop;
            int barHeight = Math.max(20, visible * visible / (filteredOptions.size() * ROW_HEIGHT));
            int track = visible - barHeight;
            if (track > 0) {
                scrollOffset = Math.max(0, Math.min(dragStartOffset
                        + (mouseY - dragStartY) / track * maxScroll(), maxScroll()));
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
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record Option(String value, String label) {
    }
}
