package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import imgui.ImGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class ParkourCalculatorFabric implements ClientModInitializer {

    public static final String MOD_ID = "parkourcalculator";
    private static final Logger LOG = LoggerFactory.getLogger("ParkourCalculator");

    public static KeyBinding toggleKeyBinding;

    // Core components
    private static final InputData inputData = new InputData();
    private static final Simulator simulator = new FabricSimulator();
    private static final SimulationRunner runner = new SimulationRunner(simulator);
    private static final BoxController boxController = new BoxController();
    private static final OverlayManager overlayManager = new OverlayManager();

    // Input state tracking
    private static final KeyState escapeKey = new KeyState();
    private static boolean wasMousePressed = false;

    // Start-box drag state. Lives here rather than in BoxController because the
    // math is loader-specific (camera, mouse ray) and 1.8.9 / 1.12.2 don't have
    // drag yet. Lifts to core once a second loader needs it.
    private static DragState dragState = null;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.parkourcalculator.toggle_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                category
        ));

        InputOverlay inputOverlay = new InputOverlay(
                inputData,
                ParkourCalculatorFabric::runSimulation,
                ParkourCalculatorFabric::setStartToPlayerPosition
        );

        overlayManager.register("TAS Inputs", inputOverlay);

        ClientTickEvents.END_CLIENT_TICK.register(ParkourCalculatorFabric::handleInput);
    }

    private static void handleInput(MinecraftClient client) {
        if (client.getWindow() == null) return;

        if (toggleKeyBinding.wasPressed()) {
            setOverlayOpen(!overlayManager.isControlPanelOpen());
        }

        long window = client.getWindow().getHandle();
        if (escapeKey.justPressed(window, GLFW.GLFW_KEY_ESCAPE) && overlayManager.isControlPanelOpen()) {
            setOverlayOpen(false);
        }
    }

    private static void setOverlayOpen(boolean open) {
        MinecraftClient client = MinecraftClient.getInstance();
        overlayManager.setControlPanelOpen(open);

        if (open) {
            client.mouse.unlockCursor();
        } else {
            client.mouse.lockCursor();
            clearImGuiInputState();
        }
    }

    private static void clearImGuiInputState() {
        ImGui.getIO().clearInputKeys();
        for (int i = 0; i < 5; i++) {
            ImGui.getIO().setMouseDown(i, false);
        }
    }

    /**
     * Called from WorldRendererMixin to render world overlays.
     */
    public static void onWorldRender(Matrix4f positionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        handleBoxDragging(client);

        if (boxController.isEmpty()) return;

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        matrixStack.push();
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        boxController.render(new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.FACES), BoxStyle.FACE_ARGB);
        boxController.render(new FabricBoxRenderer(matrixStack, consumers, BoxRenderer.Mode.LINES), BoxStyle.WIREFRAME_ARGB);
        consumers.draw();

        matrixStack.pop();
    }

    private static void handleBoxDragging(MinecraftClient client) {
        if (isUiFocused()) {
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
            updateDrag(camera);
        }

        if (!mousePressed) {
            dragState = null;
        }

        wasMousePressed = mousePressed;
    }

    private static void tryStartDrag(Camera camera) {
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

    private static void updateDrag(Camera camera) {
        if (!MinecraftClient.getInstance().options.attackKey.isPressed()) {
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

        handleStartPositionChange(newPosition);
    }

    private static Vec3d projectCursorToPlane(Camera camera, double planeY) {
        Vec3d start = camera.getPos();
        Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());

        if (Math.abs(direction.y) < 1e-6) return null;
        double t = (planeY - start.y) / direction.y;
        if (t < 0) return null;

        return start.add(direction.multiply(t));
    }

    /**
     * Called from InGameHudMixin to render ImGui overlays.
     */
    public static void onHudRender() {
        ImGuiImpl.beginImGuiRendering();
        overlayManager.render(ImGui.getIO());
        ImGuiImpl.endImGuiRendering();
    }

    public static boolean isUiFocused() {
        return overlayManager.isControlPanelOpen();
    }

    private static void runSimulation() {
        List<Vec3dCore> path = runner.simulate(inputData);
        boxController.clearAll();
        for (Vec3dCore p : path) {
            boxController.add(p);
        }
    }

    private static void setStartToPlayerPosition() {
        runner.setStartFromPlayer();
    }

    private static void handleStartPositionChange(Vec3dCore pos) {
        runner.setStartPosition(pos);
        runSimulation();
    }

    private static class DragState {
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

    private static class KeyState {
        private boolean wasPressed = false;

        boolean justPressed(long window, int key) {
            boolean isPressed = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean justPressed = isPressed && !wasPressed;
            wasPressed = isPressed;
            return justPressed;
        }
    }
}
