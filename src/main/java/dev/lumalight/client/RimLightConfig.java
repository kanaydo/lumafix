/*
 * Decompiled with CFR 0.152.
 */
package dev.lumalight.client;

import dev.lumalight.client.ConfigValue;
import dev.lumalight.client.SimpleConfigFile;

public final class RimLightConfig {
    private static final SimpleConfigFile FILE = new SimpleConfigFile("lumalight-client.properties");
    public static final ConfigValue<Boolean> ENABLED = FILE.bool("rim_light.enabled", true);
    public static final ConfigValue<Double> THICKNESS = FILE.decimal("rim_light.thickness", 3.0, 1.0, 12.0);
    public static final ConfigValue<Double> STRENGTH = FILE.decimal("rim_light.strength", 0.65, 0.0, 5.0);
    public static final ConfigValue<Double> DEPTH_THRESHOLD = FILE.decimal("rim_light.depthThreshold", 0.1, 0.01, 2.0);
    public static final ConfigValue<Double> MAX_DISTANCE = FILE.decimal("rim_light.maxDistance", 96.0, 1.0, 1024.0);
    public static final ConfigValue<Double> COLOR_RED = FILE.decimal("rim_light.color.red", 0.65, 0.0, 2.0);
    public static final ConfigValue<Double> COLOR_GREEN = FILE.decimal("rim_light.color.green", 0.76, 0.0, 2.0);
    public static final ConfigValue<Double> COLOR_BLUE = FILE.decimal("rim_light.color.blue", 1.0, 0.0, 2.0);
    public static final ConfigValue<Double> SCENE_COLOR_INFLUENCE = FILE.decimal("rim_light.color.sceneInfluence", 0.35, 0.0, 1.0);
    // Off by default: grass, plants and leaves get no outline.
    public static final ConfigValue<Boolean> OUTLINE_FOLIAGE = FILE.bool("rim_light.outlineFoliage", false);
    public static final ConfigValue<Boolean> BLOCK_SHADING_ENABLED = FILE.bool("shading.blockShadingEnabled", false);

    public static void load() {
        FILE.load();
    }

    public static void save() {
        FILE.save();
    }

    public static void reset() {
        FILE.reset();
    }

    private RimLightConfig() {
    }
}
