/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.GameRenderer
 *  net.minecraft.client.renderer.state.OptionsRenderState
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  net.minecraft.client.renderer.state.level.PlayerRenderState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.lumalight.mixin;

import dev.lumalight.client.ClientHooks;
import dev.lumalight.client.RimLightRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GameRenderer.class})
public abstract class GameRendererMixin {
    @Inject(method={"renderLevel"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V", shift=At.Shift.AFTER)})
    private void lumalight$finishPostEffects(CallbackInfo callback) {
        ClientHooks.afterLevelRender();
    }

    @Inject(method={"render3dHud"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V", shift=At.Shift.AFTER)})
    private void lumalight$outlineFirstPerson(CameraRenderState cameraState, PlayerRenderState playerState, OptionsRenderState optionsState, boolean separateHud, CallbackInfo callback) {
        RimLightRenderer.renderFirstPerson();
    }
}

