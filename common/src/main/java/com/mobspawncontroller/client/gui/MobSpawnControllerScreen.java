package com.mobspawncontroller.client.gui;

import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.platform.NetworkBridge;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MobSpawnControllerScreen extends Screen implements ClientRuleSync.Receiver {

    private static final int ROW_HEIGHT = 24;
    private static final int PADDING = 4;
    private static final int EDIT_BTN_W = 20;
    private static final int EDIT_BTN_H = 16;

    private EditBox searchBox;
    private boolean showTranslatedName = false;
    private boolean modDropdownOpen = false;
    private boolean statusDropdownOpen = false;
    private boolean attributeDropdownOpen = false;
    private String selectedMod = "gui.mobspawncontroller.filter.all_mods";
    private String selectedStatus = "gui.mobspawncontroller.filter.all_status";
    private String selectedAttributeStatus = "gui.mobspawncontroller.filter.all_attributes";
    private final List<String> availableMods = new ArrayList<>();
    private final List<String> statusOptions = Arrays.asList(
            "gui.mobspawncontroller.filter.all_status",
            "gui.mobspawncontroller.filter.enabled",
            "gui.mobspawncontroller.filter.partially_enabled",
            "gui.mobspawncontroller.filter.disabled"
    );
    private final List<String> attributeStatusOptions = Arrays.asList(
            "gui.mobspawncontroller.filter.all_attributes",
            "gui.mobspawncontroller.filter.attributes_modified",
            "gui.mobspawncontroller.filter.attributes_unmodified"
    );

    private Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules = new HashMap<>();
    private Set<ResourceLocation> attributeModifiedMobIds = new HashSet<>();
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

    public MobSpawnControllerScreen() {
        super(Component.literal("MobSpawnController"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelWidth = Math.min(this.width - 32, 440);
        listLeft = centerX - panelWidth / 2;
        listRight = centerX + panelWidth / 2;
        listTop = 78;
        listBottom = this.height - 10;

        int searchWidth = panelWidth - 92;
        searchBox = new EditBox(this.font, listLeft, 29, searchWidth, 18, Component.empty());
        searchBox.setMaxLength(128);
        searchBox.setResponder(text -> applyFilter());
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("ID"), button -> {
            showTranslatedName = !showTranslatedName;
            button.setMessage(Component.literal(showTranslatedName ? "Name" : "ID"));
            applyFilter();
        }).bounds(listLeft + searchWidth + 4, 28, 84, 20).build());

        allMobIds = BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue().getCategory() != MobCategory.MISC)
                .map(entry -> entry.getKey().location())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.toList());

        Set<String> mods = new HashSet<>();
        for (ResourceLocation id : allMobIds) {
            mods.add(id.getNamespace());
        }
        availableMods.clear();
        availableMods.add("gui.mobspawncontroller.filter.all_mods");
        availableMods.addAll(mods.stream().sorted().toList());

        applyFilter();
        NetworkBridge.sendToServer(new ServerboundRequestRulesPayload());
    }

    @Override
    public void onRulesReceived(Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> newRules) {
        this.rules = new HashMap<>(newRules);
        applyFilter();
    }

    @Override
    public void onAttributeModifiedMobsReceived(Set<ResourceLocation> mobIds) {
        this.attributeModifiedMobIds = new HashSet<>(mobIds);
        applyFilter();
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
        if (searchBox == null) {
            return;
        }
        String query = searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        filteredMobIds = allMobIds.stream()
                .filter(id -> matchesModFilter(id) && matchesStatusFilter(id)
                        && matchesAttributeFilter(id) && matchesQuery(id, query))
                .collect(Collectors.toList());

        contentHeight = filteredMobIds.size() * ROW_HEIGHT;
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    private boolean matchesModFilter(ResourceLocation id) {
        return selectedMod.equals("gui.mobspawncontroller.filter.all_mods") || id.getNamespace().equals(selectedMod);
    }

    private boolean matchesStatusFilter(ResourceLocation id) {
        if (selectedStatus.equals("gui.mobspawncontroller.filter.all_status")) {
            return true;
        }

        EnumMap<MobSpawnType, Boolean> mobRules = rules.get(id);
        boolean hasAnyRule = mobRules != null && !mobRules.isEmpty();
        if (selectedStatus.equals("gui.mobspawncontroller.filter.enabled")) {
            if (!hasAnyRule) {
                return true;
            }
            return mobRules.values().stream().allMatch(Boolean::booleanValue);
        }
        if (selectedStatus.equals("gui.mobspawncontroller.filter.disabled")) {
            return hasAnyRule && mobRules.size() == MobSpawnType.values().length
                    && mobRules.values().stream().noneMatch(Boolean::booleanValue);
        }
        if (selectedStatus.equals("gui.mobspawncontroller.filter.partially_enabled")) {
            if (!hasAnyRule) {
                return false;
            }
            boolean hasEnabled = false;
            boolean hasDisabled = false;
            for (MobSpawnType type : MobSpawnType.values()) {
                boolean effective = mobRules.getOrDefault(type, true);
                hasEnabled |= effective;
                hasDisabled |= !effective;
            }
            return hasEnabled && hasDisabled;
        }
        return true;
    }

    private boolean matchesAttributeFilter(ResourceLocation id) {
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.all_attributes")) {
            return true;
        }
        boolean modified = attributeModifiedMobIds.contains(id);
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.attributes_modified")) {
            return modified;
        }
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.attributes_unmodified")) {
            return !modified;
        }
        return true;
    }

    private boolean matchesQuery(ResourceLocation id, String query) {
        if (query.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type != null && type.getDescription().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - (listBottom - listTop));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int panelWidth = Math.min(this.width - 32, 440);
        int searchWidth = panelWidth - 92;
        int totalDropdownWidth = searchWidth + 88;
        int modDropdownWidth = Math.max(86, totalDropdownWidth / 4);
        int statusDropdownWidth = Math.max(104, totalDropdownWidth / 3);
        int attributeDropdownWidth = totalDropdownWidth - modDropdownWidth - statusDropdownWidth - 8;
        int modDropdownX = listLeft;
        int statusDropdownX = listLeft + modDropdownWidth + 4;
        int attributeDropdownX = statusDropdownX + statusDropdownWidth + 4;
        int dropdownY = 52;
        int dropdownHeight = 18;
        int visibleHeight = listBottom - listTop;

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);
        renderRows(guiGraphics, mouseX, mouseY);
        guiGraphics.disableScissor();

        if (contentHeight > visibleHeight) {
            renderScrollbar(guiGraphics, visibleHeight);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        renderDropdown(guiGraphics, mouseX, mouseY, modDropdownX, dropdownY, modDropdownWidth, dropdownHeight,
                selectedMod, modDropdownOpen, availableMods);
        renderDropdown(guiGraphics, mouseX, mouseY, statusDropdownX, dropdownY, statusDropdownWidth, dropdownHeight,
                selectedStatus, statusDropdownOpen, statusOptions);
        renderDropdown(guiGraphics, mouseX, mouseY, attributeDropdownX, dropdownY, attributeDropdownWidth, dropdownHeight,
                selectedAttributeStatus, attributeDropdownOpen, attributeStatusOptions);
        guiGraphics.pose().popPose();
    }

    private void renderRows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop - (int) scrollOffset;

        for (int idx = 0; idx < filteredMobIds.size(); idx++) {
            ResourceLocation mobId = filteredMobIds.get(idx);
            int rowY = y + idx * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            EnumMap<MobSpawnType, Boolean> mobRules = rules.get(mobId);
            boolean hasAnyRule = mobRules != null && !mobRules.isEmpty();
            boolean hasAttributeOverride = attributeModifiedMobIds.contains(mobId);
            boolean rowHovered = mouseX >= listLeft && mouseX < listRight && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            guiGraphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 1,
                    rowHovered ? 0x2AFFFFFF : hasAnyRule ? 0x24FFAA00 : 0x18FFFFFF);
            if (hasAttributeOverride) {
                guiGraphics.fill(listLeft, rowY, listLeft + 3, rowY + ROW_HEIGHT - 1, 0xFF63B3ED);
            }

            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);
            int iconSize = 20;
            if (entityType != null) {
                renderEntityIcon(guiGraphics, entityType, listLeft + PADDING + iconSize / 2,
                        rowY + ROW_HEIGHT / 2 + 2, iconSize, entityCache);
            }

            String displayName = showTranslatedName && entityType != null
                    ? entityType.getDescription().getString()
                    : mobId.toString();

            int editBtnX = listRight - EDIT_BTN_W - PADDING - 6;
            int editBtnY = rowY + (ROW_HEIGHT - EDIT_BTN_H) / 2;
            int textX = listLeft + PADDING + iconSize + 6;
            int textMaxWidth = editBtnX - textX - 10;
            guiGraphics.drawString(this.font, trimToWidth(displayName, textMaxWidth), textX,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);

            boolean hovered = mouseX >= editBtnX && mouseX < editBtnX + EDIT_BTN_W
                    && mouseY >= editBtnY && mouseY < editBtnY + EDIT_BTN_H;
            guiGraphics.fill(editBtnX, editBtnY, editBtnX + EDIT_BTN_W, editBtnY + EDIT_BTN_H,
                    hovered ? 0xFF4488CC : 0xFF336699);
            guiGraphics.drawCenteredString(this.font, "\u270E", editBtnX + EDIT_BTN_W / 2,
                    editBtnY + (EDIT_BTN_H - font.lineHeight) / 2 + 1, 0xFFFFFF);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int visibleHeight) {
        int scrollBarX = listRight - 6;
        int scrollBarW = 5;
        int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
        int maxScroll = getMaxScroll();
        int scrollBarY = listTop + (maxScroll > 0
                ? (int) (scrollOffset / maxScroll * (visibleHeight - scrollBarH)) : 0);
        guiGraphics.fill(scrollBarX, listTop, scrollBarX + scrollBarW, listBottom, 0x40FFFFFF);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarW, scrollBarY + scrollBarH, 0xAAFFFFFF);
    }

    private void renderDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height,
                                String selected, boolean open, List<String> options) {
        guiGraphics.fill(x, y, x + width, y + height, 0xDD000000);
        guiGraphics.renderOutline(x, y, width, height, open ? 0xFFFFFFFF : 0xFFAAAAAA);

        String text = optionText(selected);
        int maxTextWidth = width - 16;
        if (this.font.width(text) > maxTextWidth) {
            text = this.font.plainSubstrByWidth(text, maxTextWidth - this.font.width("...")) + "...";
        }
        guiGraphics.drawString(this.font, text, x + 4, y + 5, 0xFFFFFF);
        guiGraphics.drawString(this.font, open ? "^" : "v", x + width - 10, y + 5, 0xFFAAAAAA);

        if (!open) {
            return;
        }

        int listH = Math.min(options.size() * 14, selected.equals(selectedMod) ? 140 : options.size() * 14);
        guiGraphics.fill(x, y + height, x + width, y + height + listH, 0xEE000000);
        guiGraphics.renderOutline(x, y + height, width, listH, 0xFFFFFFFF);
        for (int i = 0; i < options.size(); i++) {
            int itemY = y + height + i * 14;
            if (itemY + 14 > y + height + listH) {
                break;
            }
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + 14;
            if (hovered) {
                guiGraphics.fill(x + 1, itemY, x + width - 1, itemY + 14, 0xFF444444);
            }
            String itemText = optionText(options.get(i));
            if (this.font.width(itemText) > maxTextWidth) {
                itemText = this.font.plainSubstrByWidth(itemText, maxTextWidth - this.font.width("...")) + "...";
            }
            guiGraphics.drawString(this.font, itemText, x + 4, itemY + 3, hovered ? 0xFFFFFF : 0xFFDDDDDD);
        }
    }

    private static String optionText(String raw) {
        String text = Component.translatable(raw).getString();
        if (raw.equals(text) && !raw.startsWith("gui.") && !raw.isEmpty()) {
            return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
        }
        return text;
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

    static void renderEntityIcon(GuiGraphics guiGraphics, EntityType<?> entityType, int x, int y, int size,
                                 Map<EntityType<?>, Entity> entityCache) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        boolean posePushed = false;
        EntityRenderDispatcher dispatcher = null;
        try {
            Entity entity = entityCache.computeIfAbsent(entityType, type -> type.create(mc.level));
            if (entity == null) {
                return;
            }

            float entitySize = Math.max(entity.getBbWidth(), entity.getBbHeight());
            float scale = entitySize > 0 ? (size * 0.4f) / entitySize : size * 0.4f;
            scale = Math.min(scale, size * 0.5f);

            guiGraphics.pose().pushPose();
            posePushed = true;
            guiGraphics.pose().translate(x, y, 50);
            guiGraphics.pose().scale(scale, -scale, scale);
            guiGraphics.pose().mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(210),
                    (float) Math.toRadians(-15), 0));

            dispatcher = mc.getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            EntityRenderDispatcher activeDispatcher = dispatcher;
            RenderSystem.enableDepthTest();
            RenderSystem.runAsFancy(() -> activeDispatcher.render(entity, 0, 0, 0, 0, 1.0f,
                    guiGraphics.pose(), guiGraphics.bufferSource(), 0xF000F0));
            guiGraphics.bufferSource().endBatch();
        } catch (Exception ignored) {
        } finally {
            if (dispatcher != null) {
                dispatcher.setRenderShadow(true);
            }
            if (posePushed) {
                guiGraphics.pose().popPose();
            }
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int panelWidth = Math.min(this.width - 32, 440);
        int searchWidth = panelWidth - 92;
        int totalDropdownWidth = searchWidth + 88;
        int modDropdownWidth = Math.max(86, totalDropdownWidth / 4);
        int statusDropdownWidth = Math.max(104, totalDropdownWidth / 3);
        int attributeDropdownWidth = totalDropdownWidth - modDropdownWidth - statusDropdownWidth - 8;
        int modDropdownX = listLeft;
        int statusDropdownX = listLeft + modDropdownWidth + 4;
        int attributeDropdownX = statusDropdownX + statusDropdownWidth + 4;
        int dropdownY = 52;
        int dropdownHeight = 18;

        if (handleDropdownClick(mouseX, mouseY, modDropdownX, dropdownY, modDropdownWidth, dropdownHeight,
                availableMods, DropdownKind.MOD)) {
            return true;
        }
        if (handleDropdownClick(mouseX, mouseY, statusDropdownX, dropdownY, statusDropdownWidth, dropdownHeight,
                statusOptions, DropdownKind.STATUS)) {
            return true;
        }
        if (handleDropdownClick(mouseX, mouseY, attributeDropdownX, dropdownY, attributeDropdownWidth, dropdownHeight,
                attributeStatusOptions, DropdownKind.ATTRIBUTE)) {
            return true;
        }

        if (contentHeight > listBottom - listTop) {
            int scrollBarX = listRight - 6;
            int scrollBarW = 5;
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

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int y = listTop - (int) scrollOffset;
        for (int idx = 0; idx < filteredMobIds.size(); idx++) {
            int rowY = y + idx * ROW_HEIGHT;
            int editBtnX = listRight - EDIT_BTN_W - PADDING - 6;
            int editBtnY = rowY + (ROW_HEIGHT - EDIT_BTN_H) / 2;
            if (mouseX >= editBtnX && mouseX < editBtnX + EDIT_BTN_W
                    && mouseY >= editBtnY && mouseY < editBtnY + EDIT_BTN_H) {
                Minecraft.getInstance().setScreen(new MobSpawnEditScreen(this, filteredMobIds.get(idx)));
                return true;
            }
        }

        return false;
    }

    private enum DropdownKind {
        MOD,
        STATUS,
        ATTRIBUTE
    }

    private boolean handleDropdownClick(double mouseX, double mouseY, int x, int y, int width, int height,
                                        List<String> options, DropdownKind kind) {
        boolean open = isDropdownOpen(kind);
        if (open) {
            int listH = Math.min(options.size() * 14, kind == DropdownKind.MOD ? 140 : options.size() * 14);
            if (mouseX >= x && mouseX < x + width && mouseY >= y + height && mouseY < y + height + listH) {
                int index = (int) ((mouseY - (y + height)) / 14);
                if (index >= 0 && index < options.size()) {
                    setSelectedDropdown(kind, options.get(index));
                    setDropdownOpen(kind, false);
                    applyFilter();
                    return true;
                }
            }
            setDropdownOpen(kind, false);
            return true;
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            modDropdownOpen = kind == DropdownKind.MOD;
            statusDropdownOpen = kind == DropdownKind.STATUS;
            attributeDropdownOpen = kind == DropdownKind.ATTRIBUTE;
            return true;
        }
        return false;
    }

    private boolean isDropdownOpen(DropdownKind kind) {
        return switch (kind) {
            case MOD -> modDropdownOpen;
            case STATUS -> statusDropdownOpen;
            case ATTRIBUTE -> attributeDropdownOpen;
        };
    }

    private void setDropdownOpen(DropdownKind kind, boolean open) {
        switch (kind) {
            case MOD -> modDropdownOpen = open;
            case STATUS -> statusDropdownOpen = open;
            case ATTRIBUTE -> attributeDropdownOpen = open;
        }
    }

    private void setSelectedDropdown(DropdownKind kind, String value) {
        switch (kind) {
            case MOD -> selectedMod = value;
            case STATUS -> selectedStatus = value;
            case ATTRIBUTE -> selectedAttributeStatus = value;
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * 20, getMaxScroll()));
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
        entityCache.clear();
        super.onClose();
    }
}
