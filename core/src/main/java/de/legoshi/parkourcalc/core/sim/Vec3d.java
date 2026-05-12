package de.legoshi.parkourcalc.core.sim;

/**
 * Core's value type for 3D positions. Crosses the SimulatedTicker port boundary so
 * core can stay free of Minecraft imports.
 */
public final class Vec3d {

    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(x + dx, y + dy, z + dz);
    }

    public Vec3d add(Vec3d o) {
        return new Vec3d(x + o.x, y + o.y, z + o.z);
    }

    public Vec3d sub(Vec3d o) {
        return new Vec3d(x - o.x, y - o.y, z - o.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec3d)) return false;
        Vec3d other = (Vec3d) o;
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        long bits = 1L;
        bits = 31 * bits + Double.doubleToLongBits(x);
        bits = 31 * bits + Double.doubleToLongBits(y);
        bits = 31 * bits + Double.doubleToLongBits(z);
        return (int) (bits ^ (bits >>> 32));
    }

    @Override
    public String toString() {
        return "Vec3d(" + x + ", " + y + ", " + z + ")";
    }
}
