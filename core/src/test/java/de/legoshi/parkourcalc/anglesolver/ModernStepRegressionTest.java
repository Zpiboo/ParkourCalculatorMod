package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pins the modern (1.21.10) step arithmetic bit-for-bit against real recorded SimulatorEntity
 * transitions (speedrun_nightmare_v21_fail, 2026-06-11). The legacy 1.8.9 float chain was provably
 * NOT bit-exact here (jump boost rounded through float, moveFlying ran the float pipeline instead of
 * movementInputToVelocity's double one, and the ground factor divided by the friction cube instead of
 * the slipperiness cube); any future drift in the modern path fails these exactly.
 */
public class ModernStepRegressionTest {

    private static final ExactJumpModel MODEL = ExactJumpModel.forMcVersion("1.21.10");

    private static ForwardPath step(double[] pos, double[] vel, float yawBefore, float yawRun,
                                    boolean jump, double slip, boolean sprint, float fwd, float str) {
        JumpPhysicsInputs one = new JumpPhysicsInputs(1);
        one.startPos = new Vec3dCore(pos[0], pos[1], pos[2]);
        one.initialVelocity = new Vec3dCore(vel[0], vel[1], vel[2]);
        one.startYaw = yawBefore;
        one.jumpTick = jump ? 0 : -1;
        one.jumpPerTick = new boolean[]{jump};
        one.strafePerTick = new boolean[]{false};
        one.yawLockedPerTick = new boolean[]{true};
        one.speedAmplifier = new int[]{0};
        one.slipPerTick = new double[]{slip};
        one.sprintPerTick = new boolean[]{sprint};
        one.forwardInputPerTick = new float[]{fwd};
        one.strafeInputPerTick = new float[]{str};
        return MODEL.forward(one, new double[]{yawRun});
    }

    private static void assertExact(ForwardPath p, double posX, double posZ, double velX, double velZ) {
        assertEquals(posX, p.posX[1], 0.0);
        assertEquals(posZ, p.posZ[1], 0.0);
        assertEquals(velX, p.velX[1], 0.0);
        assertEquals(velZ, p.velZ[1], 0.0);
    }

    @Test
    public void groundedSprintJumpStep() {
        // recorded tick 370: grounded sprint jump, W only, slip 0.6, facing -12.325562
        ForwardPath p = step(
                new double[]{45.9683216885258, 50.5, -7.57929852180672},
                new double[]{0.016669362117712225, -0.0784000015258789, 0.2628146577895248},
                36.60595F, -12.325562F, true, 0.6, true, 0.98F, 0.0F);
        assertExact(p, 46.05485537110515, -6.996631615622692, 0.0472473961762329, 0.31813616772883113);
    }

    @Test
    public void airSprintDiagonalStep() {
        // recorded tick 377: airborne, sprinting, W+A (normalized 0.7071067 inputs), facing -18.096676
        ForwardPath p = step(
                new double[]{46.50464409245055, 51.67675927506424, -5.062326856680353},
                new double[]{0.09186652456079716, -0.1523351868505571, 0.27073125237977147},
                -8.155473F, -18.096676F, false, Double.NaN, true, 0.7071067F, 0.7071067F);
        assertExact(p, 46.61969566902187, -4.779830618475119, 0.10469693769725337, 0.2570715841755221);
    }

    @Test
    public void airUnsprintedDiagonalStep() {
        // recorded tick 488: airborne after a sprint-breaking wall hit, W+A, facing 215.00014
        ForwardPath p = step(
                new double[]{52.825964626737836, 56.00133597911215, 12.281048914798118},
                new double[]{-0.08022747295749169, 0.1647732818260665, -0.017245498878770433},
                206.36528F, 215.00014F, false, Double.NaN, false, 0.7071067F, 0.7071067F);
        assertExact(p, 52.74226314205912, 12.244107448362682, -0.0761683532527852, -0.03361673542507555);
    }
}
