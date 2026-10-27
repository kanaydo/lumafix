/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.resources.model.geometry.BakedQuad$MaterialInfo
 *  net.minecraft.core.Direction
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package dev.lumalight.mixin;

import dev.lumalight.client.RimLightConfig;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={BakedQuad.MaterialInfo.class})
public abstract class BakedQuadMaterialInfoMixin {
    @Inject(method={"shadeDirectionOverride"}, at={@At(value="RETURN")}, cancellable=true)
    private void lumalight$flatBlockShading(CallbackInfoReturnable<Direction> callback) {
        if (!RimLightConfig.BLOCK_SHADING_ENABLED.get().booleanValue()) {
            callback.setReturnValue(Direction.UP);
        }
    }
}

