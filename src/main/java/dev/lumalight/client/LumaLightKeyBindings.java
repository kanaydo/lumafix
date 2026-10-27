/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.resources.Identifier
 */
package dev.lumalight.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumalight.client.LumaLightConfigScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

final class LumaLightKeyBindings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register((Identifier)Identifier.fromNamespaceAndPath((String)"lumalight", (String)"settings"));
    private static final KeyMapping OPEN_CONFIG = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.lumalight.open_config", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));

    private LumaLightKeyBindings() {
    }

    static void register() {
    }

    static void tick(Minecraft minecraft) {
        while (OPEN_CONFIG.consumeClick()) {
            if (minecraft.gui.screen() instanceof LumaLightConfigScreen) continue;
            minecraft.gui.setScreen((Screen)new LumaLightConfigScreen(minecraft.gui.screen()));
        }
    }
}

