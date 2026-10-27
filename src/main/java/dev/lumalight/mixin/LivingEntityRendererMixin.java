/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.util.ARGB
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.lumalight.mixin;

import dev.lumalight.client.CartoonEffectsConfig;
import dev.lumalight.client.OutlineVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin {
    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void lumalight$damageOutline(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo callback) {
        // Never flash the entity being viewed from (the local player): its model and layers,
        // including other mods' player rendering, would be drawn into the outline pass when hurt.
        if (entity == Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        if (CartoonEffectsConfig.DAMAGE_FLASH.get().booleanValue() && state.hasRedOverlay && OutlineVisibility.isFullyVisible((Entity)entity)) {
            state.outlineColor = ARGB.opaque((int)16734776);
        }
    }
}

