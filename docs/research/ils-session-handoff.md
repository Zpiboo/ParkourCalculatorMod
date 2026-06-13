# ILS global-solve: session handoff (2026-06-13)

Pick-up point for the "can pure-Java beat Wolfram on j021" investigation. Companion to
`docs/research/ils-global-solve.md` (the findings) and the auto-memory `project_wolfram_j021_result`.
This doc adds the reproduction recipe, the reference implementation (the scratch experiments were
removed from the tree), and the next steps.

---

## TL;DR

- **Goal:** independently reach/beat the global optimum Wolfram's spatial B&B finds on `j021-rinav1-01`
  (MC 1.8.9, n=39, 4 jumps, 13 walls, maximize Z@39), in bundleable pure Java, no Wolfram seed.
- **Result: DONE.** Iterated Local Search reaches **1067.8636684 byte-exact feasible** (legal path),
  beating Wolfram (1067.8636334, +3.5e-5) and the shipped solver (1066.633, +1.2307).
- **Generalizes:** 5/6 fresh seeds beat Wolfram (ensemble reliable); improves every multi-jump fixture
  tested (gain scales with hardness), ties the easy ones.
- **Runtime:** exact-optimum ILS ~1.5-2 min on 12 cores for n=39; the **near-optimal quick solve (~0.002
  off, +1.23 over shipped) is ~3.3 s** for n=39 and **~30-50 s at n=100**.
- **The winning idea:** a structure-AGNOSTIC neighborhood ratchet. Every structured EXACT method
  (Lagrangian dual / active-set KKT / convex-relaxation seed / SLP) UNDERperformed it, defeated by the
  genuine LCvx-failure + one degenerate block on this instance.

---

## Key numbers

**j021 (n=39), byte-exact feasible Z@39:**

| approach | Z | notes |
|---|---|---|
| shipped multi-jump | 1066.633 | sets settled=true, skips race+polish |
| quick solve (race+polish, no ILS) | 1067.8619 | ~0.0018 off, **~3.3 s** |
| random multistart ceiling | ~1067.8634 | plateaus (-2.5e-4); converged worse basins |
| **ILS (this work)** | **1067.8636684** | **beats Wolfram**, byte-exact feasible |
| Wolfram optimize.wls (NMaximize/Cbc) | 1067.8636334 | not bundleable (needs Wolfram) |
| convex (SOCP) bound | 1067.8655 | loose; minimizer violates modulus (defect 0.083) |

**Seed generalization (6 fresh seeds, parallel ILS):** 5/6 crossed Wolfram (gaps +3e-6..+3.6e-5); the 6th
was -1.9e-5 (still climbing at cap); ensemble-best 1067.8636692. Single-run ~83% reliable; ensemble reliable.

**Problem generalization (ILS as polish stage on shipped feasible seed):** j013(n11) +5e-6 (ties), j016(n11)
+8e-5, j019(n11,3-jump) +2.4e-4, j021(n39,4-jump) +3.4e-3. Improvement always >= 0 (accepts only gains).

**Runtime scaling** (single climb = CMA@~50k + BucketAscent FAST; the ILS per-candidate cost):
n=11: 30-190 ms; n=28: 0.4 s; n=39: 1.2 s; n=176: 19 s; n=189: 22 s; n=353: 131 s. Per-climb ~O(n^2)
(BucketAscent pair-scans dominate). Quick-solve wall-clock ~O(n^2.5): n=39 3.3 s, n=176 150 s, **n=100 ~30-50 s
(extrapolated)**. ILS exact-optimum: ~2 min (n=39) -> ~15-25 min (n=100) -> impractical (n=353).

---

## Why the structured exact methods failed (so we don't re-try them)

- **CostateDualSolver (Lagrangian dual):** does NOT converge on j021 (pg-residual stalls ~0.5, hits cap),
  stuck at loose bound 1067.8898; recovery infeasible by ~2.9 blocks; ~1 truly degenerate block (|g_t|->0).
- **Active-set KKT enumeration:** 0/8192 active sets gave a feasible KKT point (degenerate block breaks
  clean costate alignment). Closed-form Jacobian (= the projector Hessian in CostateDualSolver.buildHessian)
  would improve Newton convergence but does NOT fix the degeneracy.
- **Convex-relaxation LP seed** (polygon-approx disks, commons-math3 Simplex): reproduces bound 1067.8657,
  but projecting onto spheres is byte-exact infeasible; refine -> 1067.8623.
