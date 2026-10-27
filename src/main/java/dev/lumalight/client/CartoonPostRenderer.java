/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.mojang.blaze3d.buffers.Std140Builder
 *  com.mojang.blaze3d.pipeline.RenderTarget
 *  com.mojang.blaze3d.pipeline.TextureTarget
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.renderpearl.api.buffers.GpuBufferSlice$MappedView
 *  com.mojang.renderpearl.api.commands.CommandEncoder
 *  com.mojang.renderpearl.api.commands.RenderPass
 *  com.mojang.renderpearl.api.pipeline.BindGroupLayout
 *  com.mojang.renderpearl.api.pipeline.ColorTargetState
 *  com.mojang.renderpearl.api.pipeline.PrimitiveTopology
 *  com.mojang.renderpearl.api.pipeline.RenderPipeline
 *  com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet
 *  com.mojang.renderpearl.api.pipeline.UniformType
 *  com.mojang.renderpearl.api.textures.FilterMode
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.renderer.MappableRingBuffer
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.resources.Identifier
 *  net.minecraft.util.Mth
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.material.FogType
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Vector4f
 */
package dev.lumalight.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.lumalight.client.CartoonEffectsConfig;
import dev.lumalight.client.ConfigValue;
import dev.lumalight.client.ShaderPackCompatibility;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/*
 * Only the cartoon colour pass of cartoon.fsh is driven here. The shader also
 * contains the atmosphere effects (celestial light, exposure, god rays, aurora,
 * caustics, ...); their uniforms are written as zero so those branches stay off.
 * The CartoonData layout must stay byte-for-byte identical to the shader.
 */
