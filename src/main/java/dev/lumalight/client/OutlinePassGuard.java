package dev.lumalight.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.lumalight.LumaLight;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;

/*
 * Guards the entity outline pass against render types that do not belong in it.
 *
 * The outline pass renders into a target without a depth buffer. On the Vulkan backend a
 * pipeline that uses depth testing has no "without depth" variant (its handle is 0), so
 * binding one there crashes inside MoltenVK/the driver. Vanilla only ever submits the
 * depth-less OUTLINE_CULL/OUTLINE_NO_CULL pipelines, but other mods' render types can reach
 * the pass, and LumaLight's rarity/damage outlines send many more entities through it.
 * Such draws are skipped instead; they could not render correctly without depth anyway.
 */
public final class OutlinePassGuard {
    private static boolean active;
    private static final Set<Identifier> WARNED = new HashSet<Identifier>();

    private OutlinePassGuard() {
    }

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    /** True when the draw must be skipped: inside the outline pass with a depth-tested pipeline. */
    public static boolean shouldSkip(RenderPipeline pipeline) {
        if (!active || pipeline.getDepthStencilState() == null) {
            return false;
        }
        if (WARNED.add(pipeline.getLocation())) {
            LumaLight.LOGGER.warn("Skipped depth-tested pipeline {} in the entity outline pass (it would crash on Vulkan)", pipeline.getLocation());
        }
        return true;
    }
}
