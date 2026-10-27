/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.logging.LogUtils
 *  net.fabricmc.api.ClientModInitializer
 *  org.slf4j.Logger
 */
package dev.lumalight;

import com.mojang.logging.LogUtils;
import dev.lumalight.client.CartoonEffectsConfig;
import dev.lumalight.client.ClientHooks;
import dev.lumalight.client.RimLightConfig;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public final class LumaLight
implements ClientModInitializer {
    public static final String MOD_ID = "lumalight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public void onInitializeClient() {
        RimLightConfig.load();
        CartoonEffectsConfig.load();
        ClientHooks.register();
        LOGGER.info("LumaLight initialized for Fabric 26.3");
    }
}

