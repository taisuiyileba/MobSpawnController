package com.mobspawncontroller.client.gui;

import com.mobspawncontroller.active.ActiveSpawnSettings;
import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.network.ServerboundRequestRulesPayload;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
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
    private static final float ENTITY_ICON_FILL = 0.9f;
    private static final int EDIT_BTN_W = 20;
    private static final int EDIT_BTN_H = 16;
    private static final int PANEL_INSET = 8;
    private static final int HEADER_HEIGHT = 78;
    private static final int FOOTER_HEIGHT = 10;
    private static final int ACCENT_COLOR = 0xFF63B3ED;
    private static final int PANEL_BG = 0xF015171B;
    private static final int HEADER_BG = 0xAA202630;
    private static final int ROW_BG = 0x12FFFFFF;
    private static final int ROW_HOVER_BG = 0x3F63B3ED;
    private static final int RULES_ACCENT_COLOR = 0xFFFFAA00;
    private static final int NATURAL_ACCENT_COLOR = 0xFF34D399;
    private static final int ACTIVE_ACCENT_COLOR = 0xFFC084FC;
    private static final int LOADOUT_ACCENT_COLOR = 0xFFF472B6;
    private static final int DROPDOWN_ITEM_HEIGHT = 14;
    private static final int DROPDOWN_MAX_VISIBLE_ITEMS = 18;
    private static String savedSearchText = "";

    private EditBox searchBox;
    private boolean modDropdownOpen = false;
    private boolean statusDropdownOpen = false;
    private boolean attributeDropdownOpen = false;
    private int modDropdownScrollOffset = 0;
    private int statusDropdownScrollOffset = 0;
    private int attributeDropdownScrollOffset = 0;
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
            "gui.mobspawncontroller.filter.attributes_unmodified",
            "gui.mobspawncontroller.filter.natural_modified",
            "gui.mobspawncontroller.filter.natural_unmodified",
            "gui.mobspawncontroller.filter.active_modified",
            "gui.mobspawncontroller.filter.active_unmodified",
            "gui.mobspawncontroller.filter.loadout_modified",
            "gui.mobspawncontroller.filter.loadout_unmodified"
    );

    private Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> rules = new HashMap<>();
    private Set<ResourceLocation> attributeModifiedMobIds = new HashSet<>();
    private Map<ResourceLocation, NaturalSpawnSettings> naturalSpawnSettings = new HashMap<>();
    private Map<ResourceLocation, ActiveSpawnSettings> activeSpawnSettings = new HashMap<>();
    private Set<ResourceLocation> loadoutMobIds = new HashSet<>();
    private List<ResourceLocation> allMobIds = new ArrayList<>();
    private List<ResourceLocation> filteredMobIds = new ArrayList<>();

    private double scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private double dragStartY = 0;
    private double dragStartOffset = 0;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
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
        int panelWidth = Math.min(this.width - 32, 440);
        int panelHeight = Math.max(170, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = (this.height - panelHeight) / 2;
        panelBottom = panelTop + panelHeight;
        listLeft = panelLeft + PANEL_INSET;
        listRight = panelRight - PANEL_INSET;
        listTop = panelTop + HEADER_HEIGHT;
        listBottom = panelBottom - FOOTER_HEIGHT;

        int searchWidth = panelWidth - PANEL_INSET * 2 - 96;
        int searchY = panelTop + 21;
        searchBox = new EditBox(this.font, listLeft, searchY, searchWidth, 18, Component.empty());
        searchBox.setMaxLength(128);
        searchBox.setValue(savedSearchText);
        searchBox.setResponder(text -> {
            savedSearchText = text;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("\uD83D\uDD0D"), button -> {
            this.setFocused(searchBox);
            searchBox.setFocused(true);
        }).bounds(listLeft + searchWidth + 4, searchY - 1, 92, 20).build());

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

    @Override
    public void onNaturalSpawnSettingsReceived(Map<ResourceLocation, NaturalSpawnSettings> settings) {
        this.naturalSpawnSettings = new HashMap<>(settings);
        applyFilter();
    }

    @Override
    public void onActiveSpawnSettingsReceived(Map<ResourceLocation, ActiveSpawnSettings> settings) {
        this.activeSpawnSettings = new HashMap<>(settings);
        applyFilter();
    }

    @Override
    public void onLoadoutMobsReceived(Set<ResourceLocation> mobIds) {
        this.loadoutMobIds = new HashSet<>(mobIds);
        applyFilter();
    }

    public Map<ResourceLocation, EnumMap<MobSpawnType, Boolean>> getRules() {
        return rules;
    }

    public Map<EntityType<?>, Entity> getEntityCache() {
        return entityCache;
    }

    public Map<ResourceLocation, NaturalSpawnSettings> getNaturalSpawnSettings() {
        return naturalSpawnSettings;
    }

    public Map<ResourceLocation, ActiveSpawnSettings> getActiveSpawnSettings() {
        return activeSpawnSettings;
    }

    public Set<ResourceLocation> getLoadoutMobIds() {
        return loadoutMobIds;
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
        boolean naturalModified = naturalSpawnSettings.containsKey(id);
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.natural_modified")) {
            return naturalModified;
        }
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.natural_unmodified")) {
            return !naturalModified;
        }
        boolean activeModified = activeSpawnSettings.containsKey(id);
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.active_modified")) {
            return activeModified;
        }
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.active_unmodified")) {
            return !activeModified;
        }
        boolean loadoutModified = loadoutMobIds.contains(id);
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.loadout_modified")) {
            return loadoutModified;
        }
        if (selectedAttributeStatus.equals("gui.mobspawncontroller.filter.loadout_unmodified")) {
            return !loadoutModified;
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
        this.renderBackground(guiGraphics);
        renderPanel(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 8, 0xFFFFFFFF);

        int panelWidth = panelRight - panelLeft;
        int searchWidth = panelWidth - PANEL_INSET * 2 - 96;
        int totalDropdownWidth = searchWidth + 92;
        int modDropdownWidth = Math.max(86, totalDropdownWidth / 4);
        int statusDropdownWidth = Math.max(104, totalDropdownWidth / 3);
        int attributeDropdownWidth = totalDropdownWidth - modDropdownWidth - statusDropdownWidth - 8;
        int modDropdownX = listLeft;
        int statusDropdownX = listLeft + modDropdownWidth + 4;
        int attributeDropdownX = statusDropdownX + statusDropdownWidth + 4;
        int dropdownY = panelTop + 50;
        int dropdownHeight = 18;
        int visibleHeight = listBottom - listTop;

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);
        renderRows(guiGraphics, mouseX, mouseY);
        guiGraphics.disableScissor();

        if (contentHeight > visibleHeight) {
            renderScrollbar(guiGraphics, visibleHeight);
        }

        guiGraphics.fill(panelLeft + PANEL_INSET, listTop - 1, panelRight - PANEL_INSET, listTop, 0xFF303742);
        guiGraphics.fill(panelLeft + PANEL_INSET, listBottom, panelRight - PANEL_INSET, listBottom + 1, 0xFF303742);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        renderDropdown(guiGraphics, mouseX, mouseY, modDropdownX, dropdownY, modDropdownWidth, dropdownHeight,
                selectedMod, modDropdownOpen, availableMods, DropdownKind.MOD);
        renderDropdown(guiGraphics, mouseX, mouseY, statusDropdownX, dropdownY, statusDropdownWidth, dropdownHeight,
                selectedStatus, statusDropdownOpen, statusOptions, DropdownKind.STATUS);
        renderDropdown(guiGraphics, mouseX, mouseY, attributeDropdownX, dropdownY, attributeDropdownWidth, dropdownHeight,
                selectedAttributeStatus, attributeDropdownOpen, attributeStatusOptions, DropdownKind.ATTRIBUTE);
        guiGraphics.pose().popPose();
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
            boolean hasNaturalSettings = naturalSpawnSettings.containsKey(mobId);
            boolean hasActiveSettings = activeSpawnSettings.containsKey(mobId);
            boolean hasLoadout = loadoutMobIds.contains(mobId);
            boolean rowHovered = mouseX >= listLeft && mouseX < listRight && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (idx % 2 == 0) {
                guiGraphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 1, ROW_BG);
            }
            if (rowHovered) {
                guiGraphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 1, ROW_HOVER_BG);
            }
            int accentX = listLeft;
            if (hasAttributeOverride) {
                guiGraphics.fill(accentX, rowY, accentX + 2, rowY + ROW_HEIGHT - 1, ACCENT_COLOR);
                accentX += 2;
            }
            if (hasNaturalSettings) {
                guiGraphics.fill(accentX, rowY, accentX + 2, rowY + ROW_HEIGHT - 1, NATURAL_ACCENT_COLOR);
                accentX += 2;
            }
            if (hasActiveSettings) {
                guiGraphics.fill(accentX, rowY, accentX + 2, rowY + ROW_HEIGHT - 1, ACTIVE_ACCENT_COLOR);
                accentX += 2;
            }
            if (hasLoadout) {
                guiGraphics.fill(accentX, rowY, accentX + 2, rowY + ROW_HEIGHT - 1, LOADOUT_ACCENT_COLOR);
                accentX += 2;
            }
            if (hasAnyRule) {
                guiGraphics.fill(accentX, rowY, accentX + 2, rowY + ROW_HEIGHT - 1, RULES_ACCENT_COLOR);
            }

            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);
            int iconSize = 20;
            if (entityType != null) {
                renderEntityIcon(guiGraphics, entityType, listLeft + PADDING + iconSize / 2,
                        rowY + ROW_HEIGHT - 2, iconSize, entityCache);
            }

            String displayName;
            if (entityType != null) {
                displayName = entityType.getDescription().getString() + "/" + mobId;
            } else {
                displayName = mobId.toString();
            }

            int editBtnX = listRight - EDIT_BTN_W - PADDING - 6;
            int editBtnY = rowY + (ROW_HEIGHT - EDIT_BTN_H) / 2;
            int textX = listLeft + PADDING + iconSize + 6;
            int textMaxWidth = editBtnX - textX - 10;
            guiGraphics.drawString(this.font, trimToWidth(displayName, textMaxWidth), textX,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);

            boolean hovered = mouseX >= editBtnX && mouseX < editBtnX + EDIT_BTN_W
                    && mouseY >= editBtnY && mouseY < editBtnY + EDIT_BTN_H;
            guiGraphics.fill(editBtnX, editBtnY, editBtnX + EDIT_BTN_W, editBtnY + EDIT_BTN_H,
                    hovered ? 0xFF2B3442 : 0xFF171C24);
            guiGraphics.renderOutline(editBtnX, editBtnY, EDIT_BTN_W, EDIT_BTN_H,
                    hovered ? ACCENT_COLOR : 0xFF4B5563);
            guiGraphics.drawCenteredString(this.font, "\u270E", editBtnX + EDIT_BTN_W / 2,
                    editBtnY + (EDIT_BTN_H - font.lineHeight) / 2 + 1, hovered ? 0xFFFFFFFF : 0xFFE5E7EB);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int visibleHeight) {
        int scrollBarX = listRight - 8;
        int scrollBarW = 4;
        int scrollBarH = Math.max(20, (int) ((double) visibleHeight * visibleHeight / contentHeight));
        int maxScroll = getMaxScroll();
        int scrollBarY = listTop + (maxScroll > 0
                ? (int) (scrollOffset / maxScroll * (visibleHeight - scrollBarH)) : 0);
        guiGraphics.fill(scrollBarX, listTop, scrollBarX + scrollBarW, listBottom, 0x40000000);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarW, scrollBarY + scrollBarH, 0xAAFFFFFF);
    }

    private void renderDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height,
                                String selected, boolean open, List<String> options, DropdownKind kind) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        guiGraphics.fill(x, y, x + width, y + height, 0xFF111827);
        guiGraphics.renderOutline(x, y, width, height, open ? ACCENT_COLOR : hovered ? 0xFF7DD3FC : 0xFF4B5563);

        String text = optionText(selected);
        int maxTextWidth = width - 16;
        if (this.font.width(text) > maxTextWidth) {
            text = this.font.plainSubstrByWidth(text, maxTextWidth - this.font.width("...")) + "...";
        }
        guiGraphics.drawString(this.font, text, x + 4, y + 5, 0xFFE5E7EB);
        guiGraphics.drawString(this.font, open ? "^" : "v", x + width - 10, y + 5, 0xFF94A3B8);

        if (!open) {
            return;
        }

        int visibleCount = getDropdownVisibleCount(y, height, options.size());
        int scrollOffset = clampDropdownScrollOffset(kind, options.size(), visibleCount);
        int listH = visibleCount * DROPDOWN_ITEM_HEIGHT;
        guiGraphics.fill(x, y + height, x + width, y + height + listH, 0xEE111827);
        guiGraphics.renderOutline(x, y + height, width, listH, 0xFF4B5563);
        boolean scrollable = options.size() > visibleCount;
        int itemTextWidth = maxTextWidth - (scrollable ? 4 : 0);
        for (int slot = 0; slot < visibleCount; slot++) {
            int optionIndex = scrollOffset + slot;
            if (optionIndex >= options.size()) break;
            int itemY = y + height + slot * DROPDOWN_ITEM_HEIGHT;
            boolean itemHovered = mouseX >= x && mouseX < x + width
                    && mouseY >= itemY && mouseY < itemY + DROPDOWN_ITEM_HEIGHT;
            if (itemHovered) {
                guiGraphics.fill(x + 1, itemY, x + width - 1, itemY + DROPDOWN_ITEM_HEIGHT, 0xFF444444);
            }
            String itemText = optionText(options.get(optionIndex));
            if (this.font.width(itemText) > itemTextWidth) {
                itemText = this.font.plainSubstrByWidth(itemText,
                        Math.max(0, itemTextWidth - this.font.width("..."))) + "...";
            }
            guiGraphics.drawString(this.font, itemText, x + 4, itemY + 3, itemHovered ? 0xFFFFFFFF : 0xFFB6C2D0);
        }

        if (scrollable) {
            int trackX = x + width - 4;
            int trackY = y + height + 1;
            int trackHeight = Math.max(1, listH - 2);
            int thumbHeight = Math.max(8, trackHeight * visibleCount / options.size());
            int maxOffset = options.size() - visibleCount;
            int thumbY = trackY + (maxOffset > 0
                    ? (trackHeight - thumbHeight) * scrollOffset / maxOffset : 0);
            guiGraphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0x55374151);
            guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCC7DD3FC);
        }
    }

    private int getDropdownVisibleCount(int y, int height, int optionCount) {
        if (optionCount <= 0) return 0;
        int availableBelow = Math.max(DROPDOWN_ITEM_HEIGHT, this.height - (y + height) - 4);
        int screenCapacity = Math.max(1, availableBelow / DROPDOWN_ITEM_HEIGHT);
        return Math.min(optionCount, Math.min(DROPDOWN_MAX_VISIBLE_ITEMS, screenCapacity));
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
            float scale = entitySize > 0
                    ? (size * ENTITY_ICON_FILL) / entitySize
                    : size * ENTITY_ICON_FILL;

            guiGraphics.pose().pushPose();
            posePushed = true;
            guiGraphics.pose().translate(x, y, 50);
            guiGraphics.pose().scale(scale, -scale, scale);
            // Keep the entity at its default yaw for a straight-on front view. In particular,
            // do not add pitch here, as that makes every model appear diagonally tilted.

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

        int panelWidth = panelRight - panelLeft;
        int searchWidth = panelWidth - PANEL_INSET * 2 - 96;
        int totalDropdownWidth = searchWidth + 92;
        int modDropdownWidth = Math.max(86, totalDropdownWidth / 4);
        int statusDropdownWidth = Math.max(104, totalDropdownWidth / 3);
        int attributeDropdownWidth = totalDropdownWidth - modDropdownWidth - statusDropdownWidth - 8;
        int modDropdownX = listLeft;
        int statusDropdownX = listLeft + modDropdownWidth + 4;
        int attributeDropdownX = statusDropdownX + statusDropdownWidth + 4;
        int dropdownY = panelTop + 50;
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
            int scrollBarX = listRight - 8;
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
            int visibleCount = getDropdownVisibleCount(y, height, options.size());
            int scrollOffset = clampDropdownScrollOffset(kind, options.size(), visibleCount);
            int listH = visibleCount * DROPDOWN_ITEM_HEIGHT;
            if (mouseX >= x && mouseX < x + width && mouseY >= y + height && mouseY < y + height + listH) {
                int index = scrollOffset + (int) ((mouseY - (y + height)) / DROPDOWN_ITEM_HEIGHT);
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
            revealSelectedDropdownOption(kind, options, y, height);
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

    private void revealSelectedDropdownOption(DropdownKind kind, List<String> options, int y, int height) {
        int selectedIndex = options.indexOf(switch (kind) {
            case MOD -> selectedMod;
            case STATUS -> selectedStatus;
            case ATTRIBUTE -> selectedAttributeStatus;
        });
        int visibleCount = getDropdownVisibleCount(y, height, options.size());
        int offset = clampDropdownScrollOffset(kind, options.size(), visibleCount);
        if (selectedIndex >= 0 && selectedIndex < offset) {
            setDropdownScrollOffset(kind, selectedIndex);
        } else if (selectedIndex >= offset + visibleCount) {
            setDropdownScrollOffset(kind, selectedIndex - visibleCount + 1);
        }
    }

    private int clampDropdownScrollOffset(DropdownKind kind, int optionCount, int visibleCount) {
        int maxOffset = Math.max(0, optionCount - visibleCount);
        int clamped = Math.max(0, Math.min(getDropdownScrollOffset(kind), maxOffset));
        setDropdownScrollOffset(kind, clamped);
        return clamped;
    }

    private int getDropdownScrollOffset(DropdownKind kind) {
        return switch (kind) {
            case MOD -> modDropdownScrollOffset;
            case STATUS -> statusDropdownScrollOffset;
            case ATTRIBUTE -> attributeDropdownScrollOffset;
        };
    }

    private void setDropdownScrollOffset(DropdownKind kind, int offset) {
        switch (kind) {
            case MOD -> modDropdownScrollOffset = offset;
            case STATUS -> statusDropdownScrollOffset = offset;
            case ATTRIBUTE -> attributeDropdownScrollOffset = offset;
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
        int panelWidth = panelRight - panelLeft;
        int searchWidth = panelWidth - PANEL_INSET * 2 - 96;
        int totalDropdownWidth = searchWidth + 92;
        int modDropdownWidth = Math.max(86, totalDropdownWidth / 4);
        int statusDropdownWidth = Math.max(104, totalDropdownWidth / 3);
        int attributeDropdownWidth = totalDropdownWidth - modDropdownWidth - statusDropdownWidth - 8;
        int dropdownY = panelTop + 50;
        int dropdownHeight = 18;
        if (scrollOpenDropdown(mouseX, mouseY, scrollY, listLeft, dropdownY, modDropdownWidth,
                dropdownHeight, availableMods, DropdownKind.MOD)
                || scrollOpenDropdown(mouseX, mouseY, scrollY, listLeft + modDropdownWidth + 4, dropdownY,
                statusDropdownWidth, dropdownHeight, statusOptions, DropdownKind.STATUS)
                || scrollOpenDropdown(mouseX, mouseY, scrollY,
                listLeft + modDropdownWidth + statusDropdownWidth + 8, dropdownY,
                attributeDropdownWidth, dropdownHeight, attributeStatusOptions, DropdownKind.ATTRIBUTE)) {
            return true;
        }
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * 20, getMaxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean scrollOpenDropdown(double mouseX, double mouseY, double scrollY, int x, int y, int width,
                                       int height, List<String> options, DropdownKind kind) {
        if (!isDropdownOpen(kind)) return false;
        int visibleCount = getDropdownVisibleCount(y, height, options.size());
        int listHeight = visibleCount * DROPDOWN_ITEM_HEIGHT;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height + listHeight) {
            return false;
        }
        if (options.size() <= visibleCount || scrollY == 0.0) {
            return true;
        }
        int current = clampDropdownScrollOffset(kind, options.size(), visibleCount);
        int direction = scrollY > 0.0 ? -1 : 1;
        setDropdownScrollOffset(kind, Math.max(0,
                Math.min(current + direction, options.size() - visibleCount)));
        return true;
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
