/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.color.block.BlockColors
 *  net.minecraft.client.color.block.BlockTintSource
 *  net.minecraft.client.renderer.block.BlockAndTintGetter
 *  net.minecraft.client.renderer.block.dispatch.BlockStateModel
 *  net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
 *  net.minecraft.client.renderer.texture.TextureAtlasSprite
 *  net.minecraft.client.resources.model.geometry.BakedQuad
 *  net.minecraft.client.resources.model.geometry.BakedQuad$MaterialInfo
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.resources.Resource
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.level.block.state.BlockState
 */
package dev.lumalight.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class BlockTextureLightColor {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<SpriteTintKey, Candidate> SPRITE_COLOR_CACHE = new HashMap<SpriteTintKey, Candidate>();

    private BlockTextureLightColor() {
    }

    static int find(BlockState state, BlockAndTintGetter level, BlockPos pos, int fallbackRgb) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        BlockColors blockColors = minecraft.getBlockColors();
        RandomSource random = RandomSource.create((long)state.getSeed(pos));
        ArrayList<BlockStateModelPart> parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(random, parts);
        LinkedHashMap<SpriteTintKey, TextureAtlasSprite> sprites = new LinkedHashMap<SpriteTintKey, TextureAtlasSprite>();
        for (BlockStateModelPart part : parts) {
            BlockTextureLightColor.collect(part, state, level, pos, null, blockColors, sprites);
            for (Direction direction : DIRECTIONS) {
                BlockTextureLightColor.collect(part, state, level, pos, direction, blockColors, sprites);
            }
        }
        if (sprites.isEmpty()) {
            TextureAtlasSprite particle = model.particleMaterial().sprite();
            sprites.put(new SpriteTintKey(particle.contents().name(), 0xFFFFFF), particle);
        }
        Candidate best = null;
        for (Map.Entry<SpriteTintKey, TextureAtlasSprite> entry : sprites.entrySet()) {
            Candidate candidate = SPRITE_COLOR_CACHE.computeIfAbsent(entry.getKey(), BlockTextureLightColor::scanSprite);
            if (candidate == null || best != null && !(candidate.distanceSquared < best.distanceSquared)) continue;
            best = candidate;
        }
        return best == null ? fallbackRgb : best.rgb;
    }

    static void clearCache() {
        SPRITE_COLOR_CACHE.clear();
    }

    private static void collect(BlockStateModelPart part, BlockState state, BlockAndTintGetter level, BlockPos pos, Direction direction, BlockColors colors, Map<SpriteTintKey, TextureAtlasSprite> output) {
        for (BakedQuad quad : part.getQuads(direction)) {
            BlockTintSource source;
            BakedQuad.MaterialInfo material = quad.materialInfo();
            int tint = 0xFFFFFF;
            if (material.isTinted() && (source = colors.getTintSource(state, material.tintIndex())) != null) {
                try {
                    tint = source.colorInWorld(state, level, pos) & 0xFFFFFF;
                }
                catch (RuntimeException ignored) {
                    tint = 0xFFFFFF;
                }
            }
            TextureAtlasSprite sprite = material.sprite();
            output.putIfAbsent(new SpriteTintKey(sprite.contents().name(), tint), sprite);
        }
    }

    // Picks the most saturated, brightest texel of the (tinted) sprite as the block's accent colour.
    private static Candidate scanSprite(SpriteTintKey key) {
        Identifier spriteId = key.spriteId;
        Identifier textureId = Identifier.fromNamespaceAndPath(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(textureId).orElse(null);
        if (resource == null) {
            return null;
        }
        int tintRed = key.tintRgb >> 16 & 0xFF;
        int tintGreen = key.tintRgb >> 8 & 0xFF;
        int tintBlue = key.tintRgb & 0xFF;
        try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
            Candidate best = null;
            for (int y = 0; y < image.getHeight(); ++y) {
                for (int x = 0; x < image.getWidth(); ++x) {
                    int argb = image.getPixel(x, y);
                    if ((argb >>> 24 & 0xFF) < 16) continue;
                    int red = (argb >> 16 & 0xFF) * tintRed / 255;
                    int green = (argb >> 8 & 0xFF) * tintGreen / 255;
                    int blue = (argb & 0xFF) * tintBlue / 255;
                    int maximum = Math.max(red, Math.max(green, blue));
                    int minimum = Math.min(red, Math.min(green, blue));
                    float value = (float)maximum / 255.0f;
                    float saturation = maximum == 0 ? 0.0f : (float)(maximum - minimum) / (float)maximum;
                    float distance = (1.0f - saturation) * (1.0f - saturation) + (1.0f - value) * (1.0f - value);
                    if (best != null && !(distance < best.distanceSquared)) continue;
                    best = new Candidate(red << 16 | green << 8 | blue, distance);
                }
            }
            return best;
        }
        catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private record SpriteTintKey(Identifier spriteId, int tintRgb) {
    }

    private record Candidate(int rgb, float distanceSquared) {
    }
}

