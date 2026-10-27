/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.entity.state.ItemEntityRenderState
 *  net.minecraft.client.renderer.texture.TextureAtlasSprite
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.resources.Resource
 *  net.minecraft.util.RandomSource
 */
package dev.lumalight.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

public final class ItemOutlineColor {
    private static final int FALLBACK = 16765802;
    private static final Map<Identifier, Integer> CACHE = new HashMap<Identifier, Integer>();
    // Per dropped-item colour, so the item model is not walked every frame. Values hold no
    // entity or level reference, so the weak keys are collected once the entity is gone.
    private static final Map<ItemEntity, EntityColor> ENTITY_CACHE = new WeakHashMap<ItemEntity, EntityColor>();

    private ItemOutlineColor() {
    }

    public static int find(ItemEntity entity, ItemEntityRenderState state) {
        Item item = entity.getItem().getItem();
        EntityColor cached = ENTITY_CACHE.get(entity);
        if (cached != null && cached.item == item) {
            return cached.color;
        }
        int color = ItemOutlineColor.find(state, entity.getId());
        ENTITY_CACHE.put(entity, new EntityColor(item, color));
        return color;
    }

    private static int find(ItemEntityRenderState state, int seed) {
        try {
            TextureAtlasSprite sprite = state.item.pickParticleMaterial(RandomSource.create((long)seed)).sprite();
            return CACHE.computeIfAbsent(sprite.contents().name(), ItemOutlineColor::scanSprite);
        }
        catch (RuntimeException ignored) {
            return 16765802;
        }
    }

    public static void clearCache() {
        CACHE.clear();
        ENTITY_CACHE.clear();
    }

    private record EntityColor(Item item, int color) {
    }

    /*
     * Enabled aggressive exception aggregation
     */
    private static int scanSprite(Identifier spriteId) {
        Identifier textureId = Identifier.fromNamespaceAndPath((String)spriteId.getNamespace(), (String)("textures/" + spriteId.getPath() + ".png"));
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(textureId).orElse(null);
        if (resource == null) {
            return 16765802;
        }
        try (InputStream stream = resource.open();){
            int n;
            block21: {
                double weightSum;
                double blueSum;
                double greenSum;
                double redSum;
                NativeImage image;
                block19: {
                    int y;
                    block20: {
                        image = NativeImage.read((InputStream)stream);
                        try {
                            redSum = 0.0;
                            greenSum = 0.0;
                            blueSum = 0.0;
                            weightSum = 0.0;
                            for (y = 0; y < image.getHeight(); ++y) {
                                for (int x = 0; x < image.getWidth(); ++x) {
                                    int argb = image.getPixel(x, y);
                                    int alpha = argb >>> 24 & 0xFF;
                                    if (alpha < 24) continue;
                                    int red = argb >> 16 & 0xFF;
                                    int green = argb >> 8 & 0xFF;
                                    int blue = argb & 0xFF;
                                    int maximum = Math.max(red, Math.max(green, blue));
                                    int minimum = Math.min(red, Math.min(green, blue));
                                    double value = (double)maximum / 255.0;
                                    double saturation = maximum == 0 ? 0.0 : (double)(maximum - minimum) / (double)maximum;
                                    double weight = (double)alpha / 255.0 * (0.35 + saturation * 0.9) * (0.25 + value * 0.75);
                                    redSum += (double)red * weight;
                                    greenSum += (double)green * weight;
                                    blueSum += (double)blue * weight;
                                    weightSum += weight;
                                }
                            }
                            if (!(weightSum < 0.001)) break block19;
                            y = 16765802;
                            if (image == null) break block20;
                        }
                        catch (Throwable throwable) {
                            if (image != null) {
                                try {
                                    image.close();
                                }
                                catch (Throwable throwable2) {
                                    throwable.addSuppressed(throwable2);
                                }
                            }
                            throw throwable;
                        }
                        image.close();
                    }
                    return y;
                }
                int red = ItemOutlineColor.brighten((int)Math.round(redSum / weightSum));
                int green = ItemOutlineColor.brighten((int)Math.round(greenSum / weightSum));
                int blue = ItemOutlineColor.brighten((int)Math.round(blueSum / weightSum));
                n = red << 16 | green << 8 | blue;
                if (image == null) break block21;
                image.close();
            }
            return n;
        }
        catch (IOException | RuntimeException ignored) {
            return 16765802;
        }
    }

    private static int brighten(int channel) {
        return Math.min(255, 28 + channel * 227 / 255);
    }
}

