package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ui.BoxColorPicker;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

/** Loader-agnostic per-frame inputs for the cached geometry (hash, selection, bake emitters, patch spec). */
public final class PathRenderPlan {

    private static final double ALL = Double.POSITIVE_INFINITY;

    public final int structuralHash;
    public final Set<Integer> selection;
    public final Consumer<BoxRenderer> faceEmitter;
    public final Consumer<BoxRenderer> lineEmitter;
    public final SelectionPatchSpec patch;

    private PathRenderPlan(int structuralHash, Set<Integer> selection, Consumer<BoxRenderer> faceEmitter, Consumer<BoxRenderer> lineEmitter, SelectionPatchSpec patch) {
        this.structuralHash = structuralHash;
        this.selection = selection;
        this.faceEmitter = faceEmitter;
        this.lineEmitter = lineEmitter;
        this.patch = patch;
    }

    public static PathRenderPlan build(BoxController boxController, Settings settings, SelectionManager selection) {
        Set<Integer> selectedBoxes = selection.getSelectedBoxes();
        BoxColorPicker face = (i, s) -> BoxStyle.tickFaceArgb(settings, s, selectedBoxes.contains(i));
        BoxColorPicker line = (i, s) -> BoxStyle.tickLineArgb(settings, s, selectedBoxes.contains(i));
        BoxColorPicker hitbox = (i, s) -> BoxStyle.hitboxLineArgb(settings, selectedBoxes.contains(i));

        // Drop the hitbox (not the path) when it would overflow a buffer, e.g. full hitbox + subtick at huge counts.
        int edges = PathVertexLayout.hitboxEdges(settings.showHitbox, settings.showFullHitbox);
        boolean drawHitbox = edges != 0 && boxController.facePassFitsBudget(edges, settings.showSubtick, settings.showYawArrows);
        boolean full = drawHitbox && settings.showFullHitbox;
        boolean floor = drawHitbox && !settings.showFullHitbox;

        Consumer<BoxRenderer> faceEmitter = faces -> {
            boxController.render(faces, face, 0, 0, 0, ALL);
            if (floor) boxController.renderHitboxFloorOutline(faces, hitbox, settings.showSubtick, 0, 0, 0, ALL);
            if (full) boxController.renderHitboxFullWireframe(faces, hitbox, settings.showSubtick, 0, 0, 0, ALL);
            if (settings.showYawArrows) boxController.renderYawArrows(faces, BoxStyle.yawArrowArgb(settings), 0, 0, 0, ALL);
        };

        Consumer<BoxRenderer> lineEmitter = lines -> {
            boxController.render(lines, line, 0, 0, 0, ALL);
            if (settings.showSubtick) boxController.renderPath(lines, BoxStyle.subtickPathArgb(settings), 0, 0, 0, ALL);
        };

        SelectionPatchSpec patch = new SelectionPatchSpec(face, line, hitbox, drawHitbox, full, settings.showSubtick);
        return new PathRenderPlan(structuralHash(settings), selectedBoxes, faceEmitter, lineEmitter, patch);
    }

    /** Colors and overlay toggles, but NOT selection (which is patched in place). */
    private static int structuralHash(Settings settings) {
        int h = 1;
        h = 31 * h + Arrays.hashCode(settings.tickDefault);
        h = 31 * h + Arrays.hashCode(settings.tickSelected);
        h = 31 * h + Arrays.hashCode(settings.tickSoftCollision);
        h = 31 * h + Arrays.hashCode(settings.tickWall);
        h = 31 * h + Arrays.hashCode(settings.tickAir);
        h = 31 * h + Arrays.hashCode(settings.tickSneak);
        h = 31 * h + Arrays.hashCode(settings.hitboxDefault);
        h = 31 * h + Arrays.hashCode(settings.hitboxSelected);
        h = 31 * h + Arrays.hashCode(settings.yawArrow);
        h = 31 * h + Arrays.hashCode(settings.subtickPath);
        h = 31 * h + (settings.showHitbox ? 1 : 0);
        h = 31 * h + (settings.showFullHitbox ? 2 : 0);
        h = 31 * h + (settings.showYawArrows ? 4 : 0);
        h = 31 * h + (settings.showSubtick ? 8 : 0);
        return h;
    }
}
