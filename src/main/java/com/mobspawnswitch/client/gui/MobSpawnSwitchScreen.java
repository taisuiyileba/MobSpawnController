package com.mobspawnswitch.client.gui;

import com.mobspawnswitch.network.ClientboundSyncRulesPacket;
import com.mobspawnswitch.network.NetworkHandler;
import com.mobspawnswitch.network.ServerboundRequestRulesPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.stream.Collectors;

public class MobSpawnSwitchScreen extends Screen implements ClientboundSyncRulesPacket.RuleSyncReceiver {

    private static final int ROW_HEIGHT = 28;
    private static final int PADDING = 4;
    private static final int EDIT_BTN_W = 36;
    private static final int EDIT_BTN_H = 16;
    private static final int STATUS_SIZE = 8;

    private EditBox searchBox;
    private Button displayModeButton;
    private boolean showTranslatedName = false;

    private Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules = new HashMap<>();
    private List<ResourceLocation> allMobIds = new ArrayList<>();
    private List<ResourceLocation> filteredMobIds = new ArrayList<>();

    private double scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private double dragStartY = 0;
    private double dragStartOffset = 0;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int contentHeight;

    private final Map<EntityType<?>, Entity> entityCache = new HashMap<>();

    public MobSpawnSwitchScreen() {
        super(Component.literal("MobSpawnSwitch"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int panelWidth = Math.min(this.width - 40, 380);
        listLeft = centerX - panelWidth / 2;
        listRight = centerX + panelWidth / 2;
        listTop = 52;
        listBottom = this.height - 10;

        int searchWidth = panelWidth - 90;
        searchBox = new EditBox(this.font, listLeft, 28, searchWidth, 18, Component.literal(""));
        searchBox.setMaxLength(128);
        searchBox.setResponder(text -> applyFilter());
        this.addRenderableWidget(searchBox);

        displayModeButton = Button.builder(Component.literal("ID"), btn -> {
            showTranslatedName = !showTranslatedName;
            btn.setMessage(Component.literal(showTranslatedName ? "Name" : "ID"));
            applyFilter();
        }).bounds(listLeft + searchWidth + 4, 28, 82, 18).build();
        this.addRenderableWidget(displayModeButton);

        allMobIds = ForgeRegistries.ENTITY_TYPES.getEntries().stream()
                .filter(entry -> {
                    MobCategory category = entry.getValue().getCategory();
                    return category != MobCategory.MISC;
                })
                .map(entry -> entry.getKey().location())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.toList());

        applyFilter();

        NetworkHandler.CHANNEL.sendToServer(new ServerboundRequestRulesPacket());
    }

    @Override
    public void onRulesReceived(Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> newRules) {
        this.rules = new HashMap<>(newRules);
    }

    public Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> getRules() {
        return rules;
    }

    public boolean isShowTranslatedName() {
        return showTranslatedName;
    }

    public Map<EntityType<?>, Entity> getEntityCache() {
        return entityCache;
    }

    private void applyFilter() {
        String query = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            filteredMobIds = new ArrayList<>(allMobIds);
        } else {
            filteredMobIds = allMobIds.stream()
                    .filter(id -> {
                        if (id.toString().toLowerCase(Locale.ROOT).contains(query)) {
                            return true;
                        }
                        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
                        if (type != null) {
                            String translated = type.getDescription().getString().toLowerCase(Locale.ROOT);
                            if (translated.contains(query)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        contentHeight = filteredMobIds.size() * ROW_HEIGHT;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, contentHeight - (listBottom - listTop))));
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - (listBottom - listTop));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int visibleHeight = listBottom - listTop;

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);

        int y = listTop - (int) scrollOffset;
        MobSpawnType[] spawnTypes = MobSpawnType.values();

        for (int idx = 0; idx < filteredMobIds.size(); idx++) {
            ResourceLocation mobId = filteredMobIds.get(idx);
            int rowY = y + idx * ROW_HEIGHT;

            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            EnumMap<MobSpawnType, Boolean> mobRules = rules.get(mobId);

            boolean hasAnyRule = mobRules != null && !mobRules.isEmpty();
            int bgColor = hasAnyRule ? 0x30FFAA00 : 0x20FFFFFF;
            guiGraphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 1, bgColor);

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
            int iconSize = 18;
            int iconX = listLeft + PADDING + iconSize / 2;
            int iconY = rowY + ROW_HEIGHT / 2;
            if (entityType != null) {
                renderEntityIcon(guiGraphics, entityType, iconX, iconY, iconSize);
            }

            String displayName;
            if (showTranslatedName && entityType != null) {
                displayName = entityType.getDescription().getString();
            } else {
                displayName = mobId.toString();
            }
            int textX = listLeft + PADDING + iconSize + 6;
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            guiGraphics.drawString(this.font, displayName, textX, textY, 0xFFFFFF);

            int editBtnX = listRight - EDIT_BTN_W - PADDING - 6;
            int editBtnY = rowY + (ROW_HEIGHT - EDIT_BTN_H) / 2;
            boolean hovered = mouseX >= editBtnX && mouseX < editBtnX + EDIT_BTN_W
                    && mouseY >= editBtnY && mouseY < editBtnY + EDIT_BTN_H;
            int btnColor = hovered ? 0xFF4488CC : 0xFF336699;
            guiGraphics.fill(editBtnX, editBtnY, editBtnX + EDIT_BTN_W, editBtnY + EDIT_BTN_H, btnColor);
            guiGraphics.drawCenteredString(this.font, "\u270E", editBtnX + EDIT_BTN_W / 2, editBtnY + (EDIT_BTN_H - font.lineHeight) / 2 + 1, 0xFFFFFF);

            int statusX = editBtnX - STATUS_SIZE - 8;
            int statusY = rowY + (ROW_HEIGHT - STATUS_SIZE) / 2;
            int statusColor;
            if (!hasAnyRule) {
                statusColor = 0xFF00CC00;
            } else {
                boolean allEnabled = true;
                boolean allDisabled = true;
                for (MobSpawnType st : spawnTypes) {
                    Boolean v = mobRules.get(st);
                    boolean effective = v == null || v;
                    if (!effective) allEnabled = false;
                    if (effective) allDisabled = false;
                }
                if (allEnabled) {
                    statusColor = 0xFF00CC00;
                } else if (allDisabled) {
                    statusColor = 0xFFCC0000;
                } else {
                    statusColor = 0xFFFFAA00;
                }
            }
            guiGraphics.fill(statusX, statusY, statusX + STATUS_SIZE, statusY + STATUS_SIZE, statusColor);
        }

