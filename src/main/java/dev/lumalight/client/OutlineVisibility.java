package dev.lumalight.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/*
 * Decides whether an entity is fully visible from the camera (7 block raycasts).
 *
 * Results are cached per entity for one game tick, so the raycasts run at most 20 times a
 * second per entity instead of every frame. Cached values hold no Level or Entity reference,
 * so the weak keys can be collected; the cache is also dropped whenever the level changes.
 */
public final class OutlineVisibility {
    private static final Map<Entity, CachedVisibility> CACHE = new WeakHashMap<Entity, CachedVisibility>();
    private static Level cacheLevel;

    private OutlineVisibility() {
    }

    public static boolean isFullyVisible(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (minecraft.level == null || !camera.isInitialized()) {
            return false;
        }
        if (camera.entity() == entity) {
            return true;
        }
        if (cacheLevel != minecraft.level) {
            CACHE.clear();
            cacheLevel = minecraft.level;
        }
        long tick = minecraft.level.getGameTime();
        CachedVisibility cached = CACHE.get(entity);
        if (cached != null && cached.tick == tick) {
            return cached.visible;
        }
        boolean visible = OutlineVisibility.testBox(minecraft.level, entity, camera.position(), entity.getBoundingBox());
        CACHE.put(entity, new CachedVisibility(tick, visible));
        return visible;
    }

    public static void clear() {
        CACHE.clear();
        cacheLevel = null;
    }

    private static boolean testBox(Level level, Entity entity, Vec3 camera, AABB box) {
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double insetX = Math.min(0.12, box.getXsize() * 0.2);
        double insetY = Math.min(0.12, box.getYsize() * 0.2);
        double insetZ = Math.min(0.12, box.getZsize() * 0.2);
        Vec3[] samples = new Vec3[]{new Vec3(centerX, centerY, centerZ), new Vec3(box.minX + insetX, centerY, centerZ), new Vec3(box.maxX - insetX, centerY, centerZ), new Vec3(centerX, box.minY + insetY, centerZ), new Vec3(centerX, box.maxY - insetY, centerZ), new Vec3(centerX, centerY, box.minZ + insetZ), new Vec3(centerX, centerY, box.maxZ - insetZ)};
        for (Vec3 sample : samples) {
            if (!OutlineVisibility.hasClearRay(level, entity, camera, sample)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasClearRay(Level level, Entity entity, Vec3 camera, Vec3 target) {
        if (camera.distanceToSqr(target) < 0.0625) {
            return true;
        }
        BlockHitResult hit = level.clip(new ClipContext(camera, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 0.0025;
    }

    private record CachedVisibility(long tick, boolean visible) {
    }
}
