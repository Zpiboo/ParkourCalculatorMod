package de.legoshi.parkourcalc.core.sim;

/**
 * Axis-aligned bounding box defined by two Vec3dCore corners. Pure value type;
 * matches the shape of net.minecraft.util.math.Box / similar in loader code while
 * keeping core MC-free.
 */
public final class AABB {

    public final Vec3dCore min;
    public final Vec3dCore max;

    public AABB(Vec3dCore min, Vec3dCore max) {
        this.min = min;
        this.max = max;
    }

    /** Cube of the given edge length anchored at {@code corner} as the min vertex. */
    public static AABB ofCube(Vec3dCore corner, double size) {
        return new AABB(corner, corner.add(size, size, size));
    }
}
