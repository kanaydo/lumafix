package dev.lumalight.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.lumalight.client.OutlinePassGuard;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skips depth-tested pipelines in the entity outline pass, before anything is bound.
@Mixin(value = PreparedRenderType.class)
public abstract class PreparedRenderTypeMixin {
    @Inject(method = "draw(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V", at = @At("HEAD"), cancellable = true)
    private void lumalight$skipDepthPipelineInOutlinePass(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass, RenderPipeline pipeline, CallbackInfo callback) {
        if (OutlinePassGuard.shouldSkip(pipeline)) {
            callback.cancel();
        }
    }
}
