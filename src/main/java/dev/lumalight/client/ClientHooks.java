/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
 *  net.fabricmc.fabric.api.resource.v1.ResourceLoader
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.PackType
 *  net.minecraft.server.packs.resources.PreparableReloadListener
 *  net.minecraft.server.packs.resources.ResourceManagerReloadListener
 */
package dev.lumalight.client;

import dev.lumalight.client.CartoonPostRenderer;
import dev.lumalight.client.ItemOutlineColor;
import dev.lumalight.client.LumaLightKeyBindings;
import dev.lumalight.client.OutlineVisibility;
import dev.lumalight.client.RimLightRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class ClientHooks {
    private static LevelRenderContext pendingPostContext;

    private ClientHooks() {
    }

    public static void register() {
        LumaLightKeyBindings.register();
        ResourceLoader.get((PackType)PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath((String)"lumalight", (String)"texture_color_cache"), (PreparableReloadListener)((ResourceManagerReloadListener)resourceManager -> ItemOutlineColor.clearCache()));
        ClientTickEvents.END_CLIENT_TICK.register(LumaLightKeyBindings::tick);
        LevelRenderEvents.END_MAIN.register(context -> {
            pendingPostContext = context;
            RimLightRenderer.render(context);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            pendingPostContext = null;
            CartoonPostRenderer.resetWorldState();
            OutlineVisibility.clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            pendingPostContext = null;
            RimLightRenderer.close();
            CartoonPostRenderer.close();
        });
    }

    public static void afterLevelRender() {
        LevelRenderContext context = pendingPostContext;
        pendingPostContext = null;
        if (context != null) {
            CartoonPostRenderer.render(context);
        }
    }
}
