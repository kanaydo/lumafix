/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.buffers.Std140Builder
 *  com.mojang.blaze3d.pipeline.RenderTarget
 *  com.mojang.blaze3d.pipeline.TextureTarget
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.renderpearl.api.GpuFormat
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
 *  com.mojang.renderpearl.api.textures.GpuSampler
 *  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.MappableRingBuffer
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.resources.Identifier
 */
package dev.lumalight.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.lumalight.client.RimLightConfig;
import dev.lumalight.client.ShaderPackCompatibility;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class RimLightRenderer {
    private static final float NEAR_PLANE = 0.05f;
    private static final float FIRST_PERSON_FAR_PLANE = 100.0f;
    private static final int UBO_SIZE = 64 + VanillaSkyMask.STD140_SIZE;
    private static final int TERRAIN_MODE = 0;
    private static final int ENTITY_MODE = 1;
    private static final int FIRST_PERSON_MODE = 2;
    private static final RenderPipeline PIPELINE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.GLOBALS_SNIPPET}).withLocation(Identifier.fromNamespaceAndPath((String)"lumalight", (String)"pipeline/rim_light")).withVertexShader("core/screenquad").withFragmentShader(Identifier.fromNamespaceAndPath((String)"lumalight", (String)"post/rim_light")).withBindGroupLayout(BindGroupLayout.builder().withUniform("DiffuseSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("MaskDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("BaseDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("RimData", UniformType.UNIFORM_BUFFER).build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withColorTargetState(ColorTargetState.DEFAULT).build());
    private static TextureTarget sceneCopy;
    private static TextureTarget terrainDepth;
    private static MappableRingBuffer terrainUniforms;
    private static MappableRingBuffer firstPersonUniforms;
    private static int width;
    private static int height;

    private RimLightRenderer() {
    }

    public static void render(LevelRenderContext context) {
        RenderTarget main;
        Minecraft minecraft = Minecraft.getInstance();
        if (RimLightRenderer.canRender(minecraft) && RimLightRenderer.valid(main = minecraft.gameRenderer.mainRenderTarget())) {
            RimLightRenderer.ensureTargets(main);
            terrainDepth.copyDepthFrom(main);
            float farPlane = context.levelState().cameraRenderState.depthFar;
            RimLightRenderer.fillUniforms(terrainUniforms, main, farPlane, 0, context);
            RimLightRenderer.renderPass(main, terrainUniforms, terrainDepth, terrainDepth, "world");
            terrainUniforms.rotate();
        }
    }

    public static void renderFirstPerson() {
        RenderTarget main;
        Minecraft minecraft = Minecraft.getInstance();
        if (RimLightRenderer.canRender(minecraft) && RimLightRenderer.valid(main = minecraft.gameRenderer.mainRenderTarget())) {
            RimLightRenderer.ensureTargets(main);
            RimLightRenderer.fillUniforms(firstPersonUniforms, main, 100.0f, 2, null);
            RimLightRenderer.renderPass(main, firstPersonUniforms, terrainDepth, terrainDepth, "first person hand and item");
            firstPersonUniforms.rotate();
        }
    }

    private static void fillUniforms(MappableRingBuffer ring, RenderTarget main, float farPlane, int mode, LevelRenderContext context) {
        try (GpuBufferSlice.MappedView mapped = ring.currentBuffer().map(false, true);){
            Std140Builder rimData = Std140Builder.intoBuffer((ByteBuffer)mapped.data()).putVec4((float)main.width, (float)main.height, RimLightConfig.THICKNESS.get().floatValue(), RimLightConfig.STRENGTH.get().floatValue()).putVec4(RimLightConfig.DEPTH_THRESHOLD.get().floatValue(), RimLightConfig.MAX_DISTANCE.get().floatValue(), 0.05f, farPlane).putVec4(RimLightConfig.COLOR_RED.get().floatValue(), RimLightConfig.COLOR_GREEN.get().floatValue(), RimLightConfig.COLOR_BLUE.get().floatValue(), RimLightConfig.SCENE_COLOR_INFLUENCE.get().floatValue()).putVec4((float)mode, RimLightConfig.OUTLINE_FOLIAGE.get() != false ? 0.0f : 1.0f, 0.0f, 0.0f);
            VanillaSkyMask.put(rimData, context);
        }
    }

    private static void renderPass(RenderTarget main, MappableRingBuffer ring, TextureTarget maskDepth, TextureTarget baseDepth, String label) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        try (RenderPass pass = encoder.createRenderPass(() -> "LumaLight rim: " + label, main.getColorTextureView(), Optional.empty());){
            pass.setPipeline(RenderSystem.getCompiledPipeline((RenderPipeline)PIPELINE));
            RenderSystem.bindDefaultUniforms((RenderPass)pass);
            pass.setUniform("RimData", ring.currentBuffer());
            pass.setUniform("DiffuseSampler", sceneCopy.getColorTextureView(), RimLightRenderer.sampler());
            pass.setUniform("DepthSampler", main.getDepthTextureView(), RimLightRenderer.sampler());
            pass.setUniform("MaskDepthSampler", maskDepth.getDepthTextureView(), RimLightRenderer.sampler());
            pass.setUniform("BaseDepthSampler", baseDepth.getDepthTextureView(), RimLightRenderer.sampler());
            pass.draw(3, 1, 0, 0);
        }
    }

    private static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private static boolean canRender(Minecraft minecraft) {
        return RimLightConfig.ENABLED.get() != false && minecraft.level != null && !ShaderPackCompatibility.isShaderPackActive();
    }

    private static boolean valid(RenderTarget main) {
        return main.getColorTexture() != null && main.getDepthTexture() != null && main.width > 0 && main.height > 0;
    }

    private static void ensureTargets(RenderTarget main) {
        if (sceneCopy == null || width != main.width || height != main.height) {
            RimLightRenderer.close();
            width = main.width;
            height = main.height;
            GpuFormat format = main.getColorTexture().getFormat();
            sceneCopy = new TextureTarget("LumaLight rim scene", width, height, format, null);
            // Depth-only snapshot: the first-person pass compares against the world depth before the hand.
            terrainDepth = new TextureTarget("LumaLight terrain depth", width, height, null, main.getDepthTexture().getFormat());
            terrainUniforms = RimLightRenderer.ring("terrain");
            firstPersonUniforms = RimLightRenderer.ring("first person");
        }
    }

    private static MappableRingBuffer ring(String name) {
        return new MappableRingBuffer(() -> "LumaLight " + name + " rim UBO", 130, UBO_SIZE);
    }

    public static void close() {
        RimLightRenderer.close(terrainUniforms);
        RimLightRenderer.close(firstPersonUniforms);
        RimLightRenderer.destroy(sceneCopy);
        RimLightRenderer.destroy(terrainDepth);
        terrainUniforms = null;
        firstPersonUniforms = null;
        sceneCopy = null;
        terrainDepth = null;
        height = -1;
        width = -1;
    }

    private static void close(MappableRingBuffer ring) {
        if (ring != null) {
            ring.close();
        }
    }

    private static void destroy(TextureTarget target) {
        if (target != null) {
            target.destroyBuffers();
        }
    }

    static {
        width = -1;
        height = -1;
    }
}

