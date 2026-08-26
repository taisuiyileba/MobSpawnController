package com.mobspawncontroller.client.gui;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.active.ActiveSpawnSettings;
import com.mobspawncontroller.attribute.MobAttributeControl;
import com.mobspawncontroller.client.ClientRuleSync;
import com.mobspawncontroller.compat.SereneSeasonsCompat;
import com.mobspawncontroller.network.ServerboundRequestAttributesPayload;
import com.mobspawncontroller.network.ServerboundRequestStructuresPayload;
import com.mobspawncontroller.network.ServerboundSetAttributesPayload;
import com.mobspawncontroller.network.ServerboundSetActiveSpawnPayload;
import com.mobspawncontroller.network.ServerboundSetNaturalSpawnPayload;
import com.mobspawncontroller.network.ServerboundToggleSpawnPayload;
import com.mobspawncontroller.natural.NaturalSpawnSettings;
import com.mobspawncontroller.platform.NetworkBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import java.util.stream.Collectors;

public class MobSpawnEditScreen extends AbstractMobSpawnScreen implements ClientRuleSync.Receiver {

    private static final int ROW_HEIGHT = 26;
    private static final int ATTRIBUTE_ROW_HEIGHT = 26;
    private static final int NATURAL_ROW_HEIGHT = 28;
    private static final int NATURAL_INPUT_W = 154;
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
    private static final int ROW_BG = 0x12FFFFFF;
    private static final int ROW_HOVER_BG = 0x3F63B3ED;

    private enum DetailTab {
        SPAWN_RULES,
        NATURAL_SPAWN,
        ACTIVE_SPAWN,
        ATTRIBUTES
    }

    private enum NaturalFieldType {
        NUMBER,
        RANGE,
        CYCLE,
        PICKER,
        SWITCH
    }

    private record NaturalField(String key, NaturalFieldType type) {
    }

