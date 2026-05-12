package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
        boxController.setOnStartPositionChange(ParkourCalculatorFabric::handleStartPositionChange);

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

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        boxController.render(matrixStack, consumers);
        consumers.draw();
    }

    private static void handleBoxDragging(MinecraftClient client) {
        if (isUiFocused()) {
            wasMousePressed = false;
            boxController.stopDrag();
            return;
        }

        long window = client.getWindow().getHandle();
        boolean mousePressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        Camera camera = client.gameRenderer.getCamera();

        // Start drag on click
        if (mousePressed && !wasMousePressed) {
            tryStartDrag(camera);
        }

        // Update drag
        if (mousePressed && boxController.isDragging()) {
            boxController.updateDrag(camera);
        }

        // Stop drag on release
        if (!mousePressed) {
            boxController.stopDrag();
        }

        wasMousePressed = mousePressed;
    }

    private static void tryStartDrag(Camera camera) {
        Vec3d start = camera.getPos();
        Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d end = start.add(direction.multiply(128));

        boxController.pick(start, end).ifPresent(hit -> {
            if (hit.equals(boxController.getFirst())) {
                boxController.startDrag(hit, camera);
            }
        });
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
            boxController.add(new Vec3d(p.x, p.y, p.z));
        }
    }

    private static void setStartToPlayerPosition() {
        runner.setStartFromPlayer();
    }

    private static void handleStartPositionChange(Vec3dCore pos) {
        runner.setStartPosition(pos);
        runSimulation();
    }

    /**
     * Helper class for tracking key press/release transitions.
     */
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