public final class CartoonPostRenderer {
    private static final int UBO_SIZE = 480;
    private static final RenderPipeline PIPELINE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.GLOBALS_SNIPPET}).withLocation(Identifier.fromNamespaceAndPath((String)"lumalight", (String)"pipeline/cartoon_post")).withVertexShader("core/screenquad").withFragmentShader(Identifier.fromNamespaceAndPath((String)"lumalight", (String)"post/cartoon")).withBindGroupLayout(BindGroupLayout.builder().withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("CartoonData", UniformType.UNIFORM_BUFFER).build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withColorTargetState(ColorTargetState.DEFAULT).build());
    // Fallback colours the original used whenever no atmosphere profile was active.
    private static final Vec3 SKY_COLOR = new Vec3(0.55, 0.72, 1.0);
    private static final float SUN_RED = 1.0f;
    private static final float SUN_GREEN = 0.78f;
    private static final float SUN_BLUE = 0.52f;
    private static TextureTarget sceneCopy;
    private static MappableRingBuffer uniforms;
    private static int width;
    private static int height;
    private static float smoothRain;
    private static float smoothThunder;
    private static float cameraMotion;
    private static Vec3 lastLook;
    private static long lastFrameNanos;

    private CartoonPostRenderer() {
    }

    public static void render(LevelRenderContext context) {
        RenderTarget main;
        Minecraft minecraft = Minecraft.getInstance();
        boolean cartoonEnabled = CartoonEffectsConfig.ENABLED.get();
        if (cartoonEnabled && minecraft.level != null && minecraft.player != null && !ShaderPackCompatibility.isShaderPackActive() && (main = minecraft.gameRenderer.mainRenderTarget()).getColorTexture() != null && main.getDepthTexture() != null && main.width > 0 && main.height > 0) {
            CartoonPostRenderer.ensure(main);
            long now = System.nanoTime();
            float seconds = lastFrameNanos == 0L ? 0.016666668f : Math.min((float)(now - lastFrameNanos) * 1.0E-9f, 0.25f);
            lastFrameNanos = now;
            float rainNow = minecraft.level.getRainLevel(1.0f);
            float thunderNow = minecraft.level.getThunderLevel(1.0f);
            smoothRain = CartoonPostRenderer.approach(smoothRain, rainNow, seconds, rainNow > smoothRain ? 2.8f : 0.42f);
            smoothThunder = CartoonPostRenderer.approach(smoothThunder, thunderNow, seconds, thunderNow > smoothThunder ? 3.8f : 0.7f);
            boolean underwater = minecraft.gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER;
            Vec3 cameraPosition = minecraft.gameRenderer.mainCamera().position();
            Vec3 look = minecraft.player.getLookAngle();
            if (lastLook.lengthSqr() > 0.001) {
                cameraMotion = CartoonPostRenderer.approach(cameraMotion, Mth.clamp((float)((float)(1.0 - look.dot(lastLook)) * 12.0f), (float)0.0f, (float)1.0f), seconds, 9.0f);
            }
            lastLook = look;
            float biomeTemperature = ((Biome)minecraft.level.getBiome(minecraft.player.blockPosition()).value()).getBaseTemperature();
            float dimension = minecraft.level.dimension().equals(Level.NETHER) ? -1.0f : (minecraft.level.dimension().equals(Level.END) ? 1.0f : 0.0f);
            Matrix4f projection = new Matrix4f((Matrix4fc)context.levelState().cameraRenderState.projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f((Matrix4fc)projection).invert();
            Matrix4f viewRotation = new Matrix4f((Matrix4fc)context.levelState().cameraRenderState.viewRotationMatrix);
            Matrix4f inverseViewRotation = new Matrix4f((Matrix4fc)viewRotation).invert();
            Vector4f sunScreen = CartoonPostRenderer.projectSun(context, projection, viewRotation);
            try (GpuBufferSlice.MappedView mapped = uniforms.currentBuffer().map(false, true);){
                Std140Builder.intoBuffer((ByteBuffer)mapped.data())
                    // ScreenTime: width, height, time, daylight
                    .putVec4((float)main.width, (float)main.height, (float)minecraft.level.getGameTime(), CartoonPostRenderer.daylight(minecraft.level))
                    // WeatherPoster: rain, thunder, levels, strength
                    .putVec4(rainNow, thunderNow, (float)CartoonEffectsConfig.POSTERIZATION_LEVELS.get().intValue(), CartoonEffectsConfig.POSTERIZATION_STRENGTH.get().floatValue())
                    // Flags0: poster, green saturation, sky outline, water rim
                    .putVec4(CartoonPostRenderer.flag(CartoonEffectsConfig.POSTERIZATION), CartoonEffectsConfig.GREEN_SATURATION.get().floatValue(), CartoonPostRenderer.flag(CartoonEffectsConfig.SKY_OUTLINES), CartoonPostRenderer.flag(CartoonEffectsConfig.WATER_RIMS))
                    // Flags1: leaf rim, chroma, night emissive, weather palette
                    .putVec4(CartoonPostRenderer.flag(CartoonEffectsConfig.LEAF_RIMS), CartoonPostRenderer.flag(CartoonEffectsConfig.CHROMATIC_EDGES), CartoonPostRenderer.flag(CartoonEffectsConfig.NIGHT_EMISSIVE), CartoonPostRenderer.flag(CartoonEffectsConfig.WEATHER_PALETTE))
                    // SunLight: rgb is still used for water-rim reflections; strength 0 keeps celestial lighting off
                    .putVec4(SUN_RED, SUN_GREEN, SUN_BLUE, 0.0f)
                    // MoonLight
                    .putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                    // Celestial: enabled, sky exposure, has skylight, cartoon master
                    .putVec4(0.0f, 0.0f, minecraft.level.dimensionType().hasSkyLight() ? 1.0f : 0.0f, 1.0f)
                    .putMat4f((Matrix4fc)inverseProjection)
                    .putMat4f((Matrix4fc)inverseViewRotation)
                    // ExposureData: adaptive exposure, strength, wetness, camera in water
                    .putVec4(1.0f, 0.0f, 0.0f, underwater ? 1.0f : 0.0f)
                    // Effects: god rays, water, weather, environment reaction
                    .putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                    // SunData: screen uv, visibility
                    .putVec4(sunScreen.x, sunScreen.y, sunScreen.z, 0.0f)
                    // WorldData: game time, smoothed rain, smoothed thunder, after-rain
                    .putVec4((float)minecraft.level.getGameTime(), smoothRain, smoothThunder, 0.0f)
                    // SkyColor: rgb, lightning flash
                    .putVec4((float)SKY_COLOR.x, (float)SKY_COLOR.y, (float)SKY_COLOR.z, 0.0f)
                    // FogColor: rgb, zero-to-one depth flag
                    .putVec4((float)SKY_COLOR.x, (float)SKY_COLOR.y, (float)SKY_COLOR.z, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0f : 0.0f)
                    // ColorGrade: saturation, vibrance, temperature, brightness
                    .putVec4(CartoonEffectsConfig.GLOBAL_SATURATION.get().floatValue(), CartoonEffectsConfig.GLOBAL_VIBRANCE.get().floatValue(), CartoonEffectsConfig.COLOR_TEMPERATURE.get().floatValue(), CartoonEffectsConfig.SCENE_BRIGHTNESS.get().floatValue())
                    // ToneGrade: shadow lift, highlight compression, underwater caustics, poster dither
                    .putVec4(CartoonEffectsConfig.SHADOW_LIFT.get().floatValue(), CartoonEffectsConfig.HIGHLIGHT_COMPRESSION.get().floatValue(), 0.0f, CartoonEffectsConfig.POSTERIZATION_DITHER.get() != false ? 1.0f : 0.0f)
                    // VisualSky: aurora, shooting stars, End nebula, starfield
                    .putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                    // VisualSurface: frost, desert glow, Nether embers, moon shadow tint
                    .putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                    // Environment: dimension, biome temperature, camera motion
                    .putVec4(dimension, biomeTemperature, cameraMotion, 0.0f)
                    // Comfort: shadow chroma, white protection, black protection, low contrast
                    .putVec4(CartoonEffectsConfig.COMFORT_SHADOW_CHROMA.get().floatValue(), CartoonEffectsConfig.COMFORT_WHITE_PROTECTION.get().floatValue(), CartoonEffectsConfig.COMFORT_BLACK_PROTECTION.get().floatValue(), CartoonEffectsConfig.COMFORT_LOW_CONTRAST.get().floatValue())
                    // ComfortMotion: flash limit, reduce decorations, wet sheen, underwater depth tint
                    .putVec4(0.0f, CartoonEffectsConfig.COMFORT_MOTION_REDUCTION.get() != false ? 1.0f : 0.0f, 0.0f, 0.0f)
                    // CameraPosition: xyz, vanilla cloud layer bottom Y
                    .putVec4((float)cameraPosition.x, (float)cameraPosition.y, (float)cameraPosition.z, VanillaSkyMask.cloudBottom(context));
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getColorTexture(), sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
            try (RenderPass pass = encoder.createRenderPass(() -> "LumaLight cartoon post", main.getColorTextureView(), Optional.empty());){
                pass.setPipeline(RenderSystem.getCompiledPipeline((RenderPipeline)PIPELINE));
                RenderSystem.bindDefaultUniforms((RenderPass)pass);
                pass.setUniform("CartoonData", uniforms.currentBuffer());
                pass.setUniform("SceneSampler", sceneCopy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setUniform("DepthSampler", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
            uniforms.rotate();
        }
    }

    private static float flag(ConfigValue<Boolean> value) {
        return value.get() != false ? 1.0f : 0.0f;
    }

    private static float approach(float current, float target, float seconds, float speed) {
        float amount = 1.0f - (float)Math.exp(-Math.max(seconds, 0.0f) * Math.max(speed, 0.01f));
        return Mth.lerp((float)amount, (float)current, (float)target);
    }

    // Same curve as AtmosphereEngine.daylight in the original mod.
    private static float daylight(ClientLevel level) {
        long time = Math.floorMod(level.getDefaultClockTime(), 24000L);
        double angle = ((double)time - 6000.0) * 2.617993877991494E-4;
        return Mth.clamp((float)((float)(0.5 + 0.5 * Math.cos(angle))), (float)0.0f, (float)1.0f);
    }

    private static Vector4f projectSun(LevelRenderContext context, Matrix4f projection, Matrix4f viewRotation) {
        float angle = context.levelState().skyRenderState.sunAngle * ((float)Math.PI * 2);
        Vector4f direction = new Vector4f(-((float)Math.sin(angle)), (float)Math.cos(angle), 0.0f, 0.0f);
        float aboveHorizon = Mth.clamp((float)(direction.y * 4.0f), (float)0.0f, (float)1.0f);
        float weather = Math.max(1.0f - smoothRain * 0.72f - smoothThunder * 0.2f, 0.0f);
        float worldVisibility = aboveHorizon * weather;
        direction.mul((Matrix4fc)viewRotation);
        Vector4f clip = new Vector4f(direction.x * 512.0f, direction.y * 512.0f, direction.z * 512.0f, 1.0f).mul((Matrix4fc)projection);
        if (clip.w <= 0.001f) {
            return new Vector4f(0.5f, 0.5f, 0.0f, worldVisibility);
        }
        float u = clip.x / clip.w * 0.5f + 0.5f;
        float v = clip.y / clip.w * 0.5f + 0.5f;
        float onScreen = u > -0.25f && u < 1.25f && v > -0.25f && v < 1.25f ? 1.0f : 0.0f;
        return new Vector4f(u, v, worldVisibility * onScreen, worldVisibility);
    }

    private static void ensure(RenderTarget main) {
        if (sceneCopy == null || width != main.width || height != main.height) {
            CartoonPostRenderer.close();
            width = main.width;
            height = main.height;
            sceneCopy = new TextureTarget("LumaLight cartoon scene", width, height, main.getColorTexture().getFormat(), null);
            uniforms = new MappableRingBuffer(() -> "LumaLight cartoon UBO", 130, 480);
        }
    }

    public static void resetWorldState() {
        smoothRain = 0.0f;
        smoothThunder = 0.0f;
        cameraMotion = 0.0f;
        lastLook = Vec3.ZERO;
        lastFrameNanos = 0L;
    }

    public static void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
        }
        uniforms = null;
        sceneCopy = null;
        height = -1;
        width = -1;
        CartoonPostRenderer.resetWorldState();
    }

    static {
        width = -1;
        height = -1;
        lastLook = Vec3.ZERO;
    }
}
