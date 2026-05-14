package de.legoshi.parkourcalc.core.ui;

/**
 * Centralised visual constants for the simulated-path boxes. All loaders read
 * from here so size, stroke, and colors stay in sync across Fabric / Forge
 * 1.8.9 / Forge 1.12.2. Per-state coloring (v1.1.0: grounded / airborne /
 * landing) will grow additional constants here.
 *
 * Colors are packed ARGB ints: high byte = alpha, then R, G, B.
 */
public final class BoxStyle {

    /** Edge length of each tick box, in blocks. */
    public static final double BOX_SIZE = 0.1;

    /**
     * Wireframe line thickness in pixels. The Forge loaders pass this to
     * GL11.glLineWidth before the LINES pass; Fabric draws lines through a
     * DrawMode.DEBUG_LINES pipeline that's always 1px regardless of this
     * value. So this is effectively the Forge knob — drivers that ignore
     * glLineWidth > 1.0F (most desktop GPUs) will render Fabric and Forge
     * at the same 1px width.
     */
    public static final float LINE_WIDTH = 3.0F;

    /** Outline color for the wireframe edge pass (opaque grey). */
    public static final int WIREFRAME_ARGB = 0xFFB2B2B2;

    /** Fill color for the translucent face pass (~25% alpha grey). */
    public static final int FACE_ARGB = 0x40B2B2B2;

    private BoxStyle() {
    }
}
