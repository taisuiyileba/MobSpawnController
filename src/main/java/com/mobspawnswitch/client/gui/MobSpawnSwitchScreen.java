package com.mobspawnswitch.client.gui;

import com.mobspawnswitch.network.ClientboundSyncRulesPacket;
import com.mobspawnswitch.network.NetworkHandler;
import com.mobspawnswitch.network.ServerboundRequestRulesPacket;
import com.mobspawnswitch.network.ServerboundToggleSpawnPacket;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.stream.Collectors;

public class MobSpawnSwitchScreen extends Screen implements ClientboundSyncRulesPacket.RuleSyncReceiver {

    private static final int ROW_HEIGHT = 48;
    private static final int PADDING = 4;
    private static final int BUTTON_W = 14;
    private static final int BUTTON_H = 14;

    private EditBox searchBox;
    private Button displayModeButton;
    private boolean showTranslatedName = false;

    private Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules = new HashMap<>();
    private List<ResourceLocation> allMobIds = new ArrayList<>();
    private List<ResourceLocation> filteredMobIds = new ArrayList<>();

    private double scrollOffset = 0;
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
        int panelWidth = Math.min(this.width - 40, 420);
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

        allMobIds = ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.toList());

        applyFilter();

        NetworkHandler.CHANNEL.sendToServer(new ServerboundRequestRulesPacket());
    }

    @Override
    public void onRulesReceived(Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> newRules) {
        this.rules = new HashMap<>(newRules);
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
            int iconSize = 20;
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
            guiGraphics.drawString(this.font, displayName, textX, rowY + 4, 0xFFFFFF);

            String idOrName;
            if (showTranslatedName) {
                idOrName = mobId.toString();
            } else if (entityType != null) {
                idOrName = entityType.getDescription().getString();
            } else {
                idOrName = "";
            }
            if (!idOrName.isEmpty()) {
                guiGraphics.drawString(this.font, idOrName, textX, rowY + 16, 0xAAAAAA);
            }

            int btnY = rowY + ROW_HEIGHT - BUTTON_H - 4;
            int btnX = textX;

            for (MobSpawnType spawnType : spawnTypes) {
                Boolean allowed = mobRules != null ? mobRules.get(spawnType) : null;
                int color;
                String label;
                if (allowed == null) {
                    color = 0xFF555555;
                    label = "-";
                } else if (allowed) {
                    color = 0xFF00AA00;
                    label = "\u2713";
                } else {
                    color = 0xFFAA0000;
                    label = "\u2717";
                }

                guiGraphics.fill(btnX, btnY, btnX + BUTTON_W, btnY + BUTTON_H, color);
                guiGraphics.drawCenteredString(this.font, label, btnX + BUTTON_W / 2, btnY + 3, 0xFFFFFF);

                if (mouseX >= btnX && mouseX < btnX + BUTTON_W && mouseY >= btnY && mouseY < btnY + BUTTON_H) {
                    guiGraphics.renderTooltip(this.font,
                            Component.literal(spawnType.name().toLowerCase(Locale.ROOT) + ": " +
                                    (allowed == null ? "default" : allowed.toString())),
                            mouseX, mouseY);
                }

                btnX += BUTTON_W + 2;
            }

            int allBtnX = btnX + 4;
            boolean allDisabled = true;
            if (mobRules != null) {
                for (MobSpawnType st : spawnTypes) {
                    Boolean v = mobRules.get(st);
                    if (v == null || v) {
                        allDisabled = false;
                        break;
                    }
                }
            } else {
                allDisabled = false;
            }

            int allColor = allDisabled ? 0xFFAA0000 : 0xFF00AA00;
            String allLabel = "ALL";
            guiGraphics.fill(allBtnX, btnY, allBtnX + 24, btnY + BUTTON_H, allColor);
            guiGraphics.drawCenteredString(this.font, allLabel, allBtnX + 12, btnY + 3, 0xFFFFFF);

            if (mouseX >= allBtnX && mouseX < allBtnX + 24 && mouseY >= btnY && mouseY < btnY + BUTTON_H) {
                guiGraphics.renderTooltip(this.font,
                        Component.literal(allDisabled ? "Enable all spawn types" : "Disable all spawn types"),
                        mouseX, mouseY);
            }
        }

        guiGraphics.disableScissor();

        if (contentHeight > visibleHeight) {
            int scrollBarX = listRight - 4;
            int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
            int maxScroll = contentHeight - visibleHeight;
            int scrollBarY = listTop + (int) ((double) scrollOffset / maxScroll * (visibleHeight - scrollBarH));
            guiGraphics.fill(scrollBarX, listTop, scrollBarX + 4, listBottom, 0x40FFFFFF);
            guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 4, scrollBarY + scrollBarH, 0xAAFFFFFF);
        }
    }

    private void renderEntityIcon(GuiGraphics guiGraphics, EntityType<?> entityType, int x, int y, int size) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int y = listTop - (int) scrollOffset;
        MobSpawnType[] spawnTypes = MobSpawnType.values();

        for (int idx = 0; idx < filteredMobIds.size(); idx++) {
            ResourceLocation mobId = filteredMobIds.get(idx);
            int rowY = y + idx * ROW_HEIGHT;

            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
            int iconSize = 20;
            int textX = listLeft + PADDING + iconSize + 6;
            int btnY = rowY + ROW_HEIGHT - BUTTON_H - 4;
            int btnX = textX;

            EnumMap<MobSpawnType, Boolean> mobRules = rules.get(mobId);

            for (MobSpawnType spawnType : spawnTypes) {
                if (mouseX >= btnX && mouseX < btnX + BUTTON_W && mouseY >= btnY && mouseY < btnY + BUTTON_H) {
                    Boolean current = mobRules != null ? mobRules.get(spawnType) : null;
                    boolean newValue;
                    if (current == null) {
                        newValue = false;
                    } else {
                        newValue = !current;
                    }
                    NetworkHandler.CHANNEL.sendToServer(
                            new ServerboundToggleSpawnPacket(mobId, spawnType.name().toLowerCase(Locale.ROOT), newValue));
                    if (mobRules == null) {
                        mobRules = new EnumMap<>(MobSpawnType.class);
                        rules.put(mobId, mobRules);
                    }
                    mobRules.put(spawnType, newValue);
                    return true;
                }
                btnX += BUTTON_W + 2;
            }

            int allBtnX = btnX + 4;
            if (mouseX >= allBtnX && mouseX < allBtnX + 24 && mouseY >= btnY && mouseY < btnY + BUTTON_H) {
                boolean allDisabled = true;
                if (mobRules != null) {
                    for (MobSpawnType st : spawnTypes) {
                        Boolean v = mobRules.get(st);
                        if (v == null || v) {
                            allDisabled = false;
                            break;
                        }
                    }
                } else {
                    allDisabled = false;
                }

                boolean newValue = allDisabled;
                NetworkHandler.CHANNEL.sendToServer(
                        new ServerboundToggleSpawnPacket(mobId, "all", newValue));
                if (mobRules == null) {
                    mobRules = new EnumMap<>(MobSpawnType.class);
                    rules.put(mobId, mobRules);
                }
                for (MobSpawnType st : spawnTypes) {
                    mobRules.put(st, newValue);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            int visibleHeight = listBottom - listTop;
            int maxScroll = Math.max(0, contentHeight - visibleHeight);
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