- **SlpSolve (sequential LP):** feasibility-centered, LOWERS the objective from a good basin.
- Root cause: LCvx losslessness BREAKS for coupled multi-jump (Shapley-Folkman: dual differs from global in
  <= m blocks; here ~1 block + non-convergence). Research refs in ils-global-solve.md.

---

## Reproduction recipe

**Toolchain (this Windows box):** JDK 21 at `C:\Program Files\Java\jdk-21`; `wolframscript.exe` present;
system Python 3.12 has numpy 1.26.4 (+ `evalidate` installed) for the TAS-Wolfram comparison. TAS-Wolfram
repo at `C:\Users\benja\Desktop\Coding\02 Python\TAS-Wolfram`.

**Offline `:core`-only build dance** (the experiments are pure-Java core tests; loaders need
offline-unreachable plugins):
1. `settings.gradle`: comment out the 4 loader `include`s, keep `include 'core'`.
2. `core/build.gradle` test task: add `testLogging.showStandardStreams = true`.
3. Need `ProblemFixture.debugSpec(axis, goal)` (test harness) - it lives on branch
   `claude/wolfram-angle-solving-cq8b4e`. Restore via: `git checkout FETCH_HEAD -- core/src/test/.../harness/ProblemFixture.java`
   (after `git fetch origin claude/wolfram-angle-solving-cq8b4e`). It calls `engine.debugBuildSpec()` (on main).
4. Run: `JAVA_HOME=<jdk21> ./gradlew :core:test --tests '*<Experiment>*' --rerun-tasks --console=plain`.
5. REVERT settings.gradle + core/build.gradle + ProblemFixture afterward.

**Scratch artifacts** (gitignored, may still be on disk): `build/wolfram/` has the j021 Wolfram dump/run
scripts (`j021_run.wls`, `j021_mathematica_input.json`, `j021_exact_input.json`), angle exports, and all
experiment raw logs (`ils_raw.txt`, `pils*_raw.txt`, `quick_raw.txt`, `nprobe_raw.txt`, etc.).

**The fixtures:** `core/src/test/resources/problems/solve/` + `captures/` (j001..j023). j021-rinav1-01 is the
test case. n by fixture: most n=11, j007=28, j021=39, j003=176, j002=189, j001=353.

---

## Reference implementation (the winning ILS, as a portable static method)

The scratch test files were removed. Here is the core algorithm condensed for re-use; drop into a solver
class (depends only on existing core types). Wire it as a polish stage AFTER the existing solve.

```java
// Inputs: model (ExactJumpModel), spec (JumpSpec), and feasibleSeeds = byte-exact feasible facing
// vectors from the shipped cascade (ClosedFormSolve -> SlpSolve -> CMA race). Returns best feasible yaws.
// sign = (obj.sense == MAX) ? +1 : -1; score(y) = feasible ? sign*forward(y).getPos(objTick,axis) : -inf.

static double[] ilsPolish(ExactJumpModel model, JumpSpec spec, List<double[]> feasibleSeeds,
                          ExecutorService pool, int batch, int roundCap, long rngSeed, AtomicBoolean cancel) {
    JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
    JumpPhysicsInputs sc = spec.asScenario();
    Objective obj = spec.objective; int n = sc.numTicks;
    double sign = obj.sense == Objective.Sense.MAX ? 1.0 : -1.0;
    Random rng = new Random(rngSeed);

    // best feasible seed
    double[] best = null; double bestScore = Double.NEGATIVE_INFINITY;
    for (double[] s : feasibleSeeds) { double v = score(model, sc, comp, obj, sign, s); if (v > bestScore) { bestScore = v; best = s; } }
    if (best == null) return null; // no feasible seed -> caller falls back

    // best-of-batch ratchet
    for (int r = 0; r < roundCap; r++) {
        List<Future<double[]>> futs = new ArrayList<>();
        for (int b = 0; b < batch; b++) {
            double[] c = best.clone();
            int kicks = 3 + rng.nextInt(13);            // perturb 3..15 ticks
            double mag = 3 + rng.nextDouble() * 50;      // by +/- up to ~50 deg
            for (int q = 0; q < kicks; q++) c[rng.nextInt(n)] += (rng.nextDouble()*2 - 1) * mag;
            final double[] fc = c;
            futs.add(pool.submit(() -> climb(model, spec, comp, sc, obj, sign, fc, cancel)));
        }
        boolean improved = false;
        for (Future<double[]> f : futs) {
            double[] y = f.get(); double v = score(model, sc, comp, obj, sign, y);
            if (v > bestScore) { bestScore = v; best = y; improved = true; }
        }
        // precise lattice polish only when close (the FAST per-round polish stalls at the last ~1e-5)
        if (improved) {
            double[] pol = BucketAscentPolish.polish(model, spec, Angles.wrapAll(best), BucketAscentPolish.THOROUGH, cancel);
            double pv = score(model, sc, comp, obj, sign, pol);
            if (pv > bestScore) { bestScore = pv; best = pol; }
        }
    }
    return best;
}

// one climb (the per-candidate work): a tight CMA solve + a FAST bucket polish, keep if it improves.
static double[] climb(ExactJumpModel model, JumpSpec spec, JumpConstraintCompiler.Compiled comp,
                      JumpPhysicsInputs sc, Objective obj, double sign, double[] start, AtomicBoolean cancel) {
    double[] cur = start;
    SolverRunResult r = new CmaesJumpHarness(1e6, 1e6, 4.0, 80000).solve(model, spec, cur, cancel);
    if (feasible(r) && score(model,sc,comp,obj,sign,r.yawAbsDeg) > score(model,sc,comp,obj,sign,cur)) cur = r.yawAbsDeg.clone();
    double[] pol = BucketAscentPolish.polish(model, spec, Angles.wrapAll(cur), BucketAscentPolish.FAST, cancel);
    return score(model,sc,comp,obj,sign,pol) > score(model,sc,comp,obj,sign,cur) ? pol : cur;
}
```

