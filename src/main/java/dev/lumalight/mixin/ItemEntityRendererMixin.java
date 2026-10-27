/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.ItemEntityRenderer
 *  net.minecraft.client.renderer.entity.state.ItemEntityRenderState
 *  net.minecraft.util.ARGB
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.item.Rarity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.lumalight.mixin;

import dev.lumalight.client.CartoonEffectsConfig;
import dev.lumalight.client.ItemOutlineColor;
import dev.lumalight.client.OutlineVisibility;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemEntityRenderer.class})
public abstract class ItemEntityRendererMixin {
    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void lumalight$rarityOutline(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo callback) {
        if (CartoonEffectsConfig.RARITY_OUTLINES.get().booleanValue() && !entity.getItem().isEmpty() && OutlineVisibility.isFullyVisible((Entity)entity)) {
            Rarity rarity = entity.getItem().getRarity();
            int markerAlpha = switch (rarity) {
                case COMMON -> 51;
                case UNCOMMON -> 102;
                case RARE -> 153;
                case EPIC -> 204;
            };
            state.outlineColor = ARGB.color((int)markerAlpha, (int)ItemOutlineColor.find(entity, state));
        }
    }
}
