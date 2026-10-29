/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.AbstractButton
 *  net.minecraft.client.gui.components.AbstractSliderButton
 *  net.minecraft.client.gui.components.AbstractWidget
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.narration.NarrationElementOutput
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.InputWithModifiers
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.CommonComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 */
package dev.lumalight.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.lumalight.client.CartoonEffectsConfig;
import dev.lumalight.client.ConfigTranslations;
import dev.lumalight.client.ConfigValue;
import dev.lumalight.client.OutlineVisibility;
import dev.lumalight.client.RimLightConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class LumaLightConfigScreen
extends Screen {
    private static final int CYAN = -11147040;
    private static final int WHITE = -720897;
    private static final int MUTED = -6305845;
    private static final int GLASS = -1256704965;
    private static final int GLASS_HOVER = -786017703;
    private static final int CARD_HEIGHT = 36;
    private static final int GAP = 7;
    private final Screen lastScreen;
    private final List<Setting> settings = new ArrayList<Setting>();
    private Category category = Category.GENERAL;
    private Preset activePreset = Preset.fromId(CartoonEffectsConfig.ACTIVE_PROFILE.get());
    private String searchText = "";
    private int scrollRow;
    private boolean searchDirty;
    private boolean refreshing;
    private EditBox searchBox;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int columns;
    private int visibleRows;
    private int totalRows;

    public LumaLightConfigScreen(Screen lastScreen) {
        super(LumaLightConfigScreen.tr("title"));
        this.lastScreen = lastScreen;
        this.createSettings();
    }

    protected void init() {
        this.refreshing = true;
        this.panelWidth = Math.min(940, this.width - 20);
        this.panelHeight = Math.min(570, this.height - 18);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.sidebarWidth = this.panelWidth >= 700 ? 174 : 142;
        this.contentX = this.panelX + this.sidebarWidth + 15;
        this.contentY = this.panelY + 67;
        this.contentWidth = this.panelWidth - this.sidebarWidth - 27;
        this.contentHeight = this.panelHeight - 116;
        this.columns = this.contentWidth >= 290 ? 2 : 1;
        this.visibleRows = Math.max(1, (this.contentHeight + 7) / 43);
        this.searchBox = new EditBox(this.font, this.contentX + 29, this.panelY + 24, this.contentWidth - 37, 26, LumaLightConfigScreen.tr("search"));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(-720897);
        this.searchBox.setTextColorUneditable(-6305845);
        this.searchBox.setHint(LumaLightConfigScreen.tr("search.hint"));
        this.searchBox.setMaxLength(96);
        this.searchBox.setValue(this.searchText);
        this.searchBox.setResponder(valuex -> {
            if (!this.refreshing && !Objects.equals(this.searchText, valuex)) {
                this.searchText = valuex;
                this.scrollRow = 0;
                this.searchDirty = true;
            }
        });
        this.addRenderableWidget(this.searchBox);
        int buttonY = this.panelY + 52;
        int buttonWidth = this.sidebarWidth - 20;
        int categoryHeight = Mth.clamp((int)((this.panelHeight - 60) / Category.values().length - 2), (int)18, (int)25);
        for (Category value : Category.values()) {
            this.addRenderableWidget(new GlassButton(this.panelX + 10, buttonY, buttonWidth, categoryHeight, value.label(), () -> this.selectCategory(value), () -> this.searchText.isBlank() && this.category == value, true));
            buttonY += categoryHeight + 2;
        }
        int footerButtonWidth = Math.min(100, (this.contentWidth - 14) / 3);
        int footerX = this.contentX + this.contentWidth - footerButtonWidth * 3 - 14;
        int footerY = this.panelY + this.panelHeight - 34;
        this.addRenderableWidget(new GlassButton(footerX, footerY, footerButtonWidth, 24, LumaLightConfigScreen.tr("reset_category"), this::resetCategory, () -> false, true));
        this.addRenderableWidget(new GlassButton(footerX + footerButtonWidth + 7, footerY, footerButtonWidth, 24, LumaLightConfigScreen.tr("reset_all"), this::resetAll, () -> false, true));
        this.addRenderableWidget(new GlassButton(footerX + (footerButtonWidth + 7) * 2, footerY, footerButtonWidth, 24, LumaLightConfigScreen.tr("done"), this::onClose, () -> false, true));
        this.addVisibleSettings();
        this.refreshing = false;
    }

    public void tick() {
        if (this.searchDirty) {
            this.searchDirty = false;
            this.rebuildWidgets();
            if (this.searchBox != null) {
                this.searchBox.setFocused(true);
                this.searchBox.moveCursorToEnd(false);
                this.setFocused((GuiEventListener)this.searchBox);
            }
        }
    }

    private void addVisibleSettings() {
        List<Setting> filtered = this.filteredSettings();
        this.totalRows = (filtered.size() + this.columns - 1) / this.columns;
        this.scrollRow = Mth.clamp((int)this.scrollRow, (int)0, (int)Math.max(0, this.totalRows - this.visibleRows));
        int start = this.scrollRow * this.columns;
        int end = Math.min(filtered.size(), start + this.visibleRows * this.columns);
        int cardWidth = (this.contentWidth - (this.columns - 1) * 7) / this.columns;
        for (int index = start; index < end; ++index) {
            int slot = index - start;
            int column = slot % this.columns;
            int row = slot / this.columns;
            int x = this.contentX + column * (cardWidth + 7);
            int y = this.contentY + row * 43;
            this.addRenderableWidget(filtered.get(index).widget(x, y, cardWidth, 36));
        }
    }

    private List<Setting> filteredSettings() {
        String needle = this.searchText.trim().toLowerCase(Locale.ROOT);
        return this.settings.stream().filter(setting -> needle.isEmpty() ? setting.category == this.category : setting.label().getString().toLowerCase(Locale.ROOT).contains(needle) || setting.searchKey.toLowerCase(Locale.ROOT).contains(needle) || setting.category.label().getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private void selectCategory(Category selected) {
        this.category = selected;
        this.searchText = "";
        this.scrollRow = 0;
        this.rebuildWidgets();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= (double)this.contentX && mouseX <= (double)(this.contentX + this.contentWidth) && mouseY >= (double)this.contentY && mouseY <= (double)(this.contentY + this.contentHeight)) {
            int old = this.scrollRow;
            this.scrollRow = Mth.clamp((int)(this.scrollRow - (int)Math.signum(vertical)), (int)0, (int)Math.max(0, this.totalRows - this.visibleRows));
            if (old != this.scrollRow) {
                this.rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fillGradient(0, 0, this.width, this.height, -1190715352, -586803428);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(this.panelX + 4, this.panelY + 5, this.panelX + this.panelWidth + 5, this.panelY + this.panelHeight + 6, 2046822411);
        graphics.fillGradient(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, -434422973, -469098458);
        graphics.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, -1246101505);
        graphics.horizontalLine(this.panelX + 1, this.panelX + this.panelWidth - 2, this.panelY + 1, -1459617793);
        graphics.fill(this.panelX + this.sidebarWidth, this.panelY + 1, this.panelX + this.sidebarWidth + 1, this.panelY + this.panelHeight - 1, -2074744608);
        graphics.fillGradient(this.panelX + 1, this.panelY + 1, this.panelX + this.sidebarWidth, this.panelY + this.panelHeight - 1, 1395616112, 638724656);
        graphics.text(this.font, (Component)Component.literal((String)"LUMA").withColor(-720897), this.panelX + 13, this.panelY + 13, -720897, true);
        graphics.text(this.font, (Component)Component.literal((String)"LIGHT").withColor(-11147040), this.panelX + 43, this.panelY + 13, -11147040, true);
        graphics.text(this.font, LumaLightConfigScreen.tr("live"), this.panelX + 13, this.panelY + 30, -6305845, false);
        LumaLightConfigScreen.glass(graphics, this.contentX, this.panelY + 20, this.contentWidth, 34, false);
        graphics.text(this.font, (Component)Component.literal((String)"\u2315"), this.contentX + 9, this.panelY + 32, -11147040, false);
        Component heading = this.searchText.isBlank() ? this.category.label() : LumaLightConfigScreen.tr("search.results");
        String count = Integer.toString(this.filteredSettings().size());
        graphics.text(this.font, LumaLightConfigScreen.fit(this.font, heading, this.contentWidth - this.font.width(count) - 12), this.contentX, this.panelY + 58, -720897, true);
        graphics.text(this.font, count, this.contentX + this.contentWidth - this.font.width(count), this.panelY + 58, -6305845, false);
        if (this.totalRows > this.visibleRows) {
            int trackX = this.contentX + this.contentWidth + 5;
            int trackHeight = this.contentHeight;
            graphics.fill(trackX, this.contentY, trackX + 2, this.contentY + trackHeight, 0x4AFFFFFF);
            int thumbHeight = Math.max(18, trackHeight * this.visibleRows / this.totalRows);
            int thumbY = this.contentY + (trackHeight - thumbHeight) * this.scrollRow / Math.max(1, this.totalRows - this.visibleRows);
            graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, -11147040);
        }
        if (this.contentWidth > 500) {
            graphics.text(this.font, LumaLightConfigScreen.tr("hint.reset"), this.contentX, this.panelY + this.panelHeight - 27, -6305845, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    public void onClose() {
        this.saveAll();
        this.minecraft.gui.setScreen(this.lastScreen);
    }

    public void removed() {
        this.saveAll();
        super.removed();
    }

    public boolean isPauseScreen() {
        return false;
    }

    private void resetAll() {
        RimLightConfig.reset();
        CartoonEffectsConfig.reset();
        this.activePreset = Preset.CUSTOM;
        CartoonEffectsConfig.ACTIVE_PROFILE.set(this.activePreset.ordinal());
        this.saveAll();
        this.rebuildWidgets();
    }

    private void resetCategory() {
        this.settings.stream().filter(setting -> setting.category == this.category).forEach(Setting::resetValue);
        this.activePreset = Preset.CUSTOM;
        CartoonEffectsConfig.ACTIVE_PROFILE.set(this.activePreset.ordinal());
        this.saveAll();
        this.rebuildWidgets();
    }

    private void saveAll() {
        RimLightConfig.save();
        CartoonEffectsConfig.save();
        OutlineVisibility.clear();
    }

    private void createSettings() {
        this.preset("preset.performance", Preset.PERFORMANCE);
        this.preset("preset.balanced", Preset.BALANCED);
        this.preset("preset.vivid", Preset.VIVID);
        this.bool(Category.GENERAL, "cartoon.enabled", CartoonEffectsConfig.ENABLED);
        this.bool(Category.OUTLINES, "rim.enabled", RimLightConfig.ENABLED);
        this.number(Category.OUTLINES, "rim.thickness", RimLightConfig.THICKNESS, 1.0, 12.0, 1);
        this.number(Category.OUTLINES, "rim.strength", RimLightConfig.STRENGTH, 0.0, 5.0, 2);
        this.number(Category.OUTLINES, "rim.depth_threshold", RimLightConfig.DEPTH_THRESHOLD, 0.01, 2.0, 2);
        this.number(Category.OUTLINES, "rim.max_distance", RimLightConfig.MAX_DISTANCE, 1.0, 1024.0, 0);
        this.number(Category.OUTLINES, "rim.scene_color", RimLightConfig.SCENE_COLOR_INFLUENCE, 0.0, 1.0, 2);
        this.number(Category.OUTLINES, "rim.red", RimLightConfig.COLOR_RED, 0.0, 2.0, 2);
        this.number(Category.OUTLINES, "rim.green", RimLightConfig.COLOR_GREEN, 0.0, 2.0, 2);
        this.number(Category.OUTLINES, "rim.blue", RimLightConfig.COLOR_BLUE, 0.0, 2.0, 2);
        this.bool(Category.OUTLINES, "rim.foliage", RimLightConfig.OUTLINE_FOLIAGE);
        this.bool(Category.OUTLINES, "items.rarity", CartoonEffectsConfig.RARITY_OUTLINES);
        this.bool(Category.OUTLINES, "entities.damage", CartoonEffectsConfig.DAMAGE_FLASH);
        this.bool(Category.OUTLINES, "block_shading", RimLightConfig.BLOCK_SHADING_ENABLED);
        this.bool(Category.OUTLINES, "world.ores", CartoonEffectsConfig.ORE_OUTLINES);
        this.number(Category.OUTLINES, "world.ore_thickness", CartoonEffectsConfig.ORE_OUTLINE_THICKNESS, 1.0, 10.0, 1);
        this.number(Category.OUTLINES, "world.ore_opacity", CartoonEffectsConfig.ORE_OUTLINE_OPACITY, 0.1, 1.0, 2);
        this.number(Category.OUTLINES, "world.ore_brightness", CartoonEffectsConfig.ORE_OUTLINE_BRIGHTNESS, 0.0, 1.0, 2);
        this.integer(Category.OUTLINES, "world.ore_distance", CartoonEffectsConfig.ORE_OUTLINE_DISTANCE, 4, 24);
        this.integer(Category.OUTLINES, "world.ore_limit", CartoonEffectsConfig.ORE_OUTLINE_MAX_COUNT, 32, 512);
        this.number(Category.OUTLINES, "world.ore_pulse", CartoonEffectsConfig.ORE_OUTLINE_PULSE, 0.0, 0.5, 2);
        this.bool(Category.CARTOON, "cartoon.posterization", CartoonEffectsConfig.POSTERIZATION);
        this.integer(Category.CARTOON, "cartoon.levels", CartoonEffectsConfig.POSTERIZATION_LEVELS, 3, 24);
        this.number(Category.CARTOON, "cartoon.poster_strength", CartoonEffectsConfig.POSTERIZATION_STRENGTH, 0.0, 1.0, 2);
        this.bool(Category.CARTOON, "cartoon.dither", CartoonEffectsConfig.POSTERIZATION_DITHER);
        this.bool(Category.CARTOON, "cartoon.sky_outline", CartoonEffectsConfig.SKY_OUTLINES);
        this.bool(Category.CARTOON, "cartoon.water", CartoonEffectsConfig.WATER_RIMS);
        this.bool(Category.CARTOON, "cartoon.leaves", CartoonEffectsConfig.LEAF_RIMS);
        this.number(Category.CARTOON, "cartoon.green_saturation", CartoonEffectsConfig.GREEN_SATURATION, 0.0, 0.6, 2);
        this.bool(Category.CARTOON, "cartoon.chroma", CartoonEffectsConfig.CHROMATIC_EDGES);
        this.bool(Category.CARTOON, "cartoon.night_emissive", CartoonEffectsConfig.NIGHT_EMISSIVE);
        this.bool(Category.CARTOON, "cartoon.weather", CartoonEffectsConfig.WEATHER_PALETTE);
        this.number(Category.CARTOON, "cartoon.saturation", CartoonEffectsConfig.GLOBAL_SATURATION, 0.0, 2.0, 2);
        this.number(Category.CARTOON, "cartoon.vibrance", CartoonEffectsConfig.GLOBAL_VIBRANCE, 0.0, 1.0, 2);
        this.number(Category.CARTOON, "cartoon.temperature", CartoonEffectsConfig.COLOR_TEMPERATURE, -1.0, 1.0, 2);
        this.number(Category.CARTOON, "cartoon.brightness", CartoonEffectsConfig.SCENE_BRIGHTNESS, 0.5, 1.5, 2);
        this.number(Category.CARTOON, "cartoon.shadow_lift", CartoonEffectsConfig.SHADOW_LIFT, 0.0, 0.3, 3);
        this.number(Category.CARTOON, "exposure.highlights", CartoonEffectsConfig.HIGHLIGHT_COMPRESSION, 0.0, 1.0, 2);
        this.number(Category.CARTOON, "comfort.shadow_chroma", CartoonEffectsConfig.COMFORT_SHADOW_CHROMA, 0.0, 1.0, 2);
        this.number(Category.CARTOON, "comfort.white", CartoonEffectsConfig.COMFORT_WHITE_PROTECTION, 0.0, 1.0, 2);
        this.number(Category.CARTOON, "comfort.black", CartoonEffectsConfig.COMFORT_BLACK_PROTECTION, 0.0, 0.6, 2);
        this.number(Category.CARTOON, "comfort.low_contrast", CartoonEffectsConfig.COMFORT_LOW_CONTRAST, 0.0, 0.7, 2);
        this.bool(Category.CARTOON, "comfort.motion", CartoonEffectsConfig.COMFORT_MOTION_REDUCTION);
    }

    private void bool(Category category, String key, ConfigValue<Boolean> value) {
        this.settings.add(new BooleanSetting(category, key, value, this::markCustom));
    }

    private void number(Category category, String key, ConfigValue<Double> value, double min, double max, int decimals) {
        this.settings.add(new NumberSetting(category, key, value, min, max, decimals, this::markCustom));
    }

    private void integer(Category category, String key, ConfigValue<Integer> value, int min, int max) {
        this.settings.add(new IntegerSetting(category, key, value, min, max, this::markCustom));
    }

    private void preset(String key, Preset preset) {
        this.settings.add(new ActionSetting(Category.GENERAL, key, () -> this.applyPreset(preset), () -> this.activePreset == preset));
    }

    private void markCustom() {
        this.activePreset = Preset.CUSTOM;
        CartoonEffectsConfig.ACTIVE_PROFILE.set(this.activePreset.ordinal());
    }

    private void applyPreset(Preset preset) {
        RimLightConfig.reset();
        CartoonEffectsConfig.reset();
        if (preset == Preset.PERFORMANCE) {
            RimLightConfig.THICKNESS.set(2.0);
            RimLightConfig.STRENGTH.set(0.52);
            RimLightConfig.MAX_DISTANCE.set(64.0);
            CartoonEffectsConfig.POSTERIZATION_LEVELS.set(10);
            CartoonEffectsConfig.POSTERIZATION_STRENGTH.set(0.12);
            CartoonEffectsConfig.SKY_OUTLINES.set(false);
            CartoonEffectsConfig.LEAF_RIMS.set(false);
            CartoonEffectsConfig.CHROMATIC_EDGES.set(false);
            CartoonEffectsConfig.NIGHT_EMISSIVE.set(false);
            CartoonEffectsConfig.GLOBAL_SATURATION.set(1.08);
            CartoonEffectsConfig.GLOBAL_VIBRANCE.set(0.1);
            CartoonEffectsConfig.ORE_OUTLINE_THICKNESS.set(3.0);
            CartoonEffectsConfig.ORE_OUTLINE_DISTANCE.set(10);
            CartoonEffectsConfig.ORE_OUTLINE_MAX_COUNT.set(96);
            CartoonEffectsConfig.ORE_OUTLINE_PULSE.set(0.08);
        } else if (preset == Preset.VIVID) {
            RimLightConfig.THICKNESS.set(4.0);
            RimLightConfig.STRENGTH.set(0.82);
            RimLightConfig.MAX_DISTANCE.set(144.0);
            CartoonEffectsConfig.POSTERIZATION_LEVELS.set(7);
            CartoonEffectsConfig.POSTERIZATION_STRENGTH.set(0.24);
            CartoonEffectsConfig.GREEN_SATURATION.set(0.38);
            CartoonEffectsConfig.GLOBAL_SATURATION.set(1.34);
            CartoonEffectsConfig.GLOBAL_VIBRANCE.set(0.42);
            CartoonEffectsConfig.COLOR_TEMPERATURE.set(0.08);
            CartoonEffectsConfig.SCENE_BRIGHTNESS.set(1.03);
            CartoonEffectsConfig.SHADOW_LIFT.set(0.035);
            CartoonEffectsConfig.ORE_OUTLINE_THICKNESS.set(5.0);
            CartoonEffectsConfig.ORE_OUTLINE_OPACITY.set(1.0);
            CartoonEffectsConfig.ORE_OUTLINE_BRIGHTNESS.set(0.58);
            CartoonEffectsConfig.ORE_OUTLINE_DISTANCE.set(18);
            CartoonEffectsConfig.ORE_OUTLINE_MAX_COUNT.set(288);
            CartoonEffectsConfig.ORE_OUTLINE_PULSE.set(0.24);
        }
        this.activePreset = preset;
        CartoonEffectsConfig.ACTIVE_PROFILE.set(this.activePreset.ordinal());
        this.saveAll();
        this.rebuildWidgets();
    }

    private static void glass(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean hovered) {
        graphics.fillGradient(x, y, x + width, y + height, hovered ? -786017703 : -1256704965, hovered ? -1189396152 : -1525864401);
        graphics.outline(x, y, width, height, hovered ? -658505729 : 1956179434);
        graphics.horizontalLine(x + 1, x + width - 2, y + 1, hovered ? -1342177281 : 0x66FFFFFF);
    }

    private static String fit(Font font, Component text, int maximumWidth) {
        int end;
        String value = text.getString();
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String suffix = "\u2026";
        if (maximumWidth <= font.width(suffix)) {
            return suffix;
        }
        int suffixWidth = font.width(suffix);
        for (end = value.length(); end > 0 && font.width(value.substring(0, end)) + suffixWidth > maximumWidth; --end) {
        }
        return end <= 0 ? suffix : value.substring(0, end).stripTrailing() + suffix;
    }

    private static Tooltip settingTooltip(Component label) {
        return Tooltip.create((Component)Component.empty().append(label).append("\n").append(LumaLightConfigScreen.tr("hint.reset")));
    }

    private static Component tr(String key) {
        return ConfigTranslations.text(key);
    }

    private static enum Category {
        GENERAL("general"),
        OUTLINES("outlines"),
        CARTOON("cartoon_fx");

        private final String key;

        private Category(String key) {
            this.key = key;
        }

        Component label() {
            return LumaLightConfigScreen.tr("category." + this.key);
        }
    }

    private static enum Preset {
        CUSTOM,
        PERFORMANCE,
        BALANCED,
        VIVID;


        private static Preset fromId(int id) {
            Preset[] values = Preset.values();
            return values[Mth.clamp((int)id, (int)0, (int)(values.length - 1))];
        }
    }

    private static final class GlassButton
    extends AbstractButton {
        private final Runnable action;
        private final BooleanSupplier selected;
        private final boolean compact;

        GlassButton(int x, int y, int width, int height, Component label, Runnable action, BooleanSupplier selected, boolean compact) {
            super(x, y, width, height, label);
            this.action = action;
            this.selected = selected;
            this.compact = compact;
        }

        public void onPress(InputWithModifiers input) {
            this.action.run();
        }

        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean lit = this.selected.getAsBoolean() || this.isHoveredOrFocused();
            LumaLightConfigScreen.glass(graphics, this.getX(), this.getY(), this.width, this.height, lit);
            if (this.selected.getAsBoolean()) {
                graphics.fill(this.getX(), this.getY(), this.getX() + 3, this.getBottom(), -11147040);
            }
            int color = lit ? -720897 : -6305845;
            Font font = Minecraft.getInstance().font;
            if (this.compact) {
                graphics.text(font, LumaLightConfigScreen.fit(font, this.getMessage(), this.width - 18), this.getX() + 9, this.getY() + (this.height - 8) / 2, color, false);
            } else {
                graphics.text(font, LumaLightConfigScreen.fit(font, this.getMessage(), this.width - 22), this.getX() + 11, this.getY() + 7, -720897, false);
                Component actionText = this.selected.getAsBoolean() ? LumaLightConfigScreen.tr("profile_active") : LumaLightConfigScreen.tr("apply");
                graphics.text(font, LumaLightConfigScreen.fit(font, actionText, this.width - 22), this.getX() + 11, this.getY() + 21, -11147040, false);
            }
        }

        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private static abstract class Setting {
        final Category category;
        final String searchKey;

        Setting(Category category, String searchKey) {
            this.category = category;
            this.searchKey = searchKey;
        }

        Component label() {
            return LumaLightConfigScreen.tr("option." + this.searchKey);
        }

        abstract AbstractWidget widget(int var1, int var2, int var3, int var4);

        abstract void resetValue();
    }

    private static final class BooleanSetting
    extends Setting {
        private final ConfigValue<Boolean> value;
        private final Runnable changed;

        BooleanSetting(Category category, String key, ConfigValue<Boolean> value, Runnable changed) {
            super(category, key);
            this.value = value;
            this.changed = changed;
        }

        @Override
        AbstractWidget widget(int x, int y, int width, int height) {
            return new GlassToggle(x, y, width, height, this.label(), this.value, this.changed);
        }

        @Override
        void resetValue() {
            this.value.reset();
        }
    }

    private static final class NumberSetting
    extends Setting {
        private final ConfigValue<Double> value;
        private final double min;
        private final double max;
        private final int decimals;
        private final Runnable changed;

        NumberSetting(Category category, String key, ConfigValue<Double> value, double min, double max, int decimals, Runnable changed) {
            super(category, key);
            this.value = value;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.changed = changed;
        }

        @Override
        AbstractWidget widget(int x, int y, int width, int height) {
            return new GlassDoubleSlider(x, y, width, height, this.label(), this.value, this.min, this.max, this.decimals, this.changed);
        }

        @Override
        void resetValue() {
            this.value.reset();
        }
    }

    private static final class IntegerSetting
    extends Setting {
        private final ConfigValue<Integer> value;
        private final int min;
        private final int max;
        private final Runnable changed;

        IntegerSetting(Category category, String key, ConfigValue<Integer> value, int min, int max, Runnable changed) {
            super(category, key);
            this.value = value;
            this.min = min;
            this.max = max;
            this.changed = changed;
        }

        @Override
        AbstractWidget widget(int x, int y, int width, int height) {
            return new GlassIntegerSlider(x, y, width, height, this.label(), this.value, this.min, this.max, this.changed);
        }

        @Override
        void resetValue() {
            this.value.reset();
        }
    }

    private static final class ActionSetting
    extends Setting {
        private final Runnable action;
        private final BooleanSupplier selected;

        ActionSetting(Category category, String key, Runnable action, BooleanSupplier selected) {
            super(category, key);
            this.action = action;
            this.selected = selected;
        }

        @Override
        Component label() {
            return LumaLightConfigScreen.tr(this.searchKey);
        }

        @Override
        AbstractWidget widget(int x, int y, int width, int height) {
            return new GlassButton(x, y, width, height, this.label(), this.action, this.selected, false);
        }

        @Override
        void resetValue() {
        }
    }

    private static final class GlassToggle
    extends AbstractButton {
        private final ConfigValue<Boolean> config;
        private final Runnable changed;

        GlassToggle(int x, int y, int width, int height, Component label, ConfigValue<Boolean> config, Runnable changed) {
            super(x, y, width, height, label);
            this.config = config;
            this.changed = changed;
            this.setTooltip(LumaLightConfigScreen.settingTooltip(label));
        }

        public void onPress(InputWithModifiers input) {
            this.config.set(this.config.get() == false);
            this.changed.run();
        }

        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && this.isMouseOver(event.x(), event.y())) {
                this.config.reset();
                this.changed.run();
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            LumaLightConfigScreen.glass(graphics, this.getX(), this.getY(), this.width, this.height, this.isHoveredOrFocused());
            Font font = Minecraft.getInstance().font;
            graphics.text(font, LumaLightConfigScreen.fit(font, this.getMessage(), this.width - 20), this.getX() + 10, this.getY() + 6, -720897, false);
            graphics.text(font, this.config.get() != false ? LumaLightConfigScreen.tr("on") : LumaLightConfigScreen.tr("off"), this.getX() + 10, this.getY() + 21, this.config.get() != false ? -11147040 : -6305845, false);
            int tx = this.getRight() - 38;
            int ty = this.getY() + 21;
            graphics.fill(tx, ty, tx + 28, ty + 10, this.config.get() != false ? -1187193387 : 2017879658);
            graphics.outline(tx, ty, 28, 10, this.config.get() != false ? -720897 : -6305845);
            int knob = this.config.get() != false ? tx + 19 : tx + 2;
            graphics.fill(knob, ty + 2, knob + 7, ty + 8, -720897);
        }

        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private static abstract class GlassSlider
    extends AbstractSliderButton {
        final Component label;
        final Runnable changed;

        GlassSlider(int x, int y, int width, int height, Component label, double value, Runnable changed) {
            super(x, y, width, height, CommonComponents.EMPTY, Mth.clamp((double)value, (double)0.0, (double)1.0));
            this.label = label;
            this.changed = changed;
            this.setTooltip(LumaLightConfigScreen.settingTooltip(label));
            this.updateMessage();
        }

        abstract String displayValue();

        abstract void resetConfig();

        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            LumaLightConfigScreen.glass(graphics, this.getX(), this.getY(), this.width, this.height, this.isHoveredOrFocused());
            Font font = Minecraft.getInstance().font;
            String displayed = this.displayValue();
            int valueWidth = font.width(displayed);
            graphics.text(font, LumaLightConfigScreen.fit(font, this.label, this.width - valueWidth - 29), this.getX() + 10, this.getY() + 7, -720897, false);
            graphics.text(font, displayed, this.getRight() - 10 - font.width(displayed), this.getY() + 7, -11147040, false);
            int x0 = this.getX() + 10;
            int x1 = this.getRight() - 10;
            int y0 = this.getBottom() - 9;
            graphics.fill(x0, y0, x1, y0 + 3, 1717405064);
            int fillX = x0 + (int)Math.round((double)(x1 - x0) * this.value);
            graphics.fill(x0, y0, fillX, y0 + 3, -11147040);
            graphics.fill(fillX - 2, y0 - 3, fillX + 3, y0 + 6, -720897);
            graphics.outline(fillX - 2, y0 - 3, 5, 9, -10229021);
        }

        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && this.isMouseOver(event.x(), event.y())) {
                this.resetConfig();
                this.changed.run();
                this.updateMessage();
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
    }

    private static final class GlassIntegerSlider
    extends GlassSlider {
        private ConfigValue<Integer> config;
        private int min;
        private int max;

        GlassIntegerSlider(int x, int y, int width, int height, Component label, ConfigValue<Integer> config, int min, int max, Runnable changed) {
            super(x, y, width, height, label, (double)(config.get() - min) / (double)(max - min), changed);
            this.config = config;
            this.min = min;
            this.max = max;
            this.updateMessage();
        }

        protected void updateMessage() {
            if (this.config != null) {
                this.setMessage((Component)CommonComponents.optionNameValue((Component)this.label, (Component)Component.literal((String)this.displayValue())));
            }
        }

        protected void applyValue() {
            if (this.config != null) {
                this.config.set(Mth.clamp((int)((int)Math.round((double)this.min + this.value * (double)(this.max - this.min))), (int)this.min, (int)this.max));
                if (this.changed != null) {
                    this.changed.run();
                }
            }
        }

        @Override
        String displayValue() {
            return this.config == null ? "" : Integer.toString(this.config.get());
        }

        @Override
        void resetConfig() {
            this.config.reset();
            this.value = Mth.clamp((double)((double)(this.config.get() - this.min) / (double)(this.max - this.min)), (double)0.0, (double)1.0);
        }
    }

    private static final class GlassDoubleSlider
    extends GlassSlider {
        private ConfigValue<Double> config;
        private double min;
        private double max;
        private int decimals;

        GlassDoubleSlider(int x, int y, int width, int height, Component label, ConfigValue<Double> config, double min, double max, int decimals, Runnable changed) {
            super(x, y, width, height, label, (config.get() - min) / (max - min), changed);
            this.config = config;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.updateMessage();
        }

        protected void updateMessage() {
            if (this.config != null) {
                this.setMessage((Component)CommonComponents.optionNameValue((Component)this.label, (Component)Component.literal((String)this.displayValue())));
            }
        }

        protected void applyValue() {
            if (this.config != null) {
                this.config.set(this.min + this.value * (this.max - this.min));
                if (this.changed != null) {
                    this.changed.run();
                }
            }
        }

        @Override
        String displayValue() {
            return this.config == null ? "" : String.format(Locale.ROOT, "%." + this.decimals + "f", this.config.get());
        }

        @Override
        void resetConfig() {
            this.config.reset();
            this.value = Mth.clamp((double)((this.config.get() - this.min) / (this.max - this.min)), (double)0.0, (double)1.0);
        }
    }
}