Tuning notes learned the hard way:
- **Batch breadth matters more than per-task speed**: batch = #logical cores (best-of-12 crosses in ~30
  rounds; best-of-6 stalls for 300). Accept the per-task contention slowdown.
- **Lighter climbs backfire**: a shallower per-iteration climb reaches the basin fast but stalls on the
  final precision and needs ~3x more rounds. Keep the climb strong; do the precise BucketAscent THOROUGH
  only on rounds that improve.
- **Ensemble for reliability**: a single run is ~83% likely to beat Wolfram; run 2-4 RNG seeds keep-best.
- **Seeds are mandatory**: ILS needs a feasible start; on tight fixtures only the engine's full feasibility
  cascade finds one (standalone random/CF/SLP failed on j022). Always seed from the shipped solve.

---

## Next steps (productization)

1. **Quick near-optimal as the default best-effort** (~3 s @ n=39, ~30-50 s @ n=100, +1.23 over shipped):
   for multi-jump, stop bailing to `settled=true` - run the existing CMA race + one BucketAscent. Captures
   essentially all the gain over shipped at interactive-ish cost. Lowest-risk win; uses existing code.
2. **Exact-optimum ILS as a new offline "Exhaustive" effort tier** (~1.5-2 min @ n=39): add `ilsPolish`
   (above) + a small ensemble, AFTER the existing solve in `AngleSolverEngine`'s multi-jump path, seeded
   from its feasible result. Never regresses (accepts only improvements). Matches/beats an external global
   solver in bundleable pure Java.
3. **Long routes (n > ~150):** do NOT run whole-span ILS (hour-scale at n>=180, impractical at n=353). Use
   the existing segment decomposition (`FreeSpaceDecomposition` / `LongRunSolver`) and apply the
   quick-solve/ILS per segment. First micro-opt for larger n: restrict BucketAscent block-2 pairs to
   wall-relevant ticks (kills the O(n^2)).
4. **Optional:** regression-pin a `solve/` fixture's improved objective once a tier is wired; re-add the
   ILS reference as a (non-default, tagged-slow) test rather than leaving it only in this doc.

---

## Pointers

- Findings + algorithm + data: `docs/research/ils-global-solve.md`
- Auto-memory: `project_wolfram_j021_result` (loaded each session)
- Original Wolfram investigation that started this: `wolfram-session-handoff.md` on branch
  `claude/wolfram-angle-solving-cq8b4e` (also has WolframDumpTest + the debugSpec harness)
- TAS-Wolfram repo (the external comparison): `C:\Users\benja\Desktop\Coding\02 Python\TAS-Wolfram`
- Solver internals reused: `CmaesJumpHarness`, `BucketAscentPolish`, `ClosedFormSolve`, `SlpSolve`,
  `CostateDualSolver`, `ExactJumpModel`, `JumpConstraintCompiler` (all in core/.../anglesolver/solver/)
