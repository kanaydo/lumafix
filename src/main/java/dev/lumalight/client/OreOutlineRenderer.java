package dev.lumalight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/*
 * Outlines ore blocks that are exposed to a non-solid neighbour, ported from the original
 * LumaLight "Visible ore outlines". It is not X-ray: only ores with an open face are tracked,
 * and outlines are drawn with depth testing after terrain, so covered blocks stay hidden.
 *
 * The area around the player is scanned one horizontal slice at a time (one of 21 slices,
 * every 4 ticks), so a full refresh takes about 4 seconds. Touching ores are merged into
 * clusters so each vein gets a single outline shape.
 */
final class OreOutlineRenderer {
    private static final int SLICES = 21;
    private static final int SCAN_INTERVAL_TICKS = 4;
    private static final int FALLBACK_RGB = 0x74D9FF;
    private static final Map<Long, OreBlock> ORES = new HashMap<Long, OreBlock>();
    private static List<OreCluster> clusters = List.of();
    private static boolean clustersDirty = true;
    private static ClientLevel lastLevel;
    private static int scanSlice;
    private static int scanCooldown;

    private OreOutlineRenderer() {
    }

    static void tick(Minecraft minecraft) {
        if (!CartoonEffectsConfig.ORE_OUTLINES.get() || minecraft.level == null || minecraft.player == null) {
            if (!ORES.isEmpty() || lastLevel != null) {
                OreOutlineRenderer.clear();
            }
            return;
        }
        if (lastLevel != minecraft.level) {
            OreOutlineRenderer.clear();
            lastLevel = minecraft.level;
        }
        if (scanCooldown-- <= 0) {
            scanCooldown = SCAN_INTERVAL_TICKS - 1;
            OreOutlineRenderer.scanSlice(minecraft.level, minecraft.player.blockPosition());
        }
    }