        guiGraphics.disableScissor();

        if (contentHeight > visibleHeight) {
            int scrollBarX = listRight - 6;
            int scrollBarW = 5;
            int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
            int maxScroll = getMaxScroll();
            int scrollBarY = listTop + (maxScroll > 0 ? (int) ((double) scrollOffset / maxScroll * (visibleHeight - scrollBarH)) : 0);
            guiGraphics.fill(scrollBarX, listTop, scrollBarX + scrollBarW, listBottom, 0x40FFFFFF);
            guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarW, scrollBarY + scrollBarH, 0xAAFFFFFF);
        }
    }

    static void renderEntityIcon(GuiGraphics guiGraphics, EntityType<?> entityType, int x, int y, int size, Map<EntityType<?>, Entity> entityCache) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        try {
            Entity entity = entityCache.computeIfAbsent(entityType, type -> {
                try {
                    return type.create(mc.level);
                } catch (Exception e) {
                    return null;
                }
            });

            if (entity == null) return;

            float entitySize = Math.max(entity.getBbWidth(), entity.getBbHeight());
            float scale = entitySize > 0 ? (size * 0.4f) / entitySize : size * 0.4f;
            scale = Math.min(scale, size * 0.5f);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 50);
            guiGraphics.pose().mulPoseMatrix(new Matrix4f().scaling(scale, -scale, scale));
            guiGraphics.pose().mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(210), (float) Math.toRadians(-15), 0));

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> {
                dispatcher.render(entity, 0, 0, 0, 0, 1.0f, guiGraphics.pose(), guiGraphics.bufferSource(), 0xF000F0);
            });
            guiGraphics.bufferSource().endBatch();
            dispatcher.setRenderShadow(true);

            guiGraphics.pose().popPose();
        } catch (Exception ignored) {
        }
    }

    private void renderEntityIcon(GuiGraphics guiGraphics, EntityType<?> entityType, int x, int y, int size) {
        renderEntityIcon(guiGraphics, entityType, x, y, size, entityCache);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int visibleHeight = listBottom - listTop;
        if (contentHeight > visibleHeight) {
            int scrollBarX = listRight - 6;
            int scrollBarW = 5;
            int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
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

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int y = listTop - (int) scrollOffset;

        for (int idx = 0; idx < filteredMobIds.size(); idx++) {
            ResourceLocation mobId = filteredMobIds.get(idx);
            int rowY = y + idx * ROW_HEIGHT;

            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            int editBtnX = listRight - EDIT_BTN_W - PADDING - 6;
            int editBtnY = rowY + (ROW_HEIGHT - EDIT_BTN_H) / 2;

            if (mouseX >= editBtnX && mouseX < editBtnX + EDIT_BTN_W
                    && mouseY >= editBtnY && mouseY < editBtnY + EDIT_BTN_H) {
                Minecraft.getInstance().setScreen(new MobSpawnEditScreen(this, mobId));
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
            int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
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
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int maxScroll = getMaxScroll();
            scrollOffset = Math.max(0, Math.min(scrollOffset - delta * 20, maxScroll));
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
        entityCache.clear();
        super.onClose();
    }
}
