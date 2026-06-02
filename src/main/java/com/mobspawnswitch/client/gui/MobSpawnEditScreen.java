package com.mobspawnswitch.client.gui;

import com.mobspawnswitch.network.NetworkHandler;
import com.mobspawnswitch.network.ServerboundToggleSpawnPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class MobSpawnEditScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int TOGGLE_W = 32;
    private static final int TOGGLE_H = 14;
    private static final int HEADER_HEIGHT = 50;

    private final MobSpawnSwitchScreen parent;
    private final ResourceLocation mobId;
    private final EntityType<?> entityType;

    private final EnumMap<MobSpawnType, Boolean> editRules = new EnumMap<>(MobSpawnType.class);
    private final MobSpawnType[] spawnTypes = MobSpawnType.values();

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

    public MobSpawnEditScreen(MobSpawnSwitchScreen parent, ResourceLocation mobId) {
        super(Component.literal(mobId.toString()));
        this.parent = parent;
        this.mobId = mobId;
        this.entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);

        for (MobSpawnType st : spawnTypes) {
            editRules.put(st, true);
        }
        EnumMap<MobSpawnType, Boolean> existing = parent.getRules().get(mobId);
        if (existing != null) {
            editRules.putAll(existing);
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = Math.min(this.width - 60, 280);
        int panelHeight = Math.min(this.height - 40, HEADER_HEIGHT + spawnTypes.length * ROW_HEIGHT + 40);
        panelLeft = (this.width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = (this.height - panelHeight) / 2;
        panelBottom = panelTop + panelHeight;

        listTop = panelTop + HEADER_HEIGHT;
        listBottom = panelBottom - 30;

        contentHeight = spawnTypes.length * ROW_HEIGHT;

        int btnWidth = 60;
        int btnGap = 10;
        int totalBtnWidth = btnWidth * 2 + btnGap;
        int btnStartX = panelLeft + (panelWidth - totalBtnWidth) / 2;
        int btnY = panelBottom - 26;

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(btnStartX, btnY, btnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> {
            saveAndClose();
        }).bounds(btnStartX + btnWidth + btnGap, btnY, btnWidth, 20).build());
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - (listBottom - listTop));
    }

    private void saveAndClose() {
        for (MobSpawnType type : spawnTypes) {
            Boolean val = editRules.get(type);
            if (val != null) {
                NetworkHandler.CHANNEL.sendToServer(
                        new ServerboundToggleSpawnPacket(mobId, type.name().toLowerCase(Locale.ROOT), val));
            }
        }

        EnumMap<MobSpawnType, Boolean> parentMap = parent.getRules()
                .computeIfAbsent(mobId, k -> new EnumMap<>(MobSpawnType.class));
        parentMap.clear();
        parentMap.putAll(editRules);

        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC222222);

        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF555555);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF555555);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF555555);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF555555);

        int headerY = panelTop + 6;
        int iconSize = 24;
        int iconX = panelLeft + 12 + iconSize / 2;
        int iconY = headerY + iconSize / 2 + 2;
        if (entityType != null) {
            MobSpawnSwitchScreen.renderEntityIcon(guiGraphics, entityType, iconX, iconY, iconSize, parent.getEntityCache());
        }

        int textX = panelLeft + 12 + iconSize + 8;
        String mainName;
        String subName;
        if (parent.isShowTranslatedName() && entityType != null) {
            mainName = entityType.getDescription().getString();
            subName = mobId.toString();
        } else {
            mainName = mobId.toString();
            subName = entityType != null ? entityType.getDescription().getString() : "";
        }
        guiGraphics.drawString(this.font, mainName, textX, headerY + 4, 0xFFFFFF);
        if (!subName.isEmpty()) {
            guiGraphics.drawString(this.font, subName, textX, headerY + 16, 0xAAAAAA);
        }

        boolean allTrue = true;
        for (MobSpawnType st : spawnTypes) {
            Boolean v = editRules.get(st);
            if (v == null || !v) {
                allTrue = false;
                break;
            }
        }

        int allToggleX = panelRight - 12 - TOGGLE_W;
        int allToggleY = headerY + 10;
        drawToggle(guiGraphics, allToggleX, allToggleY, allTrue, mouseX, mouseY);
        guiGraphics.drawString(this.font, "ALL", allToggleX - font.width("ALL") - 4, allToggleY + (TOGGLE_H - font.lineHeight) / 2, 0xCCCCCC);

        guiGraphics.fill(panelLeft + 4, listTop - 1, panelRight - 4, listTop, 0xFF444444);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int visibleHeight = listBottom - listTop;

        guiGraphics.enableScissor(panelLeft + 4, listTop, panelRight - 4, listBottom);

        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < spawnTypes.length; i++) {
            MobSpawnType spawnType = spawnTypes[i];
            int rowY = y + i * ROW_HEIGHT;

            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            if (i % 2 == 0) {
                guiGraphics.fill(panelLeft + 4, rowY, panelRight - 4, rowY + ROW_HEIGHT, 0x15FFFFFF);
            }

            String typeName = spawnType.name().toLowerCase(Locale.ROOT);
            int textYPos = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            guiGraphics.drawString(this.font, typeName, panelLeft + 14, textYPos, 0xDDDDDD);

            Boolean val = editRules.get(spawnType);
            boolean toggleState = val == null || val;

            int toggleX = panelRight - 14 - TOGGLE_W;
            int toggleY = rowY + (ROW_HEIGHT - TOGGLE_H) / 2;
            drawToggle(guiGraphics, toggleX, toggleY, toggleState, mouseX, mouseY);
        }

        guiGraphics.disableScissor();

        if (contentHeight > visibleHeight) {
            int scrollBarX = panelRight - 7;
            int scrollBarW = 3;
            int scrollBarH = Math.max(15, (int) ((double) visibleHeight * visibleHeight / contentHeight));
            int maxScroll = getMaxScroll();
            int scrollBarY = listTop + (maxScroll > 0 ? (int) ((double) scrollOffset / maxScroll * (visibleHeight - scrollBarH)) : 0);
            guiGraphics.fill(scrollBarX, listTop, scrollBarX + scrollBarW, listBottom, 0x30FFFFFF);
            guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarW, scrollBarY + scrollBarH, 0x99FFFFFF);
        }

        guiGraphics.fill(panelLeft + 4, listBottom, panelRight - 4, listBottom + 1, 0xFF444444);
    }

    private void drawToggle(GuiGraphics guiGraphics, int x, int y, boolean state, int mouseX, int mouseY) {
        int bgColor = state ? 0xFF00AA00 : 0xFFAA0000;

        boolean hovered = mouseX >= x && mouseX < x + TOGGLE_W && mouseY >= y && mouseY < y + TOGGLE_H;
        if (hovered) {
            bgColor = brighten(bgColor);
        }

        guiGraphics.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, bgColor);

        int knobSize = TOGGLE_H - 4;
        int knobX = state ? x + TOGGLE_W - knobSize - 2 : x + 2;
        int knobY = y + 2;
        guiGraphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFEEEEEE);

        String label = state ? "ON" : "OFF";
        int labelX = state ? x + 4 : x + TOGGLE_W - font.width(label) - 4;
        guiGraphics.drawString(this.font, label, labelX, y + (TOGGLE_H - font.lineHeight) / 2, 0xFFFFFF);
    }

    private static int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 30);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 30);
        int b = Math.min(255, (color & 0xFF) + 30);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int allToggleX = panelRight - 12 - TOGGLE_W;
        int allToggleY = panelTop + 6 + 10;
        if (mouseX >= allToggleX && mouseX < allToggleX + TOGGLE_W
                && mouseY >= allToggleY && mouseY < allToggleY + TOGGLE_H) {
            boolean allTrue = true;
            for (MobSpawnType st : spawnTypes) {
                Boolean v = editRules.get(st);
                if (v == null || !v) {
                    allTrue = false;
                    break;
                }
            }
            boolean newVal = !allTrue;
            for (MobSpawnType st : spawnTypes) {
                editRules.put(st, newVal);
            }
            return true;
        }

        int visibleHeight = listBottom - listTop;
        if (contentHeight > visibleHeight) {
            int scrollBarX = panelRight - 7;
            int scrollBarW = 3;
            int scrollBarH = Math.max(15, (int) ((double) visibleHeight * visibleHeight / contentHeight));
            int maxScroll = getMaxScroll();
            int scrollBarY = listTop + (maxScroll > 0 ? (int) ((double) scrollOffset / maxScroll * (visibleHeight - scrollBarH)) : 0);
            if (mouseX >= scrollBarX && mouseX < scrollBarX + scrollBarW
                    && mouseY >= scrollBarY && mouseY < scrollBarY + scrollBarH) {
                draggingScrollbar = true;
                dragStartY = mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
        }

        int y = listTop - (int) scrollOffset;
        for (int i = 0; i < spawnTypes.length; i++) {
            int rowY = y + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            int toggleX = panelRight - 14 - TOGGLE_W;
            int toggleY = rowY + (ROW_HEIGHT - TOGGLE_H) / 2;

            if (mouseX >= toggleX && mouseX < toggleX + TOGGLE_W
                    && mouseY >= toggleY && mouseY < toggleY + TOGGLE_H) {
                MobSpawnType spawnType = spawnTypes[i];
                Boolean current = editRules.get(spawnType);
                boolean currentState = current == null || current;
                editRules.put(spawnType, !currentState);
                return true;
            }
        }

        return false;
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
            int scrollBarH = Math.max(15, (int) ((double) visibleHeight * visibleHeight / contentHeight));
            int trackHeight = visibleHeight - scrollBarH;
            if (trackHeight > 0) {
                double deltaY = mouseY - dragStartY;
                int maxScroll = getMaxScroll();
                scrollOffset = Math.max(0, Math.min(dragStartOffset + deltaY / trackHeight * maxScroll, maxScroll));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= listTop && mouseY <= listBottom) {
            int maxScroll = getMaxScroll();
            scrollOffset = Math.max(0, Math.min(scrollOffset - delta * 16, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
