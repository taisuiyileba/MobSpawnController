package com.mobspawncontroller.client.gui;

import com.mobspawncontroller.attribute.MobAttributeControl;
import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MobSpawnEditScreen extends Screen implements ClientRuleSync.Receiver {

    private static final int ROW_HEIGHT = 26;
    private static final int ATTRIBUTE_ROW_HEIGHT = 26;
    private static final int TOGGLE_W = 38;
    private static final int TOGGLE_H = 16;
    private static final int ATTRIBUTE_INPUT_W = 68;
    private static final int ATTRIBUTE_RESET_W = 16;
    private static final int HEADER_RESET_W = 62;
    private static final int HEADER_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 30;
    private static final int PANEL_INSET = 8;
    private static final int TAB_HEIGHT = 16;
    private static final int ACCENT_COLOR = 0xFF63B3ED;
    private static final int PANEL_BG = 0xF015171B;
    private static final int HEADER_BG = 0xAA202630;
    private static final int ROW_BG = 0x1AFFFFFF;
    private static final int ROW_HOVER_BG = 0x24FFFFFF;

    private enum DetailTab {
        SPAWN_RULES,
        ATTRIBUTES
    }

    private final MobSpawnControllerScreen parent;
    private final ResourceLocation mobId;
    private final EntityType<?> entityType;
    private final EnumMap<MobSpawnType, Boolean> editRules = new EnumMap<>(MobSpawnType.class);
    private final MobSpawnType[] spawnTypes = MobSpawnType.values();
    private final List<MobAttributeControl> attributeControls = new ArrayList<>();

    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int listTop;
    private int listBottom;
    private int contentHeight;
    private double scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private double dragStartY = 0;
    private double dragStartOffset = 0;
    private ResourceLocation focusedAttributeId = null;
    private Button cancelButton;
    private Button saveButton;
    private DetailTab activeTab = DetailTab.SPAWN_RULES;
    private boolean attributesLoaded = false;
    private final Map<ResourceLocation, String> attributeInputs = new HashMap<>();

    public MobSpawnEditScreen(MobSpawnControllerScreen parent, ResourceLocation mobId) {
        super(Component.literal(mobId.toString()));
        this.parent = parent;
        this.mobId = mobId;
        this.entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);

        for (MobSpawnType spawnType : spawnTypes) {
            editRules.put(spawnType, true);
        }
        EnumMap<MobSpawnType, Boolean> existing = parent.getRules().get(mobId);
        if (existing != null) {
            editRules.putAll(existing);
        }
        NetworkBridge.sendToServer(new ServerboundRequestAttributesPayload(mobId));
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(240, Math.min(this.width - 32, 360));
        int desiredHeight = HEADER_HEIGHT + spawnTypes.length * ROW_HEIGHT + FOOTER_HEIGHT;
        int panelHeight = Math.max(170, Math.min(this.height - 32, desiredHeight));
        panelLeft = (this.width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = (this.height - panelHeight) / 2;
        panelBottom = panelTop + panelHeight;
        listTop = panelTop + HEADER_HEIGHT;
        listBottom = panelBottom - FOOTER_HEIGHT;
        updateContentHeight();

        int btnWidth = 62;
        int btnGap = 8;
        int totalBtnWidth = btnWidth * 2 + btnGap;
        int btnStartX = panelRight - PANEL_INSET - totalBtnWidth;
        int btnY = panelBottom - 23;

        cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.cancel"),
                button -> Minecraft.getInstance().setScreen(parent))
                .bounds(btnStartX, btnY, btnWidth, 18).build());

        saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.mobspawncontroller.save"),
                button -> saveAndClose())
                .bounds(btnStartX + btnWidth + btnGap, btnY, btnWidth, 18).build());
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - (listBottom - listTop));
    }

    private void updateContentHeight() {
        if (activeTab == DetailTab.SPAWN_RULES) {
            contentHeight = spawnTypes.length * ROW_HEIGHT;
            return;
        }
        contentHeight = attributeControls.isEmpty() ? 88 : attributeControls.size() * ATTRIBUTE_ROW_HEIGHT;
    }

    private void saveAndClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.hasPermissions(2)) {
            mc.player.displayClientMessage(Component.translatable("gui.mobspawncontroller.no_permission")
                    .withStyle(ChatFormatting.RED), false);
            mc.setScreen(parent);
            return;
        }

        for (MobSpawnType type : spawnTypes) {
            Boolean value = editRules.get(type);
            if (value != null) {
                NetworkBridge.sendToServer(new ServerboundToggleSpawnPayload(
                        mobId, type.name().toLowerCase(Locale.ROOT), value));
            }
        }
        if (attributesLoaded) {
            NetworkBridge.sendToServer(new ServerboundSetAttributesPayload(mobId, collectAttributeOverrides()));
        }

        EnumMap<MobSpawnType, Boolean> parentMap = parent.getRules()
                .computeIfAbsent(mobId, key -> new EnumMap<>(MobSpawnType.class));
        parentMap.clear();
        parentMap.putAll(editRules);

        mc.setScreen(parent);
    }

    private Map<ResourceLocation, Double> collectAttributeOverrides() {
        Map<ResourceLocation, Double> overrides = new HashMap<>();
        for (MobAttributeControl control : attributeControls) {
            if (control.overridden()) {
                Double parsed = control.type() == MobAttributeControl.ControlType.BOOLEAN
                        ? control.value() : parseInputValue(attributeInputs.get(control.id()), control.type());
                overrides.put(control.id(), parsed != null ? parsed : control.value());
            }
        }
        return overrides;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        renderPanel(guiGraphics);

        int headerY = panelTop + 8;
        int iconSize = 30;
        renderHeaderIcon(guiGraphics, headerY, iconSize);
        if (activeTab == DetailTab.SPAWN_RULES) {
            renderAllToggle(guiGraphics, mouseX, mouseY, headerY);
        } else {
            renderHeaderResetButton(guiGraphics, mouseX, mouseY, headerY);
        }
        renderHeaderText(guiGraphics, headerY, iconSize);
        renderTabs(guiGraphics, mouseX, mouseY);
        guiGraphics.fill(panelLeft + PANEL_INSET, listTop - 1, panelRight - PANEL_INSET, listTop, 0xFF303742);

        int visibleHeight = listBottom - listTop;
        guiGraphics.enableScissor(panelLeft + PANEL_INSET, listTop, panelRight - PANEL_INSET, listBottom);
        if (activeTab == DetailTab.SPAWN_RULES) {
            renderSpawnRows(guiGraphics, mouseX, mouseY);
        } else {
            renderAttributeRows(guiGraphics, mouseX, mouseY);
        }
        guiGraphics.disableScissor();

        if (contentHeight > visibleHeight) {
            renderScrollbar(guiGraphics, visibleHeight);
        }

        guiGraphics.fill(panelLeft + PANEL_INSET, listBottom, panelRight - PANEL_INSET, listBottom + 1, 0xFF303742);
        renderFooter(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);
        guiGraphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, listTop, HEADER_BG);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF4B5563);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF4B5563);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF4B5563);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF4B5563);
        guiGraphics.fill(panelLeft + 1, panelTop + 1, panelLeft + 3, panelBottom - 1, 0x6657A6FF);
    }

    private void renderHeaderIcon(GuiGraphics guiGraphics, int headerY, int iconSize) {
        int iconLeft = panelLeft + 14;
        int iconTop = headerY;
        guiGraphics.fill(iconLeft - 2, iconTop - 2, iconLeft + iconSize + 2, iconTop + iconSize + 2, 0xFF303742);
        guiGraphics.fill(iconLeft - 1, iconTop - 1, iconLeft + iconSize + 1, iconTop + iconSize + 1, 0xFF111827);
        guiGraphics.fill(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize, 0xFF202936);
        guiGraphics.fill(iconLeft, iconTop, iconLeft + iconSize, iconTop + 1, ACCENT_COLOR);

        if (entityType != null) {
            MobSpawnControllerScreen.renderEntityIcon(guiGraphics, entityType, iconLeft + iconSize / 2,
                    iconTop + iconSize / 2 + 3, iconSize, parent.getEntityCache());
        }
    }

    private void renderHeaderText(GuiGraphics guiGraphics, int headerY, int iconSize) {
        int textX = panelLeft + 14 + iconSize + 12;
        String mainName;
        String subName;
        if (entityType != null) {
            mainName = entityType.getDescription().getString();
            subName = mobId.toString();
        } else {
            mainName = mobId.toString();
            subName = "";
        }

        int maxTextWidth = panelRight - textX - (activeTab == DetailTab.SPAWN_RULES ? 82 : HEADER_RESET_W + 22);
        mainName = trimToWidth(mainName, maxTextWidth);
        subName = trimToWidth(subName, maxTextWidth);

        guiGraphics.drawString(this.font, mainName, textX, headerY + 1, 0xFFFFFFFF);
        if (!subName.isEmpty()) {
            guiGraphics.drawString(this.font, subName, textX, headerY + 13, 0xFFB6C2D0);
        }
        Component tabLabel = activeTab == DetailTab.SPAWN_RULES
                ? Component.translatable("gui.mobspawncontroller.tab.spawn_rules")
                : Component.translatable("gui.mobspawncontroller.tab.attributes");
        guiGraphics.drawString(this.font, tabLabel, textX, headerY + 25, 0xFF7DD3FC);
    }

    private void renderAllToggle(GuiGraphics guiGraphics, int mouseX, int mouseY, int headerY) {
        int allToggleX = panelRight - 14 - TOGGLE_W;
        int allToggleY = headerY + 7;
        drawToggle(guiGraphics, allToggleX, allToggleY, allEnabled(), mouseX, mouseY);
        String allText = Component.translatable("gui.mobspawncontroller.all").getString();
        guiGraphics.drawString(this.font, allText, allToggleX - font.width(allText) - 6,
                allToggleY + (TOGGLE_H - font.lineHeight) / 2, 0xFFD1D5DB);
    }

    private void renderHeaderResetButton(GuiGraphics guiGraphics, int mouseX, int mouseY, int headerY) {
        int x = panelRight - 14 - HEADER_RESET_W;
        int y = headerY + 7;
        int modifiedCount = modifiedAttributeCount();
        boolean active = modifiedCount > 0;
        boolean hovered = mouseX >= x && mouseX < x + HEADER_RESET_W && mouseY >= y && mouseY < y + 18;
        guiGraphics.fill(x, y, x + HEADER_RESET_W, y + 18,
                active ? hovered ? 0xFF25637E : 0xFF1D4E65 : hovered ? 0xFF374151 : 0xFF202936);
        guiGraphics.renderOutline(x, y, HEADER_RESET_W, 18,
                active ? 0xFF7DD3FC : hovered ? ACCENT_COLOR : 0xFF4B5563);
        String label = Component.translatable("gui.mobspawncontroller.reset").getString();
        if (active) {
            label = label + "(" + modifiedCount + ")";
        }
        guiGraphics.drawCenteredString(this.font, trimToWidth(label, HEADER_RESET_W - 6),
                x + HEADER_RESET_W / 2, y + (18 - font.lineHeight) / 2, active ? 0xFFFFFFFF : 0xFFE5E7EB);
    }

    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int tabY = panelTop + HEADER_HEIGHT - TAB_HEIGHT - 6;
        int tabWidth = (panelRight - panelLeft - PANEL_INSET * 2 - 4) / 2;
        int firstTabX = panelLeft + PANEL_INSET;
        renderTab(guiGraphics, mouseX, mouseY, firstTabX, tabY, tabWidth, DetailTab.SPAWN_RULES,
                Component.translatable("gui.mobspawncontroller.tab.spawn_rules"));
        renderTab(guiGraphics, mouseX, mouseY, firstTabX + tabWidth + 4, tabY, tabWidth, DetailTab.ATTRIBUTES,
                Component.translatable("gui.mobspawncontroller.tab.attributes"));
    }

    private void renderTab(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width,
                           DetailTab tab, Component label) {
        boolean active = activeTab == tab;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
        int bg = active ? 0xFF263445 : hovered ? 0xFF202936 : 0xCC111827;
        int line = active ? ACCENT_COLOR : 0xFF374151;
        guiGraphics.fill(x, y, x + width, y + TAB_HEIGHT, bg);
        guiGraphics.fill(x, y + TAB_HEIGHT - 1, x + width, y + TAB_HEIGHT, line);
        guiGraphics.drawCenteredString(this.font, label, x + width / 2,
                y + (TAB_HEIGHT - font.lineHeight) / 2, active ? 0xFFFFFFFF : 0xFFB6C2D0);
    }

    private void renderSpawnRows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < spawnTypes.length; i++) {
            MobSpawnType spawnType = spawnTypes[i];
            int rowY = y + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            if (i % 2 == 0) {
                guiGraphics.fill(panelLeft + PANEL_INSET, rowY, panelRight - PANEL_INSET, rowY + ROW_HEIGHT, ROW_BG);
            }
            if (mouseX >= panelLeft + PANEL_INSET && mouseX < panelRight - PANEL_INSET
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                guiGraphics.fill(panelLeft + PANEL_INSET, rowY, panelRight - PANEL_INSET,
                        rowY + ROW_HEIGHT, ROW_HOVER_BG);
            }

            String typeName = spawnType.name().toLowerCase(Locale.ROOT);
            String translatedName = Component.translatable("gui.mobspawncontroller.spawntype." + typeName).getString();
            int toggleX = panelRight - 14 - TOGGLE_W;
            int toggleY = rowY + (ROW_HEIGHT - TOGGLE_H) / 2;
            int labelX = panelLeft + 16;
            int labelMaxWidth = toggleX - labelX - 12;
            int metaX = panelLeft + 148;
            int metaMaxWidth = toggleX - metaX - 8;
            if (panelRight - panelLeft >= 320 && metaMaxWidth > 36) {
                labelMaxWidth = metaX - labelX - 12;
                guiGraphics.drawString(this.font, trimToWidth(typeName, metaMaxWidth), metaX,
                        rowY + (ROW_HEIGHT - font.lineHeight) / 2, 0xFF7B8794);
            }
            guiGraphics.drawString(this.font, trimToWidth(translatedName, labelMaxWidth), labelX,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFE5E7EB);
            drawToggle(guiGraphics, toggleX, toggleY, editRules.getOrDefault(spawnType, true), mouseX, mouseY);
        }
    }

    private void renderAttributeRows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop - (int) scrollOffset;
        if (attributeControls.isEmpty()) {
            renderEmptyAttributes(guiGraphics, y);
            return;
        }

        for (int i = 0; i < attributeControls.size(); i++) {
            MobAttributeControl control = attributeControls.get(i);
            int rowY = y + i * ATTRIBUTE_ROW_HEIGHT;
            if (rowY + ATTRIBUTE_ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }
            boolean hovered = mouseX >= panelLeft + PANEL_INSET && mouseX < panelRight - PANEL_INSET
                    && mouseY >= rowY && mouseY < rowY + ATTRIBUTE_ROW_HEIGHT;
            guiGraphics.fill(panelLeft + PANEL_INSET, rowY, panelRight - PANEL_INSET,
                    rowY + ATTRIBUTE_ROW_HEIGHT - 1, hovered ? ROW_HOVER_BG : ROW_BG);
            if (control.overridden()) {
                guiGraphics.fill(panelLeft + PANEL_INSET, rowY, panelLeft + PANEL_INSET + 2,
                        rowY + ATTRIBUTE_ROW_HEIGHT - 1, ACCENT_COLOR);
            }
            renderAttributeControl(guiGraphics, control, rowY);
        }
    }

    private void renderEmptyAttributes(GuiGraphics guiGraphics, int y) {
        int x = panelLeft + 16;
        int width = panelRight - panelLeft - 32;
        int boxTop = Math.max(y + 10, listTop + 10);
        guiGraphics.fill(x, boxTop, x + width, boxTop + 64, 0x40111827);
        guiGraphics.renderOutline(x, boxTop, width, 64, 0xFF303742);
        guiGraphics.drawString(this.font, Component.translatable("gui.mobspawncontroller.attributes.empty_title"),
                x + 10, boxTop + 12, 0xFFE5E7EB);
        guiGraphics.drawString(this.font, Component.translatable("gui.mobspawncontroller.attributes.empty_detail"),
                x + 10, boxTop + 30, 0xFF94A3B8);
    }

    private void renderAttributeControl(GuiGraphics guiGraphics, MobAttributeControl control, int rowY) {
        int textX = panelLeft + 14;
        int resetX = panelRight - 16 - ATTRIBUTE_RESET_W;
        int controlX = resetX - 6 - ATTRIBUTE_INPUT_W;
        int textMaxWidth = controlX - textX - 12;
        String label = Component.translatable(control.descriptionKey()).getString() + " / " + control.id();
        guiGraphics.drawString(this.font, trimToWidth(label, textMaxWidth), textX,
                rowY + (ATTRIBUTE_ROW_HEIGHT - font.lineHeight) / 2, control.overridden() ? 0xFFBEEBFF : 0xFFE5E7EB);

        if (control.type() == MobAttributeControl.ControlType.BOOLEAN) {
            drawToggle(guiGraphics, controlX + ATTRIBUTE_INPUT_W - TOGGLE_W,
                    rowY + (ATTRIBUTE_ROW_HEIGHT - TOGGLE_H) / 2, control.value() > 0.0, -1, -1);
        } else {
            renderNumberInput(guiGraphics, controlX, rowY + (ATTRIBUTE_ROW_HEIGHT - 18) / 2, control);
        }
        renderAttributeResetButton(guiGraphics, resetX, rowY + (ATTRIBUTE_ROW_HEIGHT - ATTRIBUTE_RESET_W) / 2,
                control.overridden());
    }

    private void renderNumberInput(GuiGraphics guiGraphics, int x, int y, MobAttributeControl control) {
        boolean focused = control.id().equals(focusedAttributeId);
        String valueText = attributeInputs.getOrDefault(control.id(), formatInputValue(control.value(), control.type()));
        boolean valid = parseInputValue(valueText, control.type()) != null;
        guiGraphics.fill(x, y, x + ATTRIBUTE_INPUT_W, y + 18, 0xFF111827);
        guiGraphics.renderOutline(x, y, ATTRIBUTE_INPUT_W, 18,
                focused ? ACCENT_COLOR : valid ? 0xFF4B5563 : 0xFFEF4444);
        String suffix = control.type() == MobAttributeControl.ControlType.PERCENT ? "%" : "";
        int suffixWidth = suffix.isEmpty() ? 0 : font.width(suffix) + 2;
        guiGraphics.drawString(this.font, trimToWidth(valueText, ATTRIBUTE_INPUT_W - suffixWidth - 8),
                x + 4, y + (18 - font.lineHeight) / 2, valid ? 0xFFE5E7EB : 0xFFFFB4B4);
        if (!suffix.isEmpty()) {
            guiGraphics.drawString(this.font, suffix, x + ATTRIBUTE_INPUT_W - suffixWidth,
                    y + (18 - font.lineHeight) / 2, 0xFF94A3B8);
        }
    }

    private void renderAttributeResetButton(GuiGraphics guiGraphics, int x, int y, boolean active) {
        int bg = active ? 0xFF2B3442 : 0xFF171C24;
        guiGraphics.fill(x, y, x + ATTRIBUTE_RESET_W, y + ATTRIBUTE_RESET_W, bg);
        guiGraphics.renderOutline(x, y, ATTRIBUTE_RESET_W, ATTRIBUTE_RESET_W, active ? 0xFF7DD3FC : 0xFF374151);
        guiGraphics.drawCenteredString(this.font, "R", x + ATTRIBUTE_RESET_W / 2,
                y + (ATTRIBUTE_RESET_W - font.lineHeight) / 2, active ? 0xFFE5E7EB : 0xFF6B7280);
    }

    private void renderFooter(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(panelLeft + 1, listBottom + 1, panelRight - 1, panelBottom - 1, 0xBB111827);
        if (cancelButton != null) {
            cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (saveButton != null) {
            saveButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int visibleHeight) {
        int scrollBarX = panelRight - 8;
        int scrollBarW = 4;
        int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
        int maxScroll = getMaxScroll();
        int scrollBarY = listTop + (maxScroll > 0
                ? (int) (scrollOffset / maxScroll * (visibleHeight - scrollBarH)) : 0);
        guiGraphics.fill(scrollBarX, listTop, scrollBarX + scrollBarW, listBottom, 0x40000000);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarW, scrollBarY + scrollBarH, 0xAAFFFFFF);
    }

    private void drawToggle(GuiGraphics guiGraphics, int x, int y, boolean state, int mouseX, int mouseY) {
        int bgColor = state ? 0xFF16A34A : 0xFF6B7280;
        int borderColor = state ? 0xFF86EFAC : 0xFF9CA3AF;
        int labelColor = state ? 0xFFEFFFF4 : 0xFFE5E7EB;
        if (mouseX >= x && mouseX < x + TOGGLE_W && mouseY >= y && mouseY < y + TOGGLE_H) {
            bgColor = brighten(bgColor);
        }

        guiGraphics.fill(x - 1, y - 1, x + TOGGLE_W + 1, y + TOGGLE_H + 1, 0xAA000000);
        guiGraphics.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, bgColor);
        guiGraphics.fill(x, y, x + TOGGLE_W, y + 2, 0x30FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + TOGGLE_H, borderColor);

        int knobSize = TOGGLE_H - 4;
        int knobX = state ? x + TOGGLE_W - knobSize - 2 : x + 2;
        int knobY = y + 2;
        guiGraphics.fill(knobX + 1, knobY + 1, knobX + knobSize + 1, knobY + knobSize + 1, 0x55000000);
        guiGraphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF);

        String label = state ? "ON" : "OFF";
        int labelX = state ? x + 5 : x + TOGGLE_W - font.width(label) - 5;
        guiGraphics.drawString(this.font, label, labelX, y + (TOGGLE_H - font.lineHeight) / 2 + 1, labelColor);
    }

    private static int brighten(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = Math.min(255, ((color >> 16) & 0xFF) + 30);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 30);
        int blue = Math.min(255, (color & 0xFF) + 30);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return "";
        }
        return this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }

    private static String formatInputValue(double value, MobAttributeControl.ControlType type) {
        double displayValue = type == MobAttributeControl.ControlType.PERCENT ? value * 100.0 : value;
        if (Math.abs(displayValue - Math.rint(displayValue)) < 0.001) {
            return String.format(Locale.ROOT, "%.0f", displayValue);
        }
        return String.format(Locale.ROOT, "%.3f", displayValue)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static Double parseInputValue(String text, MobAttributeControl.ControlType type) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value)) {
                return null;
            }
            return type == MobAttributeControl.ControlType.PERCENT ? value / 100.0 : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isNumericInputChar(char chr) {
        return (chr >= '0' && chr <= '9') || chr == '-' || chr == '+' || chr == '.' || chr == 'e' || chr == 'E';
    }

    private boolean allEnabled() {
        for (MobSpawnType spawnType : spawnTypes) {
            if (!editRules.getOrDefault(spawnType, true)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (handleTabClick(mouseX, mouseY)) {
            return true;
        }

        if (activeTab == DetailTab.ATTRIBUTES && handleHeaderResetClick(mouseX, mouseY)) {
            return true;
        }

        if (contentHeight > listBottom - listTop) {
            int scrollBarX = panelRight - 8;
            int scrollBarW = 4;
            int scrollBarH = Math.max(20, (int) ((double) (listBottom - listTop) * (listBottom - listTop) / contentHeight));
            int maxScroll = getMaxScroll();
            int scrollBarY = listTop + (maxScroll > 0
                    ? (int) (scrollOffset / maxScroll * ((listBottom - listTop) - scrollBarH)) : 0);
            if (mouseX >= scrollBarX && mouseX < scrollBarX + scrollBarW
                    && mouseY >= scrollBarY && mouseY < scrollBarY + scrollBarH) {
                draggingScrollbar = true;
                dragStartY = mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
        }

        if (activeTab == DetailTab.ATTRIBUTES) {
            return handleAttributeClick(mouseX, mouseY);
        }

        if (activeTab != DetailTab.SPAWN_RULES) {
            return false;
        }

        int allToggleX = panelRight - 14 - TOGGLE_W;
        int allToggleY = panelTop + 20;
        if (mouseX >= allToggleX && mouseX < allToggleX + TOGGLE_W
                && mouseY >= allToggleY && mouseY < allToggleY + TOGGLE_H) {
            boolean newValue = !allEnabled();
            for (MobSpawnType spawnType : spawnTypes) {
                editRules.put(spawnType, newValue);
            }
            return true;
        }

        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < spawnTypes.length; i++) {
            int rowY = y + i * ROW_HEIGHT;
            int toggleX = panelRight - 14 - TOGGLE_W;
            int toggleY = rowY + (ROW_HEIGHT - TOGGLE_H) / 2;
            if (mouseX >= toggleX && mouseX < toggleX + TOGGLE_W
                    && mouseY >= toggleY && mouseY < toggleY + TOGGLE_H) {
                MobSpawnType spawnType = spawnTypes[i];
                editRules.put(spawnType, !editRules.getOrDefault(spawnType, true));
                return true;
            }
        }

        return false;
    }

    private boolean handleAttributeClick(double mouseX, double mouseY) {
        if (mouseX < panelLeft || mouseX > panelRight || mouseY < listTop || mouseY > listBottom) {
            focusedAttributeId = null;
            return false;
        }

        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < attributeControls.size(); i++) {
            MobAttributeControl control = attributeControls.get(i);
            int rowY = y + i * ATTRIBUTE_ROW_HEIGHT;
            int resetX = panelRight - 16 - ATTRIBUTE_RESET_W;
            int controlX = resetX - 6 - ATTRIBUTE_INPUT_W;
            int resetY = rowY + (ATTRIBUTE_ROW_HEIGHT - ATTRIBUTE_RESET_W) / 2;
            int inputY = rowY + (ATTRIBUTE_ROW_HEIGHT - 18) / 2;
            if (mouseX >= resetX && mouseX < resetX + ATTRIBUTE_RESET_W
                    && mouseY >= resetY && mouseY < resetY + ATTRIBUTE_RESET_W) {
                resetAttribute(i);
                return true;
            }
            if (control.type() == MobAttributeControl.ControlType.BOOLEAN) {
                int toggleX = controlX + ATTRIBUTE_INPUT_W - TOGGLE_W;
                int toggleY = rowY + (ATTRIBUTE_ROW_HEIGHT - TOGGLE_H) / 2;
                if (mouseX >= toggleX && mouseX < toggleX + TOGGLE_W
                        && mouseY >= toggleY && mouseY < toggleY + TOGGLE_H) {
                    focusedAttributeId = null;
                    updateAttributeValue(i, control.value() > 0.0 ? 0.0 : 1.0);
                    return true;
                }
            } else if (mouseX >= controlX && mouseX < controlX + ATTRIBUTE_INPUT_W
                    && mouseY >= inputY && mouseY < inputY + 18) {
                focusedAttributeId = control.id();
                return true;
            }
        }
        focusedAttributeId = null;
        return false;
    }

    private void updateAttributeValue(int index, double value) {
        MobAttributeControl control = attributeControls.get(index);
        double sanitized = control.type() == MobAttributeControl.ControlType.BOOLEAN
                ? value > 0.0 ? 1.0 : 0.0
                : value;
        attributeControls.set(index, new MobAttributeControl(control.id(), control.descriptionKey(), control.source(),
                control.type(), sanitized, control.defaultValue(), control.minValue(), control.maxValue(), true));
        attributeInputs.put(control.id(), formatInputValue(sanitized, control.type()));
    }

    private void resetAttribute(int index) {
        MobAttributeControl control = attributeControls.get(index);
        attributeControls.set(index, new MobAttributeControl(control.id(), control.descriptionKey(), control.source(),
                control.type(), control.defaultValue(), control.defaultValue(), control.minValue(), control.maxValue(), false));
        attributeInputs.put(control.id(), formatInputValue(control.defaultValue(), control.type()));
        if (control.id().equals(focusedAttributeId)) {
            focusedAttributeId = null;
        }
    }

    private void resetAllAttributes() {
        for (int i = 0; i < attributeControls.size(); i++) {
            resetAttribute(i);
        }
    }

    private int modifiedAttributeCount() {
        int count = 0;
        for (MobAttributeControl control : attributeControls) {
            if (control.overridden()) {
                count++;
            }
        }
        return count;
    }

    private boolean handleHeaderResetClick(double mouseX, double mouseY) {
        int x = panelRight - 14 - HEADER_RESET_W;
        int y = panelTop + 15;
        if (mouseX >= x && mouseX < x + HEADER_RESET_W && mouseY >= y && mouseY < y + 18) {
            resetAllAttributes();
            return true;
        }
        return false;
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        int tabY = panelTop + HEADER_HEIGHT - TAB_HEIGHT - 6;
        int tabWidth = (panelRight - panelLeft - PANEL_INSET * 2 - 4) / 2;
        int firstTabX = panelLeft + PANEL_INSET;
        if (mouseY < tabY || mouseY >= tabY + TAB_HEIGHT) {
            return false;
        }
        if (mouseX >= firstTabX && mouseX < firstTabX + tabWidth) {
            setActiveTab(DetailTab.SPAWN_RULES);
            return true;
        }
        int secondTabX = firstTabX + tabWidth + 4;
        if (mouseX >= secondTabX && mouseX < secondTabX + tabWidth) {
            setActiveTab(DetailTab.ATTRIBUTES);
            return true;
        }
        return false;
    }

    private void setActiveTab(DetailTab tab) {
        if (activeTab == tab) {
            return;
        }
        activeTab = tab;
        focusedAttributeId = null;
        scrollOffset = 0;
        updateContentHeight();
    }

    @Override
    public void onRulesReceived(java.util.Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules) {
        parent.onRulesReceived(rules);
        editRules.clear();
        for (MobSpawnType spawnType : spawnTypes) {
            editRules.put(spawnType, true);
        }
        EnumMap<MobSpawnType, Boolean> existing = parent.getRules().get(mobId);
        if (existing != null) {
            editRules.putAll(existing);
        }
    }

    @Override
    public void onAttributesReceived(ResourceLocation mobId,
                                     List<MobAttributeControl> controls) {
        if (!this.mobId.equals(mobId)) {
            return;
        }
        attributeControls.clear();
        attributeControls.addAll(controls);
        attributeInputs.clear();
        for (MobAttributeControl control : controls) {
            attributeInputs.put(control.id(), formatInputValue(control.value(), control.type()));
        }
        attributesLoaded = true;
        updateContentHeight();
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            int visibleHeight = listBottom - listTop;
            int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
            int trackHeight = visibleHeight - scrollBarH;
            if (trackHeight > 0) {
                scrollOffset = Math.max(0,
                        Math.min(dragStartOffset + (mouseY - dragStartY) / trackHeight * getMaxScroll(), getMaxScroll()));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedAttributeId != null && isNumericInputChar(codePoint)) {
            updateFocusedAttributeInput(attributeInputs.getOrDefault(focusedAttributeId, "") + codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedAttributeId != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String value = attributeInputs.getOrDefault(focusedAttributeId, "");
                if (!value.isEmpty()) {
                    updateFocusedAttributeInput(value.substring(0, value.length() - 1));
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                updateFocusedAttributeInput("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focusedAttributeId = null;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateFocusedAttributeInput(String value) {
        if (focusedAttributeId == null) {
            return;
        }
        attributeInputs.put(focusedAttributeId, value);
        for (int i = 0; i < attributeControls.size(); i++) {
            MobAttributeControl control = attributeControls.get(i);
            if (!control.id().equals(focusedAttributeId)) {
                continue;
            }
            Double parsed = parseInputValue(value, control.type());
            if (parsed != null) {
                attributeControls.set(i, new MobAttributeControl(control.id(), control.descriptionKey(),
                        control.source(), control.type(), parsed, control.defaultValue(),
                        control.minValue(), control.maxValue(), true));
            }
            return;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * 16, getMaxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
