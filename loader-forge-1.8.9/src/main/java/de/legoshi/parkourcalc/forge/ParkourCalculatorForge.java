package de.legoshi.parkourcalc.forge;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.forge.common.Lwjgl2ImGuiHost;
import de.legoshi.parkourcalc.forge.render.Forge8BoxRenderer;
import de.legoshi.parkourcalc.forge.sim.Forge8Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

@Mod(modid = ParkourCalculatorForge.MODID, version = ParkourCalculatorForge.VERSION, clientSideOnly = true, acceptableRemoteVersions = "*")
public class ParkourCalculatorForge {

    public static final String MODID = "parkourcalculator";
    public static final String VERSION = "1.0.0";

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final InputData inputData = new InputData();
    private final OverlayManager overlayManager = new OverlayManager();
    private final Lwjgl2ImGuiHost imguiHost = new Lwjgl2ImGuiHost(overlayManager);
    private final Simulator simulator = new Forge8Simulator();
    private final SimulationRunner runner = new SimulationRunner(simulator);
    private final BoxController boxController = new BoxController();

    private KeyBinding toggleKeyBinding;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        InputOverlay inputOverlay = new InputOverlay(inputData, this::runSimulation, this::setStartToPlayer);
        overlayManager.register("TAS Inputs", inputOverlay);

        toggleKeyBinding = new KeyBinding("key.parkourcalculator.toggle_ui", Keyboard.KEY_K, "key.categories.parkourcalculator");
        ClientRegistry.registerKeyBinding(toggleKeyBinding);

        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("ParkourCalculator init complete; K registered as toggle.");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        while (toggleKeyBinding.isPressed()) {
            overlayManager.setControlPanelOpen(!overlayManager.isControlPanelOpen());
        }
        imguiHost.renderFrame(mc.displayWidth, mc.displayHeight);
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        if (boxController.isEmpty()) return;

        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return;

        float pt = event.partialTicks;
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(BoxStyle.LINE_WIDTH);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer buf = tess.getWorldRenderer();

        // Translucent fill first; wireframe on top.
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.FACES), BoxStyle.FACE_ARGB);
        tess.draw();

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        boxController.render(new Forge8BoxRenderer(buf, camX, camY, camZ, BoxRenderer.Mode.LINES), BoxStyle.WIREFRAME_ARGB);
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void runSimulation() {
        try {
            List<Vec3dCore> path = runner.simulate(inputData);
            boxController.clearAll();
            for (Vec3dCore p : path) {
                boxController.add(p);
            }
            if (!path.isEmpty()) {
                Vec3dCore last = path.get(path.size() - 1);
                LOG.info("Simulated {} ticks; final position ({}, {}, {})",
                        path.size() - 1, last.x, last.y, last.z);
            }
        } catch (IllegalStateException ignored) {
            // Player/world not loaded yet; nothing to simulate.
        }
    }

    private void setStartToPlayer() {
        runner.setStartFromPlayer();
        runSimulation();
    }
}
