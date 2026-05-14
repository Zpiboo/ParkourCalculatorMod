package de.legoshi.parkourcalc.forge12.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import net.minecraft.client.renderer.BufferBuilder;

/**
 * BoxRenderer port impl for Forge / MC 1.12.2. Two modes:
 *
 *   - LINES: emits the 12 wireframe edges of each AABB. Caller wraps
 *     buf.begin(GL_LINES, POSITION_COLOR).
 *   - FACES: emits the 6 faces as 12 triangles (36 vertices). Caller wraps
 *     buf.begin(GL_TRIANGLES, POSITION_COLOR) and disables cull so both sides
 *     of each face render (we don't bother with consistent winding).
 *
 * Color comes from the caller (typically BoxStyle.WIREFRAME_ARGB / FACE_ARGB);
 * the renderer just emits geometry tinted with whatever it's given.
 */
public final class Forge12BoxRenderer implements BoxRenderer {

    private final BufferBuilder buf;
    private final double camX;
    private final double camY;
    private final double camZ;
    private final Mode mode;

    public Forge12BoxRenderer(BufferBuilder buf, double camX, double camY, double camZ, Mode mode) {
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

        // -Y bottom, +Y top, -Z, +Z, -X, +X. Winding doesn't matter; cull is off.
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

    /** Quad ABCD split into triangles ABC and ACD (shared AC diagonal). */
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
