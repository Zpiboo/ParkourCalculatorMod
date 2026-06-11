package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The model is collision-free, so a resim can leave the solved path (a wall graze, usually) and silently
 * void every outcome after the divergence. Apply re-checks the resimmed boxes against the plan's predicted
 * trajectory and publishes a deviation warning into the state; a byte-exact match publishes nothing.
 */
public class ApplyDeviationTest {

    private static final int TICKS = 3;

    private final BoxController boxes = new BoxController();
    private final AngleSolverState state = new AngleSolverState();
    private final AtomicReference<Runnable> resim = new AtomicReference<>(() -> { });
    private InputData inputs;
    private AngleSolverEngine engine;

    @Before
    public void setUp() {
        inputs = new InputData();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            inputs.getRows().add(row);
        }
        for (int t = 0; t <= TICKS; t++) boxes.add(placeholder(0.5, 64.0, 0.5, false));
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        engine = new AngleSolverEngine(state, boxes, inputs, t -> resim.get().run(),
                ExactJumpModel.forMcVersion("1.8.9"));

        engine.solve();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep();
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull(r);
        assertTrue(r.isSuccess());
    }

    @Test
    public void matchingResimPublishesNoDeviation() {
        ForwardPath predicted = predictedPath();
        resim.set(() -> rebuildBoxes(predicted, Double.NaN, -1));
        engine.apply();
        assertNull(state.getApplyDeviation());
    }

    @Test
    public void groundClampedYIsNotADeviation() {
        // The model's posY is never clamped onto a surface, so the resim's Y always drifts from it;
        // only X/Z carry the byte-exact contract.
        ForwardPath predicted = predictedPath();
        resim.set(() -> {
            boxes.clearAll();
            for (int k = 0; k <= TICKS; k++) {
                boxes.add(placeholder(predicted.posX[k], 64.0, predicted.posZ[k], false));
            }
        });
        engine.apply();
        assertNull(state.getApplyDeviation());
    }

    @Test
    public void divergedResimPublishesTickAndCause() {
        ForwardPath predicted = predictedPath();
        resim.set(() -> rebuildBoxes(predicted, 0.05, 2));
        engine.apply();
        String dev = state.getApplyDeviation();
        assertNotNull(dev);
        assertTrue("names the first diverged tick: " + dev, dev.contains("T3"));
        assertTrue("attributes the recorded wall hit: " + dev, dev.contains("hit a wall"));
    }

    @Test
    public void divergenceNearASneakRowNamesTheSneakDesync() {
        // No wall hit, but a SNEAK row in the lookback window: the pose-dependent slowdown was
        // sampled from a run that sneaked at a different position.
        inputs.getRows().get(1).setKeyActive(InputRow.Key.SNEAK, true);
        ForwardPath predicted = predictedPath();
        resim.set(() -> rebuildBoxes(predicted, 0.05, 2, false));
        engine.apply();
        String dev = state.getApplyDeviation();
        assertNotNull(dev);
        assertTrue("names the first diverged tick: " + dev, dev.contains("T3"));
        assertTrue("attributes the sneak desync: " + dev, dev.contains("sneak at T2"));
    }

    @Test
    public void cleanReapplyClearsAStaleWarning() {
        ForwardPath predicted = predictedPath();
        resim.set(() -> rebuildBoxes(predicted, 0.05, 2));
        engine.apply();
        assertNotNull(state.getApplyDeviation());
        resim.set(() -> rebuildBoxes(predicted, Double.NaN, -1));
        engine.apply();
        assertNull(state.getApplyDeviation());
    }

    /** Replays the solved yaws through the spec's scenario, exactly as runJob built the plan's path. */
    private ForwardPath predictedPath() {
        JumpPhysicsInputs sc = engine.lastSpecDebug().asScenario();
        double[] yaws = new double[state.getResult().getYaws().size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = state.getResult().getYaws().get(k).yaw;
        return ExactJumpModel.forMcVersion("1.8.9").forward(sc, sc.toGameFacings(yaws));
    }

    /** Boxes from the predicted path; state {@code divergeAt} (if >= 0) shifted by {@code offset} on X
     *  and flagged as a wall hit, like a real clamp would record. */
    private void rebuildBoxes(ForwardPath path, double offset, int divergeAt) {
        rebuildBoxes(path, offset, divergeAt, true);
    }

    private void rebuildBoxes(ForwardPath path, double offset, int divergeAt, boolean flagWall) {
        boxes.clearAll();
        for (int k = 0; k <= TICKS; k++) {
            boolean diverged = k == divergeAt;
            double x = path.posX[k] + (diverged ? offset : 0.0);
            boxes.add(placeholder(x, path.posY[k], path.posZ[k], diverged && flagWall));
        }
    }

    private static TickState placeholder(double x, double y, double z, boolean wallHit) {
        return new TickState(new Vec3dCore(x, y, z), false, false, wallHit, 0f,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    private static void sleep() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
