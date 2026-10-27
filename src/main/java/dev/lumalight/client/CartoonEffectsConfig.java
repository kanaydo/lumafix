/*
 * Decompiled with CFR 0.152.
 */
package dev.lumalight.client;

import dev.lumalight.client.ConfigValue;
import dev.lumalight.client.SimpleConfigFile;

public final class CartoonEffectsConfig {
    private static final SimpleConfigFile FILE = new SimpleConfigFile("lumalight-cartoon.properties");
    public static final ConfigValue<Integer> ACTIVE_PROFILE = FILE.integer("ui.activeProfile", 0, 0, 3);
    public static final ConfigValue<Boolean> ENABLED = FILE.bool("cartoon.enabled", true);
    public static final ConfigValue<Boolean> POSTERIZATION = FILE.bool("cartoon.posterization.enabled", true);
    public static final ConfigValue<Integer> POSTERIZATION_LEVELS = FILE.integer("cartoon.posterization.levels", 8, 3, 24);
    public static final ConfigValue<Double> POSTERIZATION_STRENGTH = FILE.decimal("cartoon.posterization.strength", 0.18, 0.0, 1.0);
    // Moved from the atmosphere config: cartoon.fsh applies it unconditionally.
    public static final ConfigValue<Double> HIGHLIGHT_COMPRESSION = FILE.decimal("cartoon.color.highlightCompression", 0.58, 0.0, 1.0);
    public static final ConfigValue<Boolean> POSTERIZATION_DITHER = FILE.bool("cartoon.posterization.dither", true);
    public static final ConfigValue<Boolean> SKY_OUTLINES = FILE.bool("cartoon.skyCloudSunOutlines", true);
    public static final ConfigValue<Boolean> WATER_RIMS = FILE.bool("cartoon.waterShoreRims", true);
    public static final ConfigValue<Boolean> LEAF_RIMS = FILE.bool("cartoon.sunlitLeafRims", true);
    public static final ConfigValue<Double> GREEN_SATURATION = FILE.decimal("cartoon.greenSaturation", 0.28, 0.0, 0.6);
    public static final ConfigValue<Boolean> CHROMATIC_EDGES = FILE.bool("cartoon.glassPrismarineChromaticEdges", true);
    public static final ConfigValue<Boolean> NIGHT_EMISSIVE = FILE.bool("cartoon.nightEmissiveHighlights", true);
    public static final ConfigValue<Boolean> WEATHER_PALETTE = FILE.bool("cartoon.weatherPalette", true);
    public static final ConfigValue<Double> GLOBAL_SATURATION = FILE.decimal("cartoon.color.saturation", 1.18, 0.0, 2.0);
    public static final ConfigValue<Double> GLOBAL_VIBRANCE = FILE.decimal("cartoon.color.vibrance", 0.24, 0.0, 1.0);
    public static final ConfigValue<Double> COLOR_TEMPERATURE = FILE.decimal("cartoon.color.temperature", 0.0, -1.0, 1.0);
    public static final ConfigValue<Double> SCENE_BRIGHTNESS = FILE.decimal("cartoon.color.brightness", 1.0, 0.5, 1.5);
    public static final ConfigValue<Double> SHADOW_LIFT = FILE.decimal("cartoon.color.shadowLift", 0.025, 0.0, 0.3);
    public static final ConfigValue<Double> COMFORT_SHADOW_CHROMA = FILE.decimal("cartoon.comfort.shadowChroma", 0.46, 0.0, 1.0);
    public static final ConfigValue<Double> COMFORT_WHITE_PROTECTION = FILE.decimal("cartoon.comfort.whiteProtection", 0.62, 0.0, 1.0);
    public static final ConfigValue<Double> COMFORT_BLACK_PROTECTION = FILE.decimal("cartoon.comfort.blackProtection", 0.22, 0.0, 0.6);
    public static final ConfigValue<Double> COMFORT_LOW_CONTRAST = FILE.decimal("cartoon.comfort.lowContrast", 0.18, 0.0, 0.7);
    public static final ConfigValue<Boolean> COMFORT_MOTION_REDUCTION = FILE.bool("cartoon.comfort.motionReduction", true);
    public static final ConfigValue<Boolean> RARITY_OUTLINES = FILE.bool("items.rarityOutlines", true);
    public static final ConfigValue<Boolean> DAMAGE_FLASH = FILE.bool("entities.damageOutlineFlash", true);

    public static void load() {
        FILE.load();
    }

    public static void save() {
        FILE.save();
    }

    public static void reset() {
        FILE.reset();
    }

    private CartoonEffectsConfig() {
    }
}