    static void collectSubmits(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!CartoonEffectsConfig.ORE_OUTLINES.get() || minecraft.level == null || ShaderPackCompatibility.isShaderPackActive()) {
            return;
        }
        List<OreCluster> visible = OreOutlineRenderer.clusters();
        if (visible.isEmpty()) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack stack = context.poseStack();
        double maximumDistance = CartoonEffectsConfig.ORE_OUTLINE_DISTANCE.get().intValue();
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        float lineWidth = CartoonEffectsConfig.ORE_OUTLINE_THICKNESS.get().floatValue();
        float opacity = CartoonEffectsConfig.ORE_OUTLINE_OPACITY.get().floatValue();
        float brightness = CartoonEffectsConfig.ORE_OUTLINE_BRIGHTNESS.get().floatValue();
        float pulseAmount = CartoonEffectsConfig.ORE_OUTLINE_PULSE.get().floatValue();
        long gameTime = minecraft.level.getGameTime();
        for (OreCluster cluster : visible) {
            if (!cluster.isWithin(camera, maximumDistanceSquared)) continue;
            float pulse = 0.88f + pulseAmount * Mth.sin((double)(((float)gameTime + (float)cluster.anchor.asLong() * 0.01f) * 0.12f));
            int alpha = Mth.clamp(Math.round(255.0f * opacity * pulse), 8, 254);
            int color = alpha << 24 | OreOutlineRenderer.brighten(cluster.rgb, brightness);
            stack.pushPose();
            stack.translate((double)cluster.anchor.getX() - camera.x, (double)cluster.anchor.getY() - camera.y, (double)cluster.anchor.getZ() - camera.z);
            context.submitNodeCollector().submitShapeOutline(stack, cluster.shape, RenderTypes.LINES_TRANSLUCENT, color, lineWidth, true);
            stack.popPose();
        }
    }

    static void clear() {
        ORES.clear();
        clusters = List.of();
        clustersDirty = true;
        lastLevel = null;
        scanSlice = 0;
        scanCooldown = 0;
    }

    private static void scanSlice(ClientLevel level, BlockPos center) {
        int y = center.getY() - SLICES / 2 + Math.floorMod(scanSlice++, SLICES);
        int scanRadius = Math.max(13, CartoonEffectsConfig.ORE_OUTLINE_DISTANCE.get());
        long maximumDistance = (long)(scanRadius + 2) * (long)(scanRadius + 2);
        if (ORES.keySet().removeIf(key -> OreOutlineRenderer.horizontalDistance(key, center) > maximumDistance)) {
            clustersDirty = true;
        }
        int maximumOres = CartoonEffectsConfig.ORE_OUTLINE_MAX_COUNT.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dz = -scanRadius; dz <= scanRadius; ++dz) {
            for (int dx = -scanRadius; dx <= scanRadius; ++dx) {
                cursor.set(center.getX() + dx, y, center.getZ() + dz);
                long key = cursor.asLong();
                if (!level.hasChunkAt(cursor)) {
                    if (ORES.remove(key) != null) {
                        clustersDirty = true;
                    }
                    continue;
                }
                BlockState state = level.getBlockState(cursor);
                if (state.is(ConventionalBlockTags.ORES) && OreOutlineRenderer.isExposed(level, cursor) && (ORES.containsKey(key) || ORES.size() < maximumOres)) {
                    OreBlock existing = ORES.get(key);
                    if (existing != null && existing.state == state) continue;
                    int rgb = BlockTextureLightColor.find(state, level, cursor, FALLBACK_RGB);
                    ORES.put(key, new OreBlock(cursor.immutable(), state, rgb));
                    clustersDirty = true;
                } else if (ORES.remove(key) != null) {
                    clustersDirty = true;
                }
            }
        }
    }

    private static boolean isExposed(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            neighbor.setWithOffset((Vec3i)pos, direction);
            if (!level.getBlockState(neighbor).isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    private static List<OreCluster> clusters() {
        if (!clustersDirty) {
            return clusters;
        }
        HashMap<Long, OreBlock> remaining = new HashMap<Long, OreBlock>(ORES);
        ArrayList<OreCluster> result = new ArrayList<OreCluster>();
        ArrayDeque<OreBlock> queue = new ArrayDeque<OreBlock>();
        while (!remaining.isEmpty()) {
            OreBlock first = remaining.values().iterator().next();
            remaining.remove(first.pos.asLong());
            queue.add(first);
            ArrayList<OreBlock> members = new ArrayList<OreBlock>();
            while (!queue.isEmpty()) {
                OreBlock current = queue.removeFirst();
                members.add(current);
                for (Direction direction : Direction.values()) {
                    OreBlock neighbor = remaining.remove(current.pos.relative(direction).asLong());
                    if (neighbor != null) {
                        queue.addLast(neighbor);
                    }
                }
            }
            result.add(OreOutlineRenderer.createCluster(members));
        }
        clusters = List.copyOf(result);
        clustersDirty = false;
        return clusters;
    }

    private static OreCluster createCluster(List<OreBlock> members) {
        BlockPos anchor = members.getFirst().pos;
        VoxelShape shape = Shapes.empty();
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>(members.size());
        for (OreBlock member : members) {
            BlockPos pos = member.pos;
            positions.add(pos);
            int dx = pos.getX() - anchor.getX();
            int dy = pos.getY() - anchor.getY();
            int dz = pos.getZ() - anchor.getZ();
            shape = Shapes.or(shape, Shapes.box(dx, dy, dz, dx + 1.0, dy + 1.0, dz + 1.0));
            red += member.rgb >> 16 & 0xFF;
            green += member.rgb >> 8 & 0xFF;
            blue += member.rgb & 0xFF;
        }
        int count = Math.max(1, members.size());
        int rgb = (int)(red / count) << 16 | (int)(green / count) << 8 | (int)(blue / count);
        return new OreCluster(anchor, shape.optimize(), rgb, List.copyOf(positions));
    }

    private static int brighten(int rgb, float amount) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        red += Math.round((float)(255 - red) * amount);
        green += Math.round((float)(255 - green) * amount);
        blue += Math.round((float)(255 - blue) * amount);
        return Mth.clamp(red, 0, 255) << 16 | Mth.clamp(green, 0, 255) << 8 | Mth.clamp(blue, 0, 255);
    }

    private static long horizontalDistance(long packedPos, BlockPos center) {
        long dx = BlockPos.getX(packedPos) - center.getX();
        long dz = BlockPos.getZ(packedPos) - center.getZ();
        return dx * dx + dz * dz;
    }

    private record OreBlock(BlockPos pos, BlockState state, int rgb) {
    }

    private record OreCluster(BlockPos anchor, VoxelShape shape, int rgb, List<BlockPos> blocks) {
        private boolean isWithin(Vec3 camera, double maximumDistanceSquared) {
            for (BlockPos block : this.blocks) {
                if (block.distToCenterSqr((Position)camera) <= maximumDistanceSquared) {
                    return true;
                }
            }
            return false;
        }
    }
}
