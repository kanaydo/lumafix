package dev.lumalight.mixin;

import dev.lumalight.client.OutlinePassGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks the span of the entity outline pass for OutlinePassGuard.
@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedFrame")
public abstract class FeatureRenderDispatcherPreparedFrameMixin {
    @Inject(method = "executeOutline", at = @At("HEAD"))
    private void lumalight$beginOutlinePass(CallbackInfo callback) {
        OutlinePassGuard.begin();
    }

    @Inject(method = "executeOutline", at = @At("RETURN"))
    private void lumalight$endOutlinePass(CallbackInfo callback) {
        OutlinePassGuard.end();
    }
}