    private record NaturalNumberRule(boolean integer, double min, double max) {
        private boolean accepts(String text) {
            try {
                double value = Double.parseDouble(text.trim());
                return Double.isFinite(value) && value >= min && value <= max
                        && (!integer || value == Math.rint(value));
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }

    private enum SelectorMode {
        WHITELIST,
        BLACKLIST;

        private SelectorMode next() {
            return this == WHITELIST ? BLACKLIST : WHITELIST;
        }
    }

    private static final List<NaturalField> NATURAL_FIELDS = List.of(
            new NaturalField("spawn_type_list", NaturalFieldType.PICKER),
            new NaturalField("chance", NaturalFieldType.NUMBER),
            new NaturalField("players_range", NaturalFieldType.RANGE),
            new NaturalField("max_nearby", NaturalFieldType.NUMBER),
            new NaturalField("nearby_radius", NaturalFieldType.NUMBER),
            new NaturalField("height_range", NaturalFieldType.RANGE),
            new NaturalField("distance_range", NaturalFieldType.RANGE),
            new NaturalField("spawn_distance_range", NaturalFieldType.RANGE),
            new NaturalField("sky", NaturalFieldType.CYCLE),
            new NaturalField("fluid", NaturalFieldType.CYCLE),
            new NaturalField("slime_chunk", NaturalFieldType.CYCLE),
            new NaturalField("total_light_range", NaturalFieldType.RANGE),
            new NaturalField("sky_light_range", NaturalFieldType.RANGE),
            new NaturalField("block_light_range", NaturalFieldType.RANGE),
            new NaturalField("time_range", NaturalFieldType.RANGE),
            new NaturalField("day_range", NaturalFieldType.RANGE),
            new NaturalField("moon_phase_list", NaturalFieldType.PICKER),
            new NaturalField("season_list", NaturalFieldType.PICKER),
            new NaturalField("weather", NaturalFieldType.CYCLE),
            new NaturalField("difficulty", NaturalFieldType.CYCLE),
            new NaturalField("local_difficulty_range", NaturalFieldType.RANGE),
            new NaturalField("dimension_list", NaturalFieldType.PICKER),
            new NaturalField("biome_list", NaturalFieldType.PICKER),
            new NaturalField("structure_list", NaturalFieldType.PICKER),
            new NaturalField("block_below_list", NaturalFieldType.PICKER),
            new NaturalField("block_at_list", NaturalFieldType.PICKER),
            new NaturalField("block_above_list", NaturalFieldType.PICKER)
    );
    private static final Map<String, NaturalNumberRule> NATURAL_NUMBER_RULES = Map.ofEntries(
            Map.entry("chance", decimalRule(0.0, 100.0)),
            Map.entry("min_players", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("max_players", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("max_nearby", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("nearby_radius", decimalRule(1.0, 256.0)),
            Map.entry("min_height", integerRule(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            Map.entry("max_height", integerRule(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            Map.entry("min_distance", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("max_distance", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("min_spawn_distance", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("max_spawn_distance", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("min_total_light", integerRule(0, 15)),
            Map.entry("max_total_light", integerRule(0, 15)),
            Map.entry("min_sky_light", integerRule(0, 15)),
            Map.entry("max_sky_light", integerRule(0, 15)),
            Map.entry("min_block_light", integerRule(0, 15)),
            Map.entry("max_block_light", integerRule(0, 15)),
            Map.entry("min_time", integerRule(0, 23999)),
            Map.entry("max_time", integerRule(0, 23999)),
            Map.entry("min_day", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("max_day", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("min_local_difficulty", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("max_local_difficulty", decimalRule(0.0, Double.MAX_VALUE))
    );
    private static final List<NaturalField> ACTIVE_FIELDS = List.of(
            new NaturalField("enabled", NaturalFieldType.SWITCH),
            new NaturalField("chance", NaturalFieldType.NUMBER),
            new NaturalField("attempts", NaturalFieldType.NUMBER),
            new NaturalField("amount_range", NaturalFieldType.RANGE),
            new NaturalField("players_range", NaturalFieldType.RANGE),
            new NaturalField("max_world_count", NaturalFieldType.NUMBER),
            new NaturalField("max_nearby_count", NaturalFieldType.NUMBER),
            new NaturalField("nearby_radius", NaturalFieldType.NUMBER),
            new NaturalField("distance_range", NaturalFieldType.RANGE),
            new NaturalField("spawn_distance_range", NaturalFieldType.RANGE),
            new NaturalField("height_range", NaturalFieldType.RANGE),
            new NaturalField("placement", NaturalFieldType.CYCLE),
            new NaturalField("obey_spawn_rules", NaturalFieldType.SWITCH),
            new NaturalField("sky", NaturalFieldType.CYCLE),
            new NaturalField("slime_chunk", NaturalFieldType.CYCLE),
            new NaturalField("total_light_range", NaturalFieldType.RANGE),
            new NaturalField("sky_light_range", NaturalFieldType.RANGE),
            new NaturalField("block_light_range", NaturalFieldType.RANGE),
            new NaturalField("time_range", NaturalFieldType.RANGE),
            new NaturalField("day_range", NaturalFieldType.RANGE),
            new NaturalField("moon_phase_list", NaturalFieldType.PICKER),
            new NaturalField("season_list", NaturalFieldType.PICKER),
            new NaturalField("weather", NaturalFieldType.CYCLE),
            new NaturalField("difficulty", NaturalFieldType.CYCLE),
            new NaturalField("local_difficulty_range", NaturalFieldType.RANGE),
            new NaturalField("dimension_list", NaturalFieldType.PICKER),
            new NaturalField("biome_list", NaturalFieldType.PICKER),
            new NaturalField("structure_list", NaturalFieldType.PICKER),
            new NaturalField("block_below_list", NaturalFieldType.PICKER),
            new NaturalField("block_at_list", NaturalFieldType.PICKER),
            new NaturalField("block_above_list", NaturalFieldType.PICKER)
    );
    private static final Map<String, NaturalNumberRule> ACTIVE_NUMBER_RULES = Map.ofEntries(
            Map.entry("chance", decimalRule(0.0, 100.0)),
            Map.entry("attempts", integerRule(1, 128)),
            Map.entry("min_amount", integerRule(1, 64)),
            Map.entry("max_amount", integerRule(1, 64)),
            Map.entry("min_distance", integerRule(0, 256)),
            Map.entry("max_distance", integerRule(1, 256)),
            Map.entry("min_players", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("max_players", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("min_spawn_distance", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("max_spawn_distance", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("min_height", integerRule(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            Map.entry("max_height", integerRule(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            Map.entry("min_day", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("max_day", integerRule(0, Integer.MAX_VALUE)),
            Map.entry("min_time", integerRule(0, 23999)),
            Map.entry("max_time", integerRule(0, 23999)),
            Map.entry("min_total_light", integerRule(0, 15)),
            Map.entry("max_total_light", integerRule(0, 15)),
            Map.entry("min_sky_light", integerRule(0, 15)),
            Map.entry("max_sky_light", integerRule(0, 15)),
            Map.entry("min_block_light", integerRule(0, 15)),
            Map.entry("max_block_light", integerRule(0, 15)),
            Map.entry("min_local_difficulty", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("max_local_difficulty", decimalRule(0.0, Double.MAX_VALUE)),
            Map.entry("max_world_count", integerRule(1, Integer.MAX_VALUE)),
            Map.entry("max_nearby_count", integerRule(1, Integer.MAX_VALUE)),
            Map.entry("nearby_radius", decimalRule(1.0, 256.0))
    );

    private final MobSpawnControllerScreen parent;
    private final ResourceLocation mobId;
    private final EntityType<?> entityType;
    private final EnumMap<MobSpawnType, Boolean> editRules = new EnumMap<>(MobSpawnType.class);
    private final MobSpawnType[] spawnTypes = MobSpawnType.values();
    private final List<MobAttributeControl> attributeControls = new ArrayList<>();
    private final List<NaturalField> naturalFields = NATURAL_FIELDS.stream()
            .filter(field -> !field.key().equals("season_list") || SereneSeasonsCompat.isAvailable())
            .toList();
    private final List<NaturalField> activeFields = ACTIVE_FIELDS.stream()
            .filter(field -> !field.key().equals("season_list") || SereneSeasonsCompat.isAvailable())
            .toList();

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
    private String focusedNaturalField = null;
    private String focusedActiveField = null;
    private Button cancelButton;
    private Button saveButton;
    private DetailTab activeTab = DetailTab.SPAWN_RULES;
    private boolean attributesLoaded = false;
    private final Map<ResourceLocation, String> attributeInputs = new HashMap<>();
    private final Map<String, String> naturalInputs = new HashMap<>();
    private final Map<String, SelectorMode> naturalSelectorModes = new HashMap<>();
    private final Map<String, EnumMap<SelectorMode, List<String>>> naturalSelections = new HashMap<>();
    private final Map<String, String> activeInputs = new HashMap<>();
    private final Map<String, SelectorMode> activeSelectorModes = new HashMap<>();
    private final Map<String, EnumMap<SelectorMode, List<String>>> activeSelections = new HashMap<>();
    private boolean activeEnabled = false;
    private boolean activeObeySpawnRules = true;
    private ActiveSpawnSettings.PlacementMode activePlacement = ActiveSpawnSettings.PlacementMode.GROUND;
    private ActiveSpawnSettings.SkyMode activeSky = ActiveSpawnSettings.SkyMode.ANY;
    private NaturalSpawnSettings.WeatherMode activeWeather = NaturalSpawnSettings.WeatherMode.ANY;
    private NaturalSpawnSettings.DifficultyMode activeDifficulty = NaturalSpawnSettings.DifficultyMode.ANY;
    private NaturalSpawnSettings.SlimeChunkMode activeSlimeChunk = NaturalSpawnSettings.SlimeChunkMode.ANY;
    private NaturalSpawnSettings.WeatherMode naturalWeather = NaturalSpawnSettings.WeatherMode.ANY;
    private NaturalSpawnSettings.DifficultyMode naturalDifficulty = NaturalSpawnSettings.DifficultyMode.ANY;
    private NaturalSpawnSettings.SkyMode naturalSky = NaturalSpawnSettings.SkyMode.ANY;
    private NaturalSpawnSettings.FluidMode naturalFluid = NaturalSpawnSettings.FluidMode.ANY;
    private NaturalSpawnSettings.SlimeChunkMode naturalSlimeChunk = NaturalSpawnSettings.SlimeChunkMode.ANY;

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
        loadNaturalSettings(parent.getNaturalSpawnSettings().getOrDefault(mobId, NaturalSpawnSettings.defaults()));
        loadActiveSettings(parent.getActiveSpawnSettings().getOrDefault(mobId, ActiveSpawnSettings.defaults()));
        NetworkBridge.sendToServer(new ServerboundRequestAttributesPayload(mobId));
        NetworkBridge.sendToServer(new ServerboundRequestStructuresPayload());
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(280, Math.min(this.width - 32, 430));
        int desiredHeight = HEADER_HEIGHT + Math.max(spawnTypes.length * ROW_HEIGHT,
                Math.max(naturalFields.size(), activeFields.size()) * NATURAL_ROW_HEIGHT) + FOOTER_HEIGHT;
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
        if (activeTab == DetailTab.NATURAL_SPAWN) {
            contentHeight = naturalFields.size() * NATURAL_ROW_HEIGHT;
            return;
        }
        if (activeTab == DetailTab.ACTIVE_SPAWN) {
            contentHeight = activeFields.size() * NATURAL_ROW_HEIGHT;
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

        if (!validateNaturalInputs() || !validateActiveInputs()) {
            return;
        }
        NaturalSpawnSettings naturalSettings = collectNaturalSettings();
        ActiveSpawnSettings activeSettings = collectActiveSettings();

        Map<MobSpawnType, Boolean> originalRules = parent.getRules().get(mobId);
        EnumMap<MobSpawnType, Boolean> updatedRules = new EnumMap<>(MobSpawnType.class);
        if (originalRules != null) {
            updatedRules.putAll(originalRules);
        }
        for (MobSpawnType type : spawnTypes) {
            Boolean value = editRules.get(type);
            Boolean originalValue = originalRules == null ? null : originalRules.get(type);
            boolean originalEffectiveValue = originalValue == null || originalValue;
            if (value != null && value != originalEffectiveValue) {
                NetworkBridge.sendToServer(new ServerboundToggleSpawnPayload(
                        mobId, type.name().toLowerCase(Locale.ROOT), value));
                updatedRules.put(type, value);
            }
        }
        if (attributesLoaded) {
            NetworkBridge.sendToServer(new ServerboundSetAttributesPayload(mobId, collectAttributeOverrides()));
        }
        NaturalSpawnSettings originalNaturalSettings = parent.getNaturalSpawnSettings()
                .getOrDefault(mobId, NaturalSpawnSettings.defaults());
        if (!naturalSettings.equals(originalNaturalSettings)) {
            NetworkBridge.sendToServer(new ServerboundSetNaturalSpawnPayload(mobId, naturalSettings));
        }
        ActiveSpawnSettings originalActiveSettings = parent.getActiveSpawnSettings()
                .getOrDefault(mobId, ActiveSpawnSettings.defaults());
        if (!activeSettings.equals(originalActiveSettings)) {
            NetworkBridge.sendToServer(new ServerboundSetActiveSpawnPayload(mobId, activeSettings));
        }

        if (updatedRules.isEmpty()) {
            parent.getRules().remove(mobId);
        } else {
            parent.getRules().put(mobId, updatedRules);
        }
        if (naturalSettings.isDefault()) {
            parent.getNaturalSpawnSettings().remove(mobId);
        } else {
            parent.getNaturalSpawnSettings().put(mobId, naturalSettings);
        }
        if (activeSettings.isDefault()) {
            parent.getActiveSpawnSettings().remove(mobId);
        } else {
            parent.getActiveSpawnSettings().put(mobId, activeSettings);
        }

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

    private void loadNaturalSettings(NaturalSpawnSettings settings) {
        naturalInputs.clear();
        naturalSelectorModes.clear();
        naturalSelections.clear();
        loadSelector("spawn_type_list", spawnTypeStrings(settings.spawnTypes()),
                spawnTypeStrings(settings.excludedSpawnTypes()));
        naturalInputs.put("chance", formatNaturalNumber(settings.chance() * 100.0));
        putNatural("min_height", settings.minHeight());
        putNatural("max_height", settings.maxHeight());
        putNatural("min_total_light", settings.minTotalLight());
        putNatural("max_total_light", settings.maxTotalLight());
        putNatural("min_time", settings.minTime());
        putNatural("max_time", settings.maxTime());
        putNatural("min_day", settings.minDay());
        putNatural("max_day", settings.maxDay());
        loadSelector("moon_phase_list",
                settings.moonPhases().stream().map(String::valueOf).toList(),
                settings.excludedMoonPhases().stream().map(String::valueOf).toList());
        putNatural("min_distance", settings.minPlayerDistance());
        putNatural("max_distance", settings.maxPlayerDistance());
        putNatural("min_spawn_distance", settings.minWorldSpawnDistance());
        putNatural("max_spawn_distance", settings.maxWorldSpawnDistance());
        putNatural("min_local_difficulty", settings.minLocalDifficulty());
        putNatural("max_local_difficulty", settings.maxLocalDifficulty());
        loadSelector("dimension_list", resourceStrings(settings.dimensions()),
                resourceStrings(settings.excludedDimensions()));
        loadSelector("biome_list", combineIdsAndTags(settings.biomes(), settings.biomeTags()),
                combineIdsAndTags(settings.excludedBiomes(), settings.excludedBiomeTags()));
        loadSelector("season_list", settings.seasons(), settings.excludedSeasons());
        loadSelector("structure_list", settings.structures(), settings.excludedStructures());
        loadSelector("block_below_list", settings.blocksBelow(), settings.excludedBlocksBelow());
        loadSelector("block_at_list", settings.blocksAt(), settings.excludedBlocksAt());
        loadSelector("block_above_list", settings.blocksAbove(), settings.excludedBlocksAbove());
        putNatural("min_sky_light", settings.minSkyLight());
        putNatural("max_sky_light", settings.maxSkyLight());
        putNatural("min_block_light", settings.minBlockLight());
        putNatural("max_block_light", settings.maxBlockLight());
        putNatural("min_players", settings.minPlayers());
        putNatural("max_players", settings.maxPlayers());
        putNatural("max_nearby", settings.maxNearby());
        naturalInputs.put("nearby_radius", formatNaturalNumber(settings.nearbyRadius()));
        naturalWeather = settings.weather();
        naturalDifficulty = settings.difficulty();
        naturalSky = settings.skyMode();
        naturalFluid = settings.fluidMode();
        naturalSlimeChunk = settings.slimeChunkMode();
    }

    private void loadActiveSettings(ActiveSpawnSettings settings) {
        activeInputs.clear();
        activeSelectorModes.clear();
        activeSelections.clear();
        activeEnabled = settings.enabled();
        activeObeySpawnRules = settings.obeySpawnRules();
        activePlacement = settings.placement();
        activeSky = settings.skyMode();
        activeInputs.put("chance", formatNaturalNumber(settings.chancePerSecond() * 100.0));
        activeInputs.put("attempts", String.valueOf(settings.attempts()));
        activeInputs.put("min_amount", String.valueOf(settings.minAmount()));
        activeInputs.put("max_amount", String.valueOf(settings.maxAmount()));
        activeInputs.put("min_distance", String.valueOf(settings.minDistance()));
        activeInputs.put("max_distance", String.valueOf(settings.maxDistance()));
        putActive("min_players", settings.minPlayers());
        putActive("max_players", settings.maxPlayers());
        putActive("min_spawn_distance", settings.minWorldSpawnDistance());
        putActive("max_spawn_distance", settings.maxWorldSpawnDistance());
        putActive("min_height", settings.minHeight());
        putActive("max_height", settings.maxHeight());
        putActive("min_day", settings.minDay());
        putActive("max_day", settings.maxDay());
        putActive("min_time", settings.minTime());
        putActive("max_time", settings.maxTime());
        putActive("min_total_light", settings.minTotalLight());
        putActive("max_total_light", settings.maxTotalLight());
        putActive("min_sky_light", settings.minSkyLight());
        putActive("max_sky_light", settings.maxSkyLight());
        putActive("min_block_light", settings.minBlockLight());
        putActive("max_block_light", settings.maxBlockLight());
        putActive("min_local_difficulty", settings.minLocalDifficulty());
        putActive("max_local_difficulty", settings.maxLocalDifficulty());
        putActive("max_world_count", settings.maxWorldCount());
        putActive("max_nearby_count", settings.maxNearbyCount());
        activeInputs.put("nearby_radius", formatNaturalNumber(settings.nearbyRadius()));
        loadActiveSelector("moon_phase_list",
                settings.moonPhases().stream().map(String::valueOf).toList(),
                settings.excludedMoonPhases().stream().map(String::valueOf).toList());
        loadActiveSelector("season_list", settings.seasons(), settings.excludedSeasons());
        loadActiveSelector("dimension_list", resourceStrings(settings.dimensions()),
                resourceStrings(settings.excludedDimensions()));
        loadActiveSelector("biome_list", combineIdsAndTags(settings.biomes(), settings.biomeTags()),
                combineIdsAndTags(settings.excludedBiomes(), settings.excludedBiomeTags()));
        loadActiveSelector("structure_list", settings.structures(), settings.excludedStructures());
        loadActiveSelector("block_below_list", settings.blocksBelow(), settings.excludedBlocksBelow());
        loadActiveSelector("block_at_list", settings.blocksAt(), settings.excludedBlocksAt());
        loadActiveSelector("block_above_list", settings.blocksAbove(), settings.excludedBlocksAbove());
        activeWeather = settings.weather();
        activeDifficulty = settings.difficulty();
        activeSlimeChunk = settings.slimeChunkMode();
    }

    private void putActive(String key, Number value) {
        activeInputs.put(key, value == null ? "" : formatNaturalNumber(value.doubleValue()));
    }

    private void loadActiveSelector(String key, List<String> whitelist, List<String> blacklist) {
        boolean useBlacklist = whitelist.isEmpty() && !blacklist.isEmpty();
        activeSelectorModes.put(key, useBlacklist ? SelectorMode.BLACKLIST : SelectorMode.WHITELIST);
        EnumMap<SelectorMode, List<String>> selections = new EnumMap<>(SelectorMode.class);
        selections.put(SelectorMode.WHITELIST, new ArrayList<>(whitelist));
        selections.put(SelectorMode.BLACKLIST, new ArrayList<>(blacklist));
        activeSelections.put(key, selections);
    }

    private void putNatural(String key, Number value) {
        naturalInputs.put(key, value == null ? "" : formatNaturalNumber(value.doubleValue()));
    }

    private void loadSelector(String key, List<String> whitelist, List<String> blacklist) {
        boolean useBlacklist = whitelist.isEmpty() && !blacklist.isEmpty();
        naturalSelectorModes.put(key, useBlacklist ? SelectorMode.BLACKLIST : SelectorMode.WHITELIST);
        EnumMap<SelectorMode, List<String>> selections = new EnumMap<>(SelectorMode.class);
        selections.put(SelectorMode.WHITELIST, new ArrayList<>(whitelist));
        selections.put(SelectorMode.BLACKLIST, new ArrayList<>(blacklist));
        naturalSelections.put(key, selections);
    }

    private NaturalSpawnSettings collectNaturalSettings() {
        return new NaturalSpawnSettings(
                parseNaturalDouble("chance", 100.0) / 100.0,
                parseNaturalInteger("min_height"), parseNaturalInteger("max_height"),
                parseNaturalInteger("min_total_light"), parseNaturalInteger("max_total_light"),
                parseNaturalInteger("min_time"), parseNaturalInteger("max_time"),
                parseNaturalInteger("min_day"), parseNaturalInteger("max_day"),
                selectedInts("moon_phase_list", SelectorMode.WHITELIST),
                selectedInts("moon_phase_list", SelectorMode.BLACKLIST),
                parseNaturalNullableDouble("min_distance"), parseNaturalNullableDouble("max_distance"),
                parseNaturalNullableDouble("min_spawn_distance"),
                parseNaturalNullableDouble("max_spawn_distance"),
                parseNaturalNullableDouble("min_local_difficulty"),
                parseNaturalNullableDouble("max_local_difficulty"),
                naturalWeather, naturalDifficulty, naturalSky, naturalFluid, naturalSlimeChunk,
                selectedResources("dimension_list", SelectorMode.WHITELIST, false),
                selectedResources("dimension_list", SelectorMode.BLACKLIST, false),
                selectedResources("biome_list", SelectorMode.WHITELIST, false),
                selectedResources("biome_list", SelectorMode.BLACKLIST, false),
                selectedResources("biome_list", SelectorMode.WHITELIST, true),
                selectedResources("biome_list", SelectorMode.BLACKLIST, true),
                selectedStrings("season_list", SelectorMode.WHITELIST),
                selectedStrings("season_list", SelectorMode.BLACKLIST),
                selectedStrings("structure_list", SelectorMode.WHITELIST),
                selectedStrings("structure_list", SelectorMode.BLACKLIST),
                selectedStrings("block_below_list", SelectorMode.WHITELIST),
                selectedStrings("block_below_list", SelectorMode.BLACKLIST),
                parseNaturalInteger("min_players"), parseNaturalInteger("max_players"),
                parseNaturalInteger("max_nearby"), parseNaturalDouble("nearby_radius", 16.0),
                parseNaturalInteger("min_sky_light"), parseNaturalInteger("max_sky_light"),
                parseNaturalInteger("min_block_light"), parseNaturalInteger("max_block_light"),
                selectedStrings("block_at_list", SelectorMode.WHITELIST),
                selectedStrings("block_at_list", SelectorMode.BLACKLIST),
                selectedStrings("block_above_list", SelectorMode.WHITELIST),
                selectedStrings("block_above_list", SelectorMode.BLACKLIST),
                selectedSpawnTypes("spawn_type_list", SelectorMode.WHITELIST),
                selectedSpawnTypes("spawn_type_list", SelectorMode.BLACKLIST));
    }

    private ActiveSpawnSettings collectActiveSettings() {
        return new ActiveSpawnSettings(activeEnabled,
                parseActiveDouble("chance", 100.0) / 100.0,
                parseActiveInteger("attempts", 4),
                parseActiveInteger("min_amount", 1), parseActiveInteger("max_amount", 1),
                parseActiveInteger("min_distance", 24), parseActiveInteger("max_distance", 64),
                parseActiveInteger("min_height"), parseActiveInteger("max_height"),
                parseActiveInteger("min_day"), parseActiveInteger("max_day"),
                parseActiveInteger("min_time"), parseActiveInteger("max_time"),
                parseActiveInteger("min_total_light"), parseActiveInteger("max_total_light"),
                parseActiveInteger("max_world_count"), parseActiveInteger("max_nearby_count"),
                parseActiveDouble("nearby_radius", 32.0), activePlacement, activeObeySpawnRules, activeSky,
                parseActiveInteger("min_players"), parseActiveInteger("max_players"),
                parseActiveNullableDouble("min_spawn_distance"),
                parseActiveNullableDouble("max_spawn_distance"),
                parseActiveNullableDouble("min_local_difficulty"),
                parseActiveNullableDouble("max_local_difficulty"),
                parseActiveInteger("min_sky_light"), parseActiveInteger("max_sky_light"),
                parseActiveInteger("min_block_light"), parseActiveInteger("max_block_light"),
                selectedActiveInts("moon_phase_list", SelectorMode.WHITELIST),
                selectedActiveInts("moon_phase_list", SelectorMode.BLACKLIST),
                activeWeather, activeDifficulty, activeSlimeChunk,
                selectedActiveResources("dimension_list", SelectorMode.WHITELIST, false),
                selectedActiveResources("dimension_list", SelectorMode.BLACKLIST, false),
                selectedActiveResources("biome_list", SelectorMode.WHITELIST, false),
                selectedActiveResources("biome_list", SelectorMode.BLACKLIST, false),
                selectedActiveResources("biome_list", SelectorMode.WHITELIST, true),
                selectedActiveResources("biome_list", SelectorMode.BLACKLIST, true),
                selectedActiveStrings("season_list", SelectorMode.WHITELIST),
                selectedActiveStrings("season_list", SelectorMode.BLACKLIST),
                selectedActiveStrings("structure_list", SelectorMode.WHITELIST),
                selectedActiveStrings("structure_list", SelectorMode.BLACKLIST),
                selectedActiveStrings("block_below_list", SelectorMode.WHITELIST),
                selectedActiveStrings("block_below_list", SelectorMode.BLACKLIST),
                selectedActiveStrings("block_at_list", SelectorMode.WHITELIST),
                selectedActiveStrings("block_at_list", SelectorMode.BLACKLIST),
                selectedActiveStrings("block_above_list", SelectorMode.WHITELIST),
                selectedActiveStrings("block_above_list", SelectorMode.BLACKLIST));
    }

    private Integer parseActiveInteger(String key) {
        Double value = parseActiveNullableDouble(key);
        return value == null ? null : value.intValue();
    }

    private int parseActiveInteger(String key, int fallback) {
        Integer value = parseActiveInteger(key);
        return value == null ? fallback : value;
    }

    private Double parseActiveNullableDouble(String key) {
        String text = activeInputs.get(key);
        if (text == null || text.isBlank()) return null;
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double parseActiveDouble(String key, double fallback) {
        Double value = parseActiveNullableDouble(key);
        return value == null ? fallback : value;
    }

    private List<ResourceLocation> selectedActiveResources(String key, SelectorMode mode, boolean tagsOnly) {
        List<ResourceLocation> result = new ArrayList<>();
        for (String value : activeSelection(key, mode)) {
            boolean tag = value.startsWith("#");
            if (tag != tagsOnly) continue;
            ResourceLocation id = ResourceLocation.tryParse(tag ? value.substring(1) : value);
            if (id != null) result.add(id);
        }
        return result.stream().distinct().toList();
    }

    private List<String> selectedActiveStrings(String key, SelectorMode mode) {
        return activeSelection(key, mode).stream().map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    private List<Integer> selectedActiveInts(String key, SelectorMode mode) {
        List<Integer> values = new ArrayList<>();
        for (String value : activeSelection(key, mode)) {
            try {
                values.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private List<String> activeSelection(String key, SelectorMode mode) {
        EnumMap<SelectorMode, List<String>> selections = activeSelections.get(key);
        return selections == null ? List.of() : selections.getOrDefault(mode, List.of());
    }

    private Integer parseNaturalInteger(String key) {
        Double value = parseNaturalNullableDouble(key);
        return value == null ? null : value.intValue();
    }

    private Double parseNaturalNullableDouble(String key) {
        String text = naturalInputs.get(key);
        if (text == null || text.isBlank()) return null;
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double parseNaturalDouble(String key, double fallback) {
        Double value = parseNaturalNullableDouble(key);
        return value == null ? fallback : value;
    }

    private boolean validateNaturalInputs() {
        if (!validateNaturalNumbers() || !validateNaturalRanges()) {
            return false;
        }
        return validateBlockSelections();
    }

    private boolean validateActiveInputs() {
        List<String> invalidNumbers = new ArrayList<>();
        ACTIVE_NUMBER_RULES.forEach((key, rule) -> {
            String text = activeInputs.get(key);
            if (text != null && !text.isBlank() && !rule.accepts(text)) {
                invalidNumbers.add(Component.translatable("gui.mobspawncontroller.active." + key).getString());
            }
        });
        if (!invalidNumbers.isEmpty()) {
            showValidationError("gui.mobspawncontroller.natural.error.invalid_numbers",
                    String.join(", ", invalidNumbers.subList(0, Math.min(3, invalidNumbers.size()))));
            return false;
        }

        List<String> invalidRanges = new ArrayList<>();
        checkActiveRange(invalidRanges, "amount_range", "min_amount", "max_amount");
        checkActiveRange(invalidRanges, "players_range", "min_players", "max_players");
        checkActiveRange(invalidRanges, "distance_range", "min_distance", "max_distance");
        checkActiveRange(invalidRanges, "spawn_distance_range", "min_spawn_distance", "max_spawn_distance");
        checkActiveRange(invalidRanges, "height_range", "min_height", "max_height");
        checkActiveRange(invalidRanges, "day_range", "min_day", "max_day");
        checkActiveRange(invalidRanges, "total_light_range", "min_total_light", "max_total_light");
        checkActiveRange(invalidRanges, "sky_light_range", "min_sky_light", "max_sky_light");
        checkActiveRange(invalidRanges, "block_light_range", "min_block_light", "max_block_light");
        checkActiveRange(invalidRanges, "local_difficulty_range",
                "min_local_difficulty", "max_local_difficulty");
        if (!invalidRanges.isEmpty()) {
            showValidationError("gui.mobspawncontroller.natural.error.range_min_max",
                    String.join(", ", invalidRanges.subList(0, Math.min(3, invalidRanges.size()))));
            return false;
        }
        List<String> invalidBlocks = new ArrayList<>();
        for (String key : List.of("block_below_list", "block_at_list", "block_above_list")) {
            for (SelectorMode mode : SelectorMode.values()) {
                for (String value : activeSelection(key, mode)) {
                    if (!BlockIdListEditScreen.isValidBlockSelector(value)) invalidBlocks.add(value);
                }
            }
        }
        if (!invalidBlocks.isEmpty()) {
            showValidationError("gui.mobspawncontroller.natural.error.invalid_blocks",
                    String.join(", ", invalidBlocks.subList(0, Math.min(3, invalidBlocks.size()))));
            return false;
        }
        return true;
    }

    private void checkActiveRange(List<String> invalid, String labelKey, String minKey, String maxKey) {
        Double min = parseActiveNullableDouble(minKey);
        Double max = parseActiveNullableDouble(maxKey);
        if (min != null && max != null && min > max) {
            invalid.add(Component.translatable("gui.mobspawncontroller.active." + labelKey).getString());
        }
    }

    private boolean validateNaturalNumbers() {
        List<String> invalid = new ArrayList<>();
        NATURAL_NUMBER_RULES.forEach((key, rule) -> {
            String text = naturalInputs.get(key);
            if (text != null && !text.isBlank() && !rule.accepts(text)) {
                invalid.add(Component.translatable("gui.mobspawncontroller.natural." + key).getString());
            }
        });
        if (invalid.isEmpty()) return true;
        showValidationError("gui.mobspawncontroller.natural.error.invalid_numbers",
                String.join(", ", invalid.subList(0, Math.min(3, invalid.size()))));
        return false;
    }

    private boolean validateNaturalRanges() {
        List<String> invalid = new ArrayList<>();
        checkRange(invalid, "height_range", parseNaturalNullableDouble("min_height"), parseNaturalNullableDouble("max_height"));
        checkRange(invalid, "total_light_range", parseNaturalNullableDouble("min_total_light"), parseNaturalNullableDouble("max_total_light"));
        checkRange(invalid, "sky_light_range", parseNaturalNullableDouble("min_sky_light"), parseNaturalNullableDouble("max_sky_light"));
        checkRange(invalid, "block_light_range", parseNaturalNullableDouble("min_block_light"), parseNaturalNullableDouble("max_block_light"));
        checkRange(invalid, "day_range", parseNaturalNullableDouble("min_day"), parseNaturalNullableDouble("max_day"));
        checkRange(invalid, "distance_range", parseNaturalNullableDouble("min_distance"), parseNaturalNullableDouble("max_distance"));
        checkRange(invalid, "spawn_distance_range", parseNaturalNullableDouble("min_spawn_distance"), parseNaturalNullableDouble("max_spawn_distance"));
        checkRange(invalid, "local_difficulty_range", parseNaturalNullableDouble("min_local_difficulty"), parseNaturalNullableDouble("max_local_difficulty"));
        checkRange(invalid, "players_range", parseNaturalNullableDouble("min_players"), parseNaturalNullableDouble("max_players"));
        if (!invalid.isEmpty()) {
            showValidationError("gui.mobspawncontroller.natural.error.range_min_max",
                    String.join(", ", invalid.subList(0, Math.min(3, invalid.size()))));
            return false;
        }
        return true;
    }

    private static void checkRange(List<String> invalid, String key, Double min, Double max) {
        if (min != null && max != null && min > max) {
            invalid.add(Component.translatable("gui.mobspawncontroller.natural." + key).getString());
        }
    }

    private boolean validateBlockSelections() {
        List<String> invalid = new ArrayList<>();
        for (String key : List.of("block_below_list", "block_at_list", "block_above_list")) {
            for (SelectorMode mode : SelectorMode.values()) {
                for (String value : naturalSelection(key, mode)) {
                    if (!BlockIdListEditScreen.isValidBlockSelector(value)) {
                        invalid.add(value);
                    }
                }
            }
        }
        if (!invalid.isEmpty()) {
            showValidationError("gui.mobspawncontroller.natural.error.invalid_blocks",
                    String.join(", ", invalid.subList(0, Math.min(3, invalid.size()))));
            return false;
        }
        return true;
    }

    private void showValidationError(String key, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), false);
        }
    }

    private List<ResourceLocation> selectedResources(String key, SelectorMode mode, boolean tags) {
        return naturalSelection(key, mode).stream()
                .filter(value -> value.startsWith("#") == tags)
                .map(value -> ResourceLocation.tryParse(tags ? value.substring(1) : value))
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    private List<String> selectedStrings(String key, SelectorMode mode) {
        return List.copyOf(naturalSelection(key, mode));
    }

    private List<Integer> selectedInts(String key, SelectorMode mode) {
        return naturalSelection(key, mode).stream()
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    private List<MobSpawnType> selectedSpawnTypes(String key, SelectorMode mode) {
        return naturalSelection(key, mode).stream()
                .map(value -> {
                    try {
                        return MobSpawnType.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    private static List<String> spawnTypeStrings(List<MobSpawnType> values) {
        return values.stream().map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
    }

    private List<String> activeNaturalSelection(String key) {
        return naturalSelection(key, naturalSelectorModes.getOrDefault(key, SelectorMode.WHITELIST));
    }

    private List<String> naturalSelection(String key, SelectorMode mode) {
        EnumMap<SelectorMode, List<String>> selections = naturalSelections.get(key);
        return selections == null ? List.of() : selections.getOrDefault(mode, List.of());
    }

    private void setNaturalSelection(String key, SelectorMode mode, List<String> selected) {
        naturalSelections.computeIfAbsent(key, ignored -> new EnumMap<>(SelectorMode.class))
                .put(mode, new ArrayList<>(selected));
    }

    private void toggleNaturalSelectorMode(String key) {
        SelectorMode current = naturalSelectorModes.getOrDefault(key, SelectorMode.WHITELIST);
        SelectorMode next = current.next();
        List<String> currentValues = naturalSelection(key, current);
        if (!currentValues.isEmpty() && naturalSelection(key, next).isEmpty()) {
            setNaturalSelection(key, next, currentValues);
            setNaturalSelection(key, current, List.of());
        }
        naturalSelectorModes.put(key, next);
    }

    private static NaturalNumberRule integerRule(double min, double max) {
        return new NaturalNumberRule(true, min, max);
    }

    private static NaturalNumberRule decimalRule(double min, double max) {
        return new NaturalNumberRule(false, min, max);
    }

    private static List<String> resourceStrings(List<ResourceLocation> values) {
        return values.stream().map(ResourceLocation::toString).toList();
    }

    private static List<String> combineIdsAndTags(List<ResourceLocation> ids, List<ResourceLocation> tags) {
        List<String> values = new ArrayList<>(resourceStrings(ids));
        tags.stream().map(value -> "#" + value).forEach(values::add);
        return values;
    }

    private static String formatNaturalNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void resetNaturalSettings() {
        focusedNaturalField = null;
        loadNaturalSettings(NaturalSpawnSettings.defaults());
    }

    private void resetActiveSettings() {
        focusedActiveField = null;
        loadActiveSettings(ActiveSpawnSettings.defaults());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderUnblurredBackground(guiGraphics);
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
        } else if (activeTab == DetailTab.NATURAL_SPAWN) {
            renderNaturalRows(guiGraphics, mouseX, mouseY);
        } else if (activeTab == DetailTab.ACTIVE_SPAWN) {
            renderActiveRows(guiGraphics, mouseX, mouseY);
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
                    iconTop + iconSize - 2, iconSize, parent.getEntityCache());
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
        Component tabLabel = switch (activeTab) {
            case SPAWN_RULES -> Component.translatable("gui.mobspawncontroller.tab.spawn_rules");
            case NATURAL_SPAWN -> Component.translatable("gui.mobspawncontroller.tab.natural_spawn");
            case ACTIVE_SPAWN -> Component.translatable("gui.mobspawncontroller.tab.extra_spawn");
            case ATTRIBUTES -> Component.translatable("gui.mobspawncontroller.tab.attributes");
        };
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
        int modifiedCount = activeTab == DetailTab.NATURAL_SPAWN
                ? (collectNaturalSettings().isDefault() ? 0 : 1)
                : activeTab == DetailTab.ACTIVE_SPAWN
                ? (collectActiveSettings().isDefault() ? 0 : 1) : modifiedAttributeCount();
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
        int tabWidth = (panelRight - panelLeft - PANEL_INSET * 2 - 12) / 4;
        int firstTabX = panelLeft + PANEL_INSET;
        renderTab(guiGraphics, mouseX, mouseY, firstTabX, tabY, tabWidth, DetailTab.SPAWN_RULES,
                Component.translatable("gui.mobspawncontroller.tab.spawn_rules"));
        renderTab(guiGraphics, mouseX, mouseY, firstTabX + tabWidth + 4, tabY, tabWidth, DetailTab.NATURAL_SPAWN,
                Component.translatable("gui.mobspawncontroller.tab.natural_spawn"));
        renderTab(guiGraphics, mouseX, mouseY, firstTabX + (tabWidth + 4) * 2, tabY, tabWidth, DetailTab.ACTIVE_SPAWN,
                Component.translatable("gui.mobspawncontroller.tab.extra_spawn"));
        renderTab(guiGraphics, mouseX, mouseY, firstTabX + (tabWidth + 4) * 3, tabY, tabWidth, DetailTab.ATTRIBUTES,
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

    private void renderNaturalRows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop - (int) scrollOffset;
        int inputX = panelRight - PANEL_INSET - NATURAL_INPUT_W - 6;
        for (int i = 0; i < naturalFields.size(); i++) {
            NaturalField field = naturalFields.get(i);
            int rowY = y + i * NATURAL_ROW_HEIGHT;
            if (rowY + NATURAL_ROW_HEIGHT < listTop || rowY > listBottom) continue;

            boolean hovered = mouseX >= panelLeft + PANEL_INSET && mouseX < panelRight - PANEL_INSET
                    && mouseY >= rowY && mouseY < rowY + NATURAL_ROW_HEIGHT;
            guiGraphics.fill(panelLeft + PANEL_INSET, rowY, panelRight - PANEL_INSET,
                    rowY + NATURAL_ROW_HEIGHT - 1, hovered ? ROW_HOVER_BG : (i % 2 == 0 ? ROW_BG : 0x08000000));

            int labelX = panelLeft + 16;
            int labelWidth = inputX - labelX - 10;
            String baseKey = "gui.mobspawncontroller.natural." + field.key();
            guiGraphics.drawString(font, trimToWidth(Component.translatable(baseKey).getString(), labelWidth),
                    labelX, rowY + 4, 0xFFE5E7EB);
            guiGraphics.drawString(font, trimToWidth(Component.translatable(baseKey + ".hint").getString(), labelWidth),
                    labelX, rowY + 16, 0xFF7B8794);

            int inputY = rowY + 6;
            if (field.type() == NaturalFieldType.PICKER) {
                renderNaturalPickerControl(guiGraphics, field.key(), inputX, inputY, mouseX, mouseY);
                continue;
            }
            if (field.type() == NaturalFieldType.RANGE) {
                renderNaturalRangeControl(guiGraphics, field.key(), inputX, inputY, mouseX, mouseY);
                continue;
            }
            boolean cycle = field.type() == NaturalFieldType.CYCLE;
            boolean focused = field.key().equals(focusedNaturalField);
            int border = focused ? ACCENT_COLOR : hovered ? 0xFF64748B : 0xFF374151;
            guiGraphics.fill(inputX, inputY, inputX + NATURAL_INPUT_W, inputY + 18, 0xFF111827);
            guiGraphics.renderOutline(inputX, inputY, NATURAL_INPUT_W, 18, border);
            String value = cycle ? naturalCycleLabel(field.key())
                    : naturalInputs.getOrDefault(field.key(), "");
            String shown = value.isEmpty()
                    ? Component.translatable("gui.mobspawncontroller.natural.unlimited").getString() : value;
            int color = value.isEmpty() ? 0xFF6B7280 : 0xFFE5E7EB;
            if (cycle) {
                renderNaturalCycleControl(guiGraphics, field.key(), inputX, inputY, mouseX, mouseY);
            } else {
                guiGraphics.drawString(font, trimToWidth(shown, NATURAL_INPUT_W - 8),
                        inputX + 4, inputY + 5, color);
                if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
                    int cursorX = inputX + 4 + font.width(trimToWidth(value, NATURAL_INPUT_W - 10));
                    guiGraphics.fill(Math.min(cursorX, inputX + NATURAL_INPUT_W - 4), inputY + 4,
                            Math.min(cursorX + 1, inputX + NATURAL_INPUT_W - 3), inputY + 14, 0xFFFFFFFF);
                }
            }
        }
    }

    private void renderNaturalRangeControl(GuiGraphics graphics, String key, int x, int y,
                                           int mouseX, int mouseY) {
        String[] keys = naturalRangeKeys(key);
        int gap = 12;
        int width = (NATURAL_INPUT_W - gap) / 2;
        renderNaturalRangeInput(graphics, keys[0], x, y, width,
                Component.translatable("gui.mobspawncontroller.natural.range.min").getString(), mouseX, mouseY);
        graphics.drawCenteredString(font, "~", x + width + gap / 2, y + 5, 0xFF94A3B8);
        renderNaturalRangeInput(graphics, keys[1], x + width + gap, y, width,
                Component.translatable("gui.mobspawncontroller.natural.range.max").getString(), mouseX, mouseY);
    }

    private void renderNaturalRangeInput(GuiGraphics graphics, String inputKey, int x, int y, int width,
                                         String placeholder, int mouseX, int mouseY) {
        boolean focused = inputKey.equals(focusedNaturalField);
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18;
        graphics.fill(x, y, x + width, y + 18, 0xFF111827);
        graphics.renderOutline(x, y, width, 18, focused ? ACCENT_COLOR : hovered ? 0xFF64748B : 0xFF374151);
        String value = naturalInputs.getOrDefault(inputKey, "");
        String shown = value.isEmpty() ? placeholder : value;
        graphics.drawString(font, trimToWidth(shown, width - 8), x + 4, y + 5,
                value.isEmpty() ? 0xFF6B7280 : 0xFFE5E7EB);
        if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
            int cursorX = Math.min(x + width - 4, x + 4 + font.width(trimToWidth(value, width - 9)));
            graphics.fill(cursorX, y + 4, cursorX + 1, y + 14, 0xFFFFFFFF);
        }
    }

    private static String[] naturalRangeKeys(String key) {
        return switch (key) {
            case "players_range" -> new String[]{"min_players", "max_players"};
            case "height_range" -> new String[]{"min_height", "max_height"};
            case "distance_range" -> new String[]{"min_distance", "max_distance"};
            case "spawn_distance_range" -> new String[]{"min_spawn_distance", "max_spawn_distance"};
            case "total_light_range" -> new String[]{"min_total_light", "max_total_light"};
            case "sky_light_range" -> new String[]{"min_sky_light", "max_sky_light"};
            case "block_light_range" -> new String[]{"min_block_light", "max_block_light"};
            case "time_range" -> new String[]{"min_time", "max_time"};
            case "day_range" -> new String[]{"min_day", "max_day"};
            case "local_difficulty_range" -> new String[]{"min_local_difficulty", "max_local_difficulty"};
            default -> throw new IllegalArgumentException("Unknown natural spawn range: " + key);
        };
    }

    private void renderNaturalPickerControl(GuiGraphics guiGraphics, String key, int x, int y,
                                            int mouseX, int mouseY) {
        int modeWidth = 62;
        int gap = 4;
        int pickerX = x + modeWidth + gap;
        int pickerWidth = NATURAL_INPUT_W - modeWidth - gap;
        SelectorMode mode = naturalSelectorModes.getOrDefault(key, SelectorMode.WHITELIST);
        boolean modeHovered = mouseX >= x && mouseX < x + modeWidth && mouseY >= y && mouseY < y + 18;
        boolean pickerHovered = mouseX >= pickerX && mouseX < pickerX + pickerWidth
                && mouseY >= y && mouseY < y + 18;
        int modeColor = mode == SelectorMode.WHITELIST ? 0xFF166534 : 0xFF7F1D1D;
        guiGraphics.fill(x, y, x + modeWidth, y + 18, modeHovered ? brighten(modeColor) : modeColor);
        guiGraphics.renderOutline(x, y, modeWidth, 18,
                mode == SelectorMode.WHITELIST ? 0xFF86EFAC : 0xFFFCA5A5);
        guiGraphics.drawCenteredString(font, Component.translatable("gui.mobspawncontroller.natural.option."
                        + mode.name().toLowerCase(Locale.ROOT)), x + modeWidth / 2, y + 5, 0xFFFFFFFF);

        int count = activeNaturalSelection(key).size();
        guiGraphics.fill(pickerX, y, pickerX + pickerWidth, y + 18, 0xFF111827);
        guiGraphics.renderOutline(pickerX, y, pickerWidth, 18, pickerHovered ? ACCENT_COLOR : 0xFF374151);
        String text = Component.translatable("gui.mobspawncontroller.natural.selected_count", count).getString();
        guiGraphics.drawString(font, trimToWidth(text, pickerWidth - 18), pickerX + 4, y + 5, 0xFFE5E7EB);
        guiGraphics.drawString(font, ">", pickerX + pickerWidth - 12, y + 5, 0xFF7DD3FC);
    }

    private void renderNaturalCycleControl(GuiGraphics guiGraphics, String key, int x, int y,
                                           int mouseX, int mouseY) {
        int arrowW = 18;
        int leftX = x;
        int rightX = x + NATURAL_INPUT_W - arrowW;
        boolean inputHovered = mouseX >= x && mouseX < x + NATURAL_INPUT_W
                && mouseY >= y && mouseY < y + 18;
        boolean leftHovered = mouseX >= leftX && mouseX < leftX + arrowW
                && mouseY >= y && mouseY < y + 18;
        boolean rightHovered = mouseX >= rightX && mouseX < rightX + arrowW
                && mouseY >= y && mouseY < y + 18;
        int leftBg = leftHovered ? 0xFF5A9CC0 : 0xFF4A7C9B;
        int rightBg = rightHovered ? 0xFF5A9CC0 : 0xFF4A7C9B;

        guiGraphics.fill(x, y, x + NATURAL_INPUT_W, y + 18, 0xFF111827);
        guiGraphics.renderOutline(x, y, NATURAL_INPUT_W, 18,
                inputHovered ? 0xFF64748B : 0xFF374151);
        guiGraphics.fill(leftX, y, leftX + arrowW, y + 18, leftBg);
        guiGraphics.fill(rightX, y, rightX + arrowW, y + 18, rightBg);
        guiGraphics.fill(leftX + arrowW, y, leftX + arrowW + 1, y + 18, 0xFF2C4A5F);
        guiGraphics.fill(rightX - 1, y, rightX, y + 18, 0xFF2C4A5F);

        guiGraphics.drawCenteredString(font, "<", leftX + arrowW / 2, y + 5,
                leftHovered ? 0xFFFFFFFF : 0xFFD6EAF5);
        guiGraphics.drawCenteredString(font, ">", rightX + arrowW / 2, y + 5,
                rightHovered ? 0xFFFFFFFF : 0xFFD6EAF5);

        String shown = naturalCycleLabel(key);
        int textWidth = NATURAL_INPUT_W - arrowW * 2 - 8;
        guiGraphics.drawString(font, trimToWidth(shown, textWidth),
                x + arrowW + 4, y + 5, 0xFFE5E7EB);
    }

    private String naturalCycleLabel(String key) {
        String value = switch (key) {
            case "weather" -> naturalWeather.name();
            case "difficulty" -> naturalDifficulty.name();
            case "sky" -> naturalSky.name();
            case "fluid" -> naturalFluid.name();
            case "slime_chunk" -> naturalSlimeChunk.name();
            default -> "ANY";
        };
        return Component.translatable("gui.mobspawncontroller.natural.option."
                + value.toLowerCase(Locale.ROOT)).getString();
    }

    private void renderActiveRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = listTop - (int) scrollOffset;
        int inputX = panelRight - PANEL_INSET - NATURAL_INPUT_W - 6;
        for (int i = 0; i < activeFields.size(); i++) {
            NaturalField field = activeFields.get(i);
            int rowY = y + i * NATURAL_ROW_HEIGHT;
            if (rowY + NATURAL_ROW_HEIGHT < listTop || rowY > listBottom) continue;
            boolean hovered = mouseX >= panelLeft + PANEL_INSET && mouseX < panelRight - PANEL_INSET
                    && mouseY >= rowY && mouseY < rowY + NATURAL_ROW_HEIGHT;
            graphics.fill(panelLeft + PANEL_INSET, rowY, panelRight - PANEL_INSET,
                    rowY + NATURAL_ROW_HEIGHT - 1, hovered ? ROW_HOVER_BG : (i % 2 == 0 ? ROW_BG : 0x08000000));

            int labelX = panelLeft + 16;
            int labelWidth = inputX - labelX - 10;
            String baseKey = "gui.mobspawncontroller.active." + field.key();
            graphics.drawString(font, trimToWidth(Component.translatable(baseKey).getString(), labelWidth),
                    labelX, rowY + 4, 0xFFE5E7EB);
            graphics.drawString(font, trimToWidth(Component.translatable(baseKey + ".hint").getString(), labelWidth),
                    labelX, rowY + 16, 0xFF7B8794);

            int inputY = rowY + 6;
            if (field.type() == NaturalFieldType.RANGE) {
                renderActiveRangeControl(graphics, field.key(), inputX, inputY, mouseX, mouseY);
            } else if (field.type() == NaturalFieldType.PICKER) {
                renderActivePickerControl(graphics, field.key(), inputX, inputY, mouseX, mouseY);
            } else if (field.type() == NaturalFieldType.CYCLE) {
                renderActiveCycleControl(graphics, field.key(), inputX, inputY, mouseX, mouseY);
            } else if (field.type() == NaturalFieldType.SWITCH) {
                boolean state = field.key().equals("enabled") ? activeEnabled : activeObeySpawnRules;
                int toggleX = inputX + NATURAL_INPUT_W - TOGGLE_W;
                drawToggle(graphics, toggleX, inputY + 1, state, mouseX, mouseY);
            } else {
                renderActiveNumberInput(graphics, field.key(), inputX, inputY, NATURAL_INPUT_W, "", mouseX, mouseY);
            }
        }
    }

    private void renderActiveRangeControl(GuiGraphics graphics, String key, int x, int y,
                                          int mouseX, int mouseY) {
        String[] keys = activeRangeKeys(key);
        int gap = 12;
        int width = (NATURAL_INPUT_W - gap) / 2;
        renderActiveNumberInput(graphics, keys[0], x, y, width,
                Component.translatable("gui.mobspawncontroller.natural.range.min").getString(), mouseX, mouseY);
        graphics.drawCenteredString(font, "~", x + width + gap / 2, y + 5, 0xFF94A3B8);
        renderActiveNumberInput(graphics, keys[1], x + width + gap, y, width,
                Component.translatable("gui.mobspawncontroller.natural.range.max").getString(), mouseX, mouseY);
    }

    private void renderActiveNumberInput(GuiGraphics graphics, String key, int x, int y, int width,
                                         String placeholder, int mouseX, int mouseY) {
        boolean focused = key.equals(focusedActiveField);
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18;
        graphics.fill(x, y, x + width, y + 18, 0xFF111827);
        graphics.renderOutline(x, y, width, 18, focused ? ACCENT_COLOR : hovered ? 0xFF64748B : 0xFF374151);
        String value = activeInputs.getOrDefault(key, "");
        String shown = value.isEmpty() ? (placeholder.isEmpty()
                ? Component.translatable("gui.mobspawncontroller.natural.unlimited").getString() : placeholder) : value;
        graphics.drawString(font, trimToWidth(shown, width - 8), x + 4, y + 5,
                value.isEmpty() ? 0xFF6B7280 : 0xFFE5E7EB);
        if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
            int cursorX = Math.min(x + width - 4, x + 4 + font.width(trimToWidth(value, width - 9)));
            graphics.fill(cursorX, y + 4, cursorX + 1, y + 14, 0xFFFFFFFF);
        }
    }

    private static String[] activeRangeKeys(String key) {
        return switch (key) {
            case "amount_range" -> new String[]{"min_amount", "max_amount"};
            case "players_range" -> new String[]{"min_players", "max_players"};
            case "distance_range" -> new String[]{"min_distance", "max_distance"};
            case "spawn_distance_range" -> new String[]{"min_spawn_distance", "max_spawn_distance"};
            case "height_range" -> new String[]{"min_height", "max_height"};
            case "day_range" -> new String[]{"min_day", "max_day"};
            case "time_range" -> new String[]{"min_time", "max_time"};
            case "total_light_range" -> new String[]{"min_total_light", "max_total_light"};
            case "sky_light_range" -> new String[]{"min_sky_light", "max_sky_light"};
            case "block_light_range" -> new String[]{"min_block_light", "max_block_light"};
            case "local_difficulty_range" -> new String[]{"min_local_difficulty", "max_local_difficulty"};
            default -> throw new IllegalArgumentException("Unknown active spawn range: " + key);
        };
    }

    private void renderActivePickerControl(GuiGraphics graphics, String key, int x, int y,
                                           int mouseX, int mouseY) {
        int modeWidth = 62;
        int gap = 4;
        int pickerX = x + modeWidth + gap;
        int pickerWidth = NATURAL_INPUT_W - modeWidth - gap;
        SelectorMode mode = activeSelectorModes.getOrDefault(key, SelectorMode.WHITELIST);
        boolean modeHovered = mouseX >= x && mouseX < x + modeWidth && mouseY >= y && mouseY < y + 18;
        boolean pickerHovered = mouseX >= pickerX && mouseX < pickerX + pickerWidth
                && mouseY >= y && mouseY < y + 18;
        int modeColor = mode == SelectorMode.WHITELIST ? 0xFF166534 : 0xFF7F1D1D;
        graphics.fill(x, y, x + modeWidth, y + 18, modeHovered ? brighten(modeColor) : modeColor);
        graphics.renderOutline(x, y, modeWidth, 18,
                mode == SelectorMode.WHITELIST ? 0xFF86EFAC : 0xFFFCA5A5);
        graphics.drawCenteredString(font, Component.translatable("gui.mobspawncontroller.natural.option."
                + mode.name().toLowerCase(Locale.ROOT)), x + modeWidth / 2, y + 5, 0xFFFFFFFF);

        int count = activeSelection(key, mode).size();
        graphics.fill(pickerX, y, pickerX + pickerWidth, y + 18, 0xFF111827);
        graphics.renderOutline(pickerX, y, pickerWidth, 18, pickerHovered ? ACCENT_COLOR : 0xFF374151);
        String text = Component.translatable("gui.mobspawncontroller.natural.selected_count", count).getString();
        graphics.drawString(font, trimToWidth(text, pickerWidth - 18), pickerX + 4, y + 5, 0xFFE5E7EB);
        graphics.drawString(font, ">", pickerX + pickerWidth - 12, y + 5, 0xFF7DD3FC);
    }

    private void renderActiveCycleControl(GuiGraphics graphics, String key, int x, int y,
                                          int mouseX, int mouseY) {
        int arrowW = 18;
        int rightX = x + NATURAL_INPUT_W - arrowW;
        boolean hovered = mouseX >= x && mouseX < x + NATURAL_INPUT_W && mouseY >= y && mouseY < y + 18;
        boolean leftHovered = mouseX < x + arrowW && hovered;
        boolean rightHovered = mouseX >= rightX && hovered;
        graphics.fill(x, y, x + NATURAL_INPUT_W, y + 18, 0xFF111827);
        graphics.renderOutline(x, y, NATURAL_INPUT_W, 18, hovered ? 0xFF64748B : 0xFF374151);
        graphics.fill(x, y, x + arrowW, y + 18, leftHovered ? 0xFF5A9CC0 : 0xFF4A7C9B);
        graphics.fill(rightX, y, rightX + arrowW, y + 18, rightHovered ? 0xFF5A9CC0 : 0xFF4A7C9B);
        graphics.drawCenteredString(font, "<", x + arrowW / 2, y + 5, 0xFFFFFFFF);
        graphics.drawCenteredString(font, ">", rightX + arrowW / 2, y + 5, 0xFFFFFFFF);
        graphics.drawString(font, trimToWidth(activeCycleLabel(key), NATURAL_INPUT_W - arrowW * 2 - 8),
                x + arrowW + 4, y + 5, 0xFFE5E7EB);
    }

    private String activeCycleLabel(String key) {
        if (key.equals("placement")) {
            return Component.translatable("gui.mobspawncontroller.active.option."
                    + activePlacement.name().toLowerCase(Locale.ROOT)).getString();
        }
        String value = switch (key) {
            case "weather" -> activeWeather.name();
            case "difficulty" -> activeDifficulty.name();
            case "slime_chunk" -> activeSlimeChunk.name();
            default -> activeSky.name();
        };
        return Component.translatable("gui.mobspawncontroller.natural.option."
                + value.toLowerCase(Locale.ROOT)).getString();
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

        if (activeTab != DetailTab.SPAWN_RULES && handleHeaderResetClick(mouseX, mouseY)) {
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

        if (activeTab == DetailTab.NATURAL_SPAWN) {
            return handleNaturalClick(mouseX, mouseY);
        }

        if (activeTab == DetailTab.ACTIVE_SPAWN) {
            return handleActiveClick(mouseX, mouseY);
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

    private boolean handleNaturalClick(double mouseX, double mouseY) {
        if (mouseX < panelLeft || mouseX > panelRight || mouseY < listTop || mouseY > listBottom) {
            focusedNaturalField = null;
            return false;
        }
        int y = listTop - (int) scrollOffset;
        int inputX = panelRight - PANEL_INSET - NATURAL_INPUT_W - 6;
        for (int i = 0; i < naturalFields.size(); i++) {
            NaturalField field = naturalFields.get(i);
            int rowY = y + i * NATURAL_ROW_HEIGHT;
            int inputY = rowY + 6;
            if (mouseX < inputX || mouseX >= inputX + NATURAL_INPUT_W
                    || mouseY < inputY || mouseY >= inputY + 18) continue;
            if (field.type() == NaturalFieldType.RANGE) {
                String[] keys = naturalRangeKeys(field.key());
                int gap = 12;
                int rangeWidth = (NATURAL_INPUT_W - gap) / 2;
                focusedNaturalField = null;
                if (mouseX < inputX + rangeWidth) {
                    focusedNaturalField = keys[0];
                } else if (mouseX >= inputX + rangeWidth + gap) {
                    focusedNaturalField = keys[1];
                }
                focusedAttributeId = null;
            } else if (field.type() == NaturalFieldType.PICKER) {
                int modeWidth = 62;
                if (mouseX < inputX + modeWidth) {
                    toggleNaturalSelectorMode(field.key());
                } else {
                    openNaturalPicker(field.key());
                }
                focusedNaturalField = null;
            } else if (field.type() == NaturalFieldType.CYCLE) {
                focusedNaturalField = null;
                int arrowW = 18;
                boolean previous = mouseX < inputX + arrowW;
                switch (field.key()) {
                    case "weather" -> naturalWeather = previous ? naturalWeather.previous() : naturalWeather.next();
                    case "difficulty" -> naturalDifficulty = previous ? naturalDifficulty.previous() : naturalDifficulty.next();
                    case "sky" -> naturalSky = previous ? naturalSky.previous() : naturalSky.next();
                    case "fluid" -> naturalFluid = previous ? naturalFluid.previous() : naturalFluid.next();
                    case "slime_chunk" -> naturalSlimeChunk = previous ? naturalSlimeChunk.previous() : naturalSlimeChunk.next();
                }
            } else {
                focusedNaturalField = field.key();
                focusedAttributeId = null;
            }
            return true;
        }
        focusedNaturalField = null;
        return false;
    }

    private boolean handleActiveClick(double mouseX, double mouseY) {
        if (mouseX < panelLeft || mouseX > panelRight || mouseY < listTop || mouseY > listBottom) {
            focusedActiveField = null;
            return false;
        }
        int y = listTop - (int) scrollOffset;
        int inputX = panelRight - PANEL_INSET - NATURAL_INPUT_W - 6;
        for (int i = 0; i < activeFields.size(); i++) {
            NaturalField field = activeFields.get(i);
            int inputY = y + i * NATURAL_ROW_HEIGHT + 6;
            if (mouseX < inputX || mouseX >= inputX + NATURAL_INPUT_W
                    || mouseY < inputY || mouseY >= inputY + 18) continue;
            focusedActiveField = null;
            focusedNaturalField = null;
            focusedAttributeId = null;
            if (field.type() == NaturalFieldType.RANGE) {
                String[] keys = activeRangeKeys(field.key());
                int gap = 12;
                int rangeWidth = (NATURAL_INPUT_W - gap) / 2;
                if (mouseX < inputX + rangeWidth) focusedActiveField = keys[0];
                else if (mouseX >= inputX + rangeWidth + gap) focusedActiveField = keys[1];
            } else if (field.type() == NaturalFieldType.PICKER) {
                if (mouseX < inputX + 62) {
                    SelectorMode current = activeSelectorModes.getOrDefault(field.key(), SelectorMode.WHITELIST);
                    activeSelectorModes.put(field.key(), current.next());
                } else {
                    openActivePicker(field.key());
                }
            } else if (field.type() == NaturalFieldType.CYCLE) {
                boolean previous = mouseX < inputX + 18;
                switch (field.key()) {
                    case "placement" -> activePlacement = previous ? activePlacement.previous() : activePlacement.next();
                    case "weather" -> activeWeather = previous ? activeWeather.previous() : activeWeather.next();
                    case "difficulty" -> activeDifficulty = previous ? activeDifficulty.previous() : activeDifficulty.next();
                    case "slime_chunk" -> activeSlimeChunk = previous ? activeSlimeChunk.previous() : activeSlimeChunk.next();
                    default -> activeSky = previous ? activeSky.previous() : activeSky.next();
                }
            } else if (field.type() == NaturalFieldType.SWITCH) {
                if (field.key().equals("enabled")) activeEnabled = !activeEnabled;
                else activeObeySpawnRules = !activeObeySpawnRules;
            } else {
                focusedActiveField = field.key();
            }
            return true;
        }
        focusedActiveField = null;
        return false;
    }

    private void openActivePicker(String key) {
        SelectorMode mode = activeSelectorModes.getOrDefault(key, SelectorMode.WHITELIST);
        if (key.equals("block_below_list") || key.equals("block_at_list") || key.equals("block_above_list")) {
            Component title = Component.translatable("gui.mobspawncontroller.active." + key).copy()
                    .append(" · ").append(Component.translatable("gui.mobspawncontroller.natural.option."
                            + mode.name().toLowerCase(Locale.ROOT)));
            Minecraft.getInstance().setScreen(new BlockIdListEditScreen(this, title,
                    activeSelection(key, mode), selected -> setActiveSelection(key, mode, selected)));
            return;
        }
        List<NaturalRegistryPickerScreen.Option> options;
        try {
            options = naturalPickerOptions(key);
        } catch (Exception exception) {
            MobSpawnController.LOGGER.warn("Failed to collect active spawn options for {}", key, exception);
            options = List.of();
        }
        Component title = Component.translatable("gui.mobspawncontroller.active." + key);
        Minecraft.getInstance().setScreen(new NaturalRegistryPickerScreen(this, title, options,
                activeSelection(key, mode), selected -> setActiveSelection(key, mode, selected)));
    }

    private void setActiveSelection(String key, SelectorMode mode, List<String> selected) {
        activeSelections.computeIfAbsent(key, ignored -> new EnumMap<>(SelectorMode.class))
                .put(mode, new ArrayList<>(selected));
    }

    private void openNaturalPicker(String key) {
        if (key.equals("block_below_list") || key.equals("block_at_list") || key.equals("block_above_list")) {
            Component fieldTitle = Component.translatable("gui.mobspawncontroller.natural." + key);
            SelectorMode mode = naturalSelectorModes.getOrDefault(key, SelectorMode.WHITELIST);
            Component modeLabel = Component.translatable("gui.mobspawncontroller.natural.option."
                    + mode.name().toLowerCase(Locale.ROOT));
            Component title = fieldTitle.copy().append(" · ").append(modeLabel);
            Minecraft.getInstance().setScreen(new BlockIdListEditScreen(this, title,
                    naturalSelection(key, mode), selected -> setNaturalSelection(key, mode, selected)));
            return;
        }
        List<NaturalRegistryPickerScreen.Option> options;
        try {
            options = naturalPickerOptions(key);
        } catch (Exception exception) {
            MobSpawnController.LOGGER.warn("Failed to collect registry options for {}", key, exception);
            options = List.of();
        }
        Component title = Component.translatable("gui.mobspawncontroller.natural." + key);
        SelectorMode mode = naturalSelectorModes.getOrDefault(key, SelectorMode.WHITELIST);
        Minecraft.getInstance().setScreen(new NaturalRegistryPickerScreen(this, title, options,
                naturalSelection(key, mode), selected -> setNaturalSelection(key, mode, selected)));
    }

    private List<NaturalRegistryPickerScreen.Option> naturalPickerOptions(String key) {
        Minecraft mc = Minecraft.getInstance();
        List<NaturalRegistryPickerScreen.Option> options = new ArrayList<>();
        if (key.equals("spawn_type_list")) {
            for (MobSpawnType spawnType : MobSpawnType.values()) {
                String value = spawnType.name().toLowerCase(Locale.ROOT);
                String label = Component.translatable("gui.mobspawncontroller.spawntype." + value).getString();
                options.add(new NaturalRegistryPickerScreen.Option(value, label));
            }
        } else if (key.equals("moon_phase_list")) {
            for (int phase = 0; phase < 8; phase++) {
                String value = String.valueOf(phase);
                String label = Component.translatable("gui.mobspawncontroller.natural.moon_phase." + phase).getString();
                options.add(new NaturalRegistryPickerScreen.Option(value, label));
            }
        } else if (key.equals("season_list")) {
            for (String season : SereneSeasonsCompat.SEASONS) {
                String label = Component.translatable("gui.mobspawncontroller.natural.season." + season).getString();
                options.add(new NaturalRegistryPickerScreen.Option(season, label));
            }
        } else if (key.equals("dimension_list") && mc.getConnection() != null) {
            mc.getConnection().levels().stream().map(value -> value.location().toString())
                    .forEach(value -> options.add(new NaturalRegistryPickerScreen.Option(value, value)));
        } else if (key.equals("biome_list") && mc.level != null) {
            mc.level.registryAccess().registry(Registries.BIOME)
                    .ifPresent(registry -> addRegistryOptions(options, registry, true));
        } else if (key.equals("structure_list")) {
            options.add(new NaturalRegistryPickerScreen.Option("*",
                    Component.translatable("gui.mobspawncontroller.natural.any_structure").getString()));
            for (String entry : ClientRuleSync.getCachedStructureEntries()) {
                options.add(new NaturalRegistryPickerScreen.Option(entry, entry));
            }
            for (String tag : ClientRuleSync.getCachedStructureTags()) {
                String value = "#" + tag;
                options.add(new NaturalRegistryPickerScreen.Option(value, value));
            }
        }

        options.sort(java.util.Comparator.comparing(NaturalRegistryPickerScreen.Option::value));
        return options;
    }

    private static void addRegistryOptions(List<NaturalRegistryPickerScreen.Option> options,
                                           Registry<?> registry, boolean includeTags) {
        registry.keySet().forEach(id -> options.add(
                new NaturalRegistryPickerScreen.Option(id.toString(), id.toString())));
        if (includeTags) {
            registry.getTagNames().forEach(tag -> {
                String value = "#" + tag.location();
                options.add(new NaturalRegistryPickerScreen.Option(value, value));
            });
        }
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
            if (activeTab == DetailTab.NATURAL_SPAWN) {
                resetNaturalSettings();
            } else if (activeTab == DetailTab.ACTIVE_SPAWN) {
                resetActiveSettings();
            } else {
                resetAllAttributes();
            }
            return true;
        }
        return false;
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        int tabY = panelTop + HEADER_HEIGHT - TAB_HEIGHT - 6;
        int tabWidth = (panelRight - panelLeft - PANEL_INSET * 2 - 12) / 4;
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
            setActiveTab(DetailTab.NATURAL_SPAWN);
            return true;
        }
        int thirdTabX = firstTabX + (tabWidth + 4) * 2;
        if (mouseX >= thirdTabX && mouseX < thirdTabX + tabWidth) {
            setActiveTab(DetailTab.ACTIVE_SPAWN);
            return true;
        }
        int fourthTabX = firstTabX + (tabWidth + 4) * 3;
        if (mouseX >= fourthTabX && mouseX < fourthTabX + tabWidth) {
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
        focusedNaturalField = null;
        focusedActiveField = null;
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
    public void onNaturalSpawnSettingsReceived(Map<ResourceLocation, NaturalSpawnSettings> settings) {
        parent.onNaturalSpawnSettingsReceived(settings);
        loadNaturalSettings(settings.getOrDefault(mobId, NaturalSpawnSettings.defaults()));
    }

    @Override
    public void onActiveSpawnSettingsReceived(Map<ResourceLocation, ActiveSpawnSettings> settings) {
        parent.onActiveSpawnSettingsReceived(settings);
        loadActiveSettings(settings.getOrDefault(mobId, ActiveSpawnSettings.defaults()));
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
    public void onStructuresReceived(List<String> entries, List<String> tags) {
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
        if (focusedActiveField != null && codePoint >= 32 && codePoint != 127 && isNumericInputChar(codePoint)) {
            String value = activeInputs.getOrDefault(focusedActiveField, "");
            if (value.length() < 256) activeInputs.put(focusedActiveField, value + codePoint);
            return true;
        }
        if (focusedNaturalField != null && codePoint >= 32 && codePoint != 127) {
            if (isNumericInputChar(codePoint)) {
                String value = naturalInputs.getOrDefault(focusedNaturalField, "");
                if (value.length() < 256) naturalInputs.put(focusedNaturalField, value + codePoint);
                return true;
            }
        }
        if (focusedAttributeId != null && isNumericInputChar(codePoint)) {
            updateFocusedAttributeInput(attributeInputs.getOrDefault(focusedAttributeId, "") + codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedActiveField != null) {
            String value = activeInputs.getOrDefault(focusedActiveField, "");
            if (Screen.isPaste(keyCode)) {
                String pasted = Minecraft.getInstance().keyboardHandler.getClipboard().chars()
                        .mapToObj(chr -> String.valueOf((char) chr))
                        .filter(chr -> isNumericInputChar(chr.charAt(0))).collect(Collectors.joining());
                activeInputs.put(focusedActiveField, (value + pasted).substring(0,
                        Math.min(256, value.length() + pasted.length())));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!value.isEmpty()) activeInputs.put(focusedActiveField, value.substring(0, value.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                activeInputs.put(focusedActiveField, "");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focusedActiveField = null;
                return true;
            }
        }
        if (focusedNaturalField != null) {
            String value = naturalInputs.getOrDefault(focusedNaturalField, "");
            if (Screen.isPaste(keyCode)) {
                String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
                pasted = pasted.chars().mapToObj(chr -> String.valueOf((char) chr))
                        .filter(chr -> isNumericInputChar(chr.charAt(0))).collect(Collectors.joining());
                naturalInputs.put(focusedNaturalField, (value + pasted).substring(0,
                        Math.min(256, value.length() + pasted.length())));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!value.isEmpty()) naturalInputs.put(focusedNaturalField, value.substring(0, value.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                naturalInputs.put(focusedNaturalField, "");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focusedNaturalField = null;
                return true;
            }
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * 16, getMaxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
