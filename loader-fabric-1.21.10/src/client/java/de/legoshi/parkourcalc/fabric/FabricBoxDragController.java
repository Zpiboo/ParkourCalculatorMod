package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Click-and-drag the start box across the world plane. The plane Y is locked
 * to the start box's bottom Y at drag start; cursor movement is projected onto
 * that plane via raycast from the camera.
 *
 * Loader-specific (Minecraft Camera, Box raycast, Vec3d) so it stays in the
 * Fabric module rather than core. Lifts to core only if a second loader needs it.
 */
public final class FabricBoxDragController {

    private final BoxController boxController;
    private final BooleanSupplier uiFocused;
    private final Consumer<Vec3dCore> onPositionChange;

    private boolean wasMousePressed = false;
    private DragState dragState = null;

    public FabricBoxDragController(BoxController boxController, BooleanSupplier uiFocused,
                                   Consumer<Vec3dCore> onPositionChange) {
        this.boxController = boxController;
        this.uiFocused = uiFocused;
        this.onPositionChange = onPositionChange;
    }

    public void tick(MinecraftClient client) {
        if (uiFocused.getAsBoolean()) {
            wasMousePressed = false;
            dragState = null;
            return;
        }

        long window = client.getWindow().getHandle();
        boolean mousePressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        Camera camera = client.gameRenderer.getCamera();

        if (mousePressed && !wasMousePressed) {
            tryStartDrag(camera);
        }

        if (mousePressed && dragState != null) {
            updateDrag(client, camera);
        }

        if (!mousePressed) {
            dragState = null;
        }

        wasMousePressed = mousePressed;
    }

    private void tryStartDrag(Camera camera) {
        AABB first = boxController.getFirst();
        if (first == null) return;

        Vec3d start = camera.getPos();
        Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d end = start.add(direction.multiply(128));

        Box firstMc = new Box(
                first.min.x, first.min.y, first.min.z,
                first.max.x, first.max.y, first.max.z
        ).expand(1e-3);

        Optional<Vec3d> hit = firstMc.raycast(start, end);
        if (hit.isEmpty()) return;

        Vec3d cursorOnPlane = projectCursorToPlane(camera, first.min.y);
        if (cursorOnPlane == null) return;

        dragState = new DragState(
                first.min.y,
                first.min.x, first.min.z,
                cursorOnPlane.x, cursorOnPlane.z
        );
    }

    private void updateDrag(MinecraftClient client, Camera camera) {
        if (!client.options.attackKey.isPressed()) {
            dragState = null;
            return;
        }

        Vec3d cursorOnPlane = projectCursorToPlane(camera, dragState.planeY);
        if (cursorOnPlane == null) return;

        double deltaX = cursorOnPlane.x - dragState.startCursorX;
        double deltaZ = cursorOnPlane.z - dragState.startCursorZ;

        Vec3dCore newPosition = new Vec3dCore(
                dragState.startBoxX + deltaX,
                dragState.planeY,
                dragState.startBoxZ + deltaZ
        );

        onPositionChange.accept(newPosition);
    }

    private static Vec3d projectCursorToPlane(Camera camera, double planeY) {
        Vec3d start = camera.getPos();
        Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());

        if (Math.abs(direction.y) < 1e-6) return null;
        double t = (planeY - start.y) / direction.y;
        if (t < 0) return null;

        return start.add(direction.multiply(t));
    }

    private static final class DragState {
        final double planeY;
        final double startBoxX;
        final double startBoxZ;
        final double startCursorX;
        final double startCursorZ;

        DragState(double planeY, double startBoxX, double startBoxZ,
                  double startCursorX, double startCursorZ) {
            this.planeY = planeY;
            this.startBoxX = startBoxX;
            this.startBoxZ = startBoxZ;
            this.startCursorX = startCursorX;
            this.startCursorZ = startCursorZ;
        }
    }
}
