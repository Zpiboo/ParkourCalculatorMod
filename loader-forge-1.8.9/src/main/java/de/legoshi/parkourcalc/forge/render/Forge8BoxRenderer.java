package de.legoshi.parkourcalc.forge.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import net.minecraft.client.renderer.WorldRenderer;

/**
 * BoxRenderer port impl for Forge / MC 1.8.9. Mirrors Forge12BoxRenderer; the
 * only difference is the buffer type (1.8.9 calls it WorldRenderer; 1.12.2
 * renamed it to BufferBuilder). See that class for mode semantics.
 */
public final class Forge8BoxRenderer implements BoxRenderer {

    private final WorldRenderer buf;
    private final double camX;
    private final double camY;
    private final double camZ;
    private final Mode mode;

    public Forge8BoxRenderer(WorldRenderer buf, double camX, double camY, double camZ, Mode mode) {
        this.buf = buf;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.mode = mode;
    }

    @Override
    public void drawBox(AABB box, int argb) {
        if (mode == Mode.LINES) {
            emitEdges(box, argb);
        } else {
            emitFaces(box, argb);
        }
    }

    private void emitEdges(AABB b, int argb) {
        double x0 = b.min.x - camX, y0 = b.min.y - camY, z0 = b.min.z - camZ;
        double x1 = b.max.x - camX, y1 = b.max.y - camY, z1 = b.max.z - camZ;

        edge(x0, y0, z0, x1, y0, z0, argb);
        edge(x1, y0, z0, x1, y0, z1, argb);
        edge(x1, y0, z1, x0, y0, z1, argb);
        edge(x0, y0, z1, x0, y0, z0, argb);

        edge(x0, y1, z0, x1, y1, z0, argb);
        edge(x1, y1, z0, x1, y1, z1, argb);
        edge(x1, y1, z1, x0, y1, z1, argb);
        edge(x0, y1, z1, x0, y1, z0, argb);

        edge(x0, y0, z0, x0, y1, z0, argb);
        edge(x1, y0, z0, x1, y1, z0, argb);
        edge(x1, y0, z1, x1, y1, z1, argb);
        edge(x0, y0, z1, x0, y1, z1, argb);
    }

    private void emitFaces(AABB b, int argb) {
        double x0 = b.min.x - camX, y0 = b.min.y - camY, z0 = b.min.z - camZ;
        double x1 = b.max.x - camX, y1 = b.max.y - camY, z1 = b.max.z - camZ;

        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, argb);
        quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, argb);
        quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, argb);
        quad(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, argb);
        quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, argb);
        quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, argb);
    }

    private void edge(double ax, double ay, double az,
                      double bx, double by, double bz, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        buf.pos(ax, ay, az).color(r, g, b, a).endVertex();
        buf.pos(bx, by, bz).color(r, g, b, a).endVertex();
    }

    private void quad(double ax, double ay, double az,
                      double bx, double by, double bz,
                      double cx, double cy, double cz,
                      double dx, double dy, double dz, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        buf.pos(ax, ay, az).color(r, g, b, a).endVertex();
        buf.pos(bx, by, bz).color(r, g, b, a).endVertex();
        buf.pos(cx, cy, cz).color(r, g, b, a).endVertex();

        buf.pos(ax, ay, az).color(r, g, b, a).endVertex();
        buf.pos(cx, cy, cz).color(r, g, b, a).endVertex();
        buf.pos(dx, dy, dz).color(r, g, b, a).endVertex();
    }
}
