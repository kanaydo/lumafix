package dev.lumalight.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/*
 * Keeps vanilla sky and cloud pixels untouched by the post passes (always on).
 *
 * Sky pixels have cleared depth. Vanilla clouds write depth like terrain, so they are
 * found by reconstructing each pixel's world Y and testing it against the cloud layer
 * (LevelRenderState.cloudHeight up to 4 blocks above, see CloudRenderer.prepare).
 * Terrain that sits inside that band is treated as cloud as well.
 *
 * Appended to a uniform block as: mat4 InverseProjection, mat4 InverseViewRotation,
 * vec4 SkyMask (enabled, camera Y, cloud bottom Y, zero-to-one depth flag).
 */
final class VanillaSkyMask {
    static final int STD140_SIZE = 144;
    // Far below any reachable Y, so the cloud band test never matches.
    private static final float NO_CLOUDS = -1.0e9f;

    private VanillaSkyMask() {
    }

    static float cloudBottom(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCloudStatus() == CloudStatus.OFF || ARGB.alpha(context.levelState().cloudColor) == 0) {
            return NO_CLOUDS;
        }
        return context.levelState().cloudHeight;
    }

    /** Writes the mask; a null context (e.g. the first-person pass) writes it disabled. */
    static Std140Builder put(Std140Builder builder, LevelRenderContext context) {
        if (context == null) {
            return builder.putMat4f((Matrix4fc)new Matrix4f()).putMat4f((Matrix4fc)new Matrix4f()).putVec4(0.0f, 0.0f, NO_CLOUDS, 0.0f);
        }
        Matrix4f inverseProjection = new Matrix4f((Matrix4fc)context.levelState().cameraRenderState.projectionMatrix).invert();
        Matrix4f inverseViewRotation = new Matrix4f((Matrix4fc)context.levelState().cameraRenderState.viewRotationMatrix).invert();
        float cameraY = (float)context.levelState().cameraRenderState.pos.y;
        float zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0f : 0.0f;
        return builder.putMat4f((Matrix4fc)inverseProjection).putMat4f((Matrix4fc)inverseViewRotation).putVec4(1.0f, cameraY, VanillaSkyMask.cloudBottom(context), zeroToOne);
    }
}
