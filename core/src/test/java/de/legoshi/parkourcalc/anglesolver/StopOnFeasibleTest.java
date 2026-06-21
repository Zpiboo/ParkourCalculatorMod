package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StopOnFeasibleTest {

    private static final double SIGMA = 90.0;

    private static JumpSpec singleJumpSpec() {
        ProblemFixture pf = ProblemFixture.load("solve", "j005");
        return pf.solveDirected(30_000L, AngleSolverState.Axis.X, AngleSolverState.Goal.MAX).engine.lastSpecDebug();
    }

    private static ExactJumpModel modelFor(String name) {
        return ProblemFixture.load("solve", name).model;
    }

    private static double violation(ExactJumpModel model, JumpSpec spec, double[] yawsAbsWrapped) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbsWrapped));
        ForwardPath path = model.forward(sc, gf);
        return c.maxViolation(gf, path);
    }

    @Test
    public void progressKeepsTheBestByObjectiveSense() {
        SolveProgress maxP = new SolveProgress(true, true);
        assertFalse(maxP.haveBest());
        assertTrue(maxP.stopOnFeasible());

        maxP.report(new double[]{1.0, 2.0}, 5.0, 0.0, true);
        assertTrue(maxP.haveBest());
        maxP.report(new double[]{3.0, 4.0}, 3.0, 0.0, true);
        assertEquals("a lower objective never displaces the best for MAX", 5.0, maxP.bestObjective(), 0.0);
        assertArrayEquals(new double[]{1.0, 2.0}, maxP.bestYaws(), 0.0);
        maxP.report(new double[]{7.0, 8.0}, 9.0, 0.0, true);
        assertEquals(9.0, maxP.bestObjective(), 0.0);
        assertArrayEquals(new double[]{7.0, 8.0}, maxP.bestYaws(), 0.0);

        SolveProgress minP = new SolveProgress(false, false);
        assertFalse(minP.stopOnFeasible());
        minP.report(new double[]{1.0}, 5.0, 0.0, true);
        minP.report(new double[]{2.0}, 3.0, 0.0, true);
        assertEquals("a lower objective wins for MIN", 3.0, minP.bestObjective(), 0.0);
        assertArrayEquals(new double[]{2.0}, minP.bestYaws(), 0.0);
    }

    @Test
    public void infeasibleBestTracksObjectiveTowardGoalUntilFeasibilityWins() {
        SolveProgress p = new SolveProgress(true, false);
        p.report(new double[]{1.0}, 2.0, 0.5, false);
        assertTrue(p.haveBest());
        assertFalse("an infeasible candidate is still surfaced as the best so far", p.isBestFeasible());
        assertEquals(2.0, p.bestObjective(), 0.0);

        p.report(new double[]{2.0}, 5.0, 0.9, false);
        assertEquals("an infeasible best ranks by objective toward the goal, not by violation", 5.0, p.bestObjective(), 0.0);
        assertArrayEquals(new double[]{2.0}, p.bestYaws(), 0.0);

        p.report(new double[]{3.0}, 1.0, 0.01, false);
        assertEquals("a worse-objective attempt is ignored even when it is closer to feasible", 5.0, p.bestObjective(), 0.0);

        p.report(new double[]{9.0}, -100.0, 0.0, true);
        assertTrue("feasibility dominates, even with a worse objective", p.isBestFeasible());
        assertArrayEquals(new double[]{9.0}, p.bestYaws(), 0.0);

        p.report(new double[]{8.0}, 5.0, 0.0, true);
        assertEquals("once feasible, the better objective wins", 5.0, p.bestObjective(), 0.0);
        p.report(new double[]{7.0}, 0.0, 0.0, true);
        assertEquals(5.0, p.bestObjective(), 0.0);

        SolveProgress minP = new SolveProgress(false, false);
        minP.report(new double[]{1.0}, 5.0, 0.1, false);
        minP.report(new double[]{2.0}, 3.0, 0.9, false);
        assertEquals("for MIN, a smaller objective wins while infeasible", 3.0, minP.bestObjective(), 0.0);
        assertArrayEquals(new double[]{2.0}, minP.bestYaws(), 0.0);
    }

    @Test
    public void infeasibleSolveReturnsTheBestObjectiveAttemptNotTheLowestViolation() {
        ExactJumpModel model = modelFor("j005");
        JumpPhysicsInputs sc = ProblemFixture.load("solve", "j005")
                .specFor(AngleSolverState.Axis.X, AngleSolverState.Goal.MAX).asScenario();
        int tick = sc.numTicks;
        List<JumpConstraint> cons = Collections.singletonList(
                new JumpConstraint(JumpConstraint.Mode.Z, tick, null, JumpConstraint.Op.PLUS,
                        JumpConstraint.Cmp.GE, 1.0e6, "unreachableZ"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, tick));

        SolveCore.Budget budget = new SolveCore.Budget(8, 2500, 2, BucketAscentPolish.FAST);
        SolveProgress progress = new SolveProgress(true, false);
        double[] yaws = SolveCore.optimize(model, spec, budget, SIGMA, 0.0,
                new AtomicBoolean(false), null, 0L, false, progress);

        assertNotNull(yaws);
        assertTrue("the unreachable Z wall keeps every candidate infeasible", violation(model, spec, yaws) > 0.0);
        double achievedX = model.forward(sc, sc.toGameFacings(Angles.wrapAll(yaws)))
                .getPos(tick, JumpPhysicsInputs.Axis.X);
        assertEquals("an infeasible solve returns the furthest-reaching attempt the live tracker surfaced",
                progress.bestObjective(), achievedX, 1.0e-6);
    }

    @Test
    public void stopOnFeasibleReturnsAFeasibleSolution() {
        ExactJumpModel model = modelFor("j005");
        JumpSpec spec = singleJumpSpec();
        SolveCore.Budget budget = new SolveCore.Budget(16, 4500, 16, BucketAscentPolish.FAST);
        SolveProgress progress = new SolveProgress(true, true);

        double[] yaws = SolveCore.optimize(model, spec, budget, SIGMA, 0.0,
                new AtomicBoolean(false), null, 0L, false, progress);

        assertNotNull(yaws);
        assertTrue("stop-on-feasible must still return a byte-exact feasible solve", violation(model, spec, yaws) <= 0.0);
        assertTrue("a feasible candidate was reported into progress", progress.haveBest());
    }

    @Test
    public void cancelReturnsTheBestSoFarInsteadOfNull() {
        ExactJumpModel model = modelFor("j005");
        JumpSpec spec = singleJumpSpec();
        SolveCore.Budget budget = new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);

        double[] feasible = SolveCore.optimize(model, spec, budget, SIGMA, 0.0, new AtomicBoolean(false));
        assertNotNull(feasible);

        SolveProgress withBest = new SolveProgress(true, false);
        withBest.report(feasible, 1.0, 0.0, true);
        double[] kept = SolveCore.optimize(model, spec, budget, SIGMA, 0.0,
                new AtomicBoolean(true), null, 0L, false, withBest);
        assertArrayEquals("a cancel keeps the reported best feasible", feasible, kept, 0.0);

        SolveProgress empty = new SolveProgress(true, false);
        double[] none = SolveCore.optimize(model, spec, budget, SIGMA, 0.0,
                new AtomicBoolean(true), null, 0L, false, empty);
        assertNull("a cancel with no feasible found yet still returns null", none);
    }

    @Test
    public void stopOnFeasibleSurvivesRoundTrip() throws Exception {
        AngleSolverState s = new AngleSolverState();
        s.setStopOnFeasible(true);
        AngleSolverState loaded = roundTrip(s);
        assertTrue(loaded.isStopOnFeasible());

        AngleSolverState off = new AngleSolverState();
        assertFalse(off.isStopOnFeasible());
        assertFalse(roundTrip(off).isStopOnFeasible());
    }

    @Test
    public void resetClearsStopOnFeasible() {
        AngleSolverState s = new AngleSolverState();
        s.setStopOnFeasible(true);
        s.reset();
        assertFalse(s.isStopOnFeasible());
    }

    @Test
    public void oldSaveWithoutStopOnFeasibleDefaultsToOff() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0,0,0], \"vel\": [0,0,0], \"yaw\": 0.0 },\n" +
                "  \"rows\": [ { \"keys\": [\"W\"], \"yaw\": 0.0 } ],\n" +
                "  \"angleSolver\": { \"startTick\": 0, \"landingTick\": 1, \"axis\": \"X\", \"goal\": \"MAX\" }\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        AngleSolverState s = new AngleSolverState();
        s.setStopOnFeasible(true);
        SaveIO.applyAngleSolverTo(file, s);
        assertFalse("an absent flag loads as off", s.isStopOnFeasible());
    }

    private static AngleSolverState roundTrip(AngleSolverState s) throws Exception {
        Path dir = Files.createTempDirectory("pkc-stop-feasible-rt");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        InputData in = new InputData();
        in.getRows().add(new InputRow());
        Result<String> saved = SaveIO.save(store, "run", in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, s, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);
        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);
        return out;
    }
}
