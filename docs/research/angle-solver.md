# The angle solver: design record, verified results, and open problems

This document is the single research record for the angle-solver subsystem. It consolidates the earlier research files (the block-solving handoff, the long-jump handoff and findings, the constraint-derivation brief and findings, and the code audit) into one place, preserving every durable result while omitting session-specific scaffolding. It was last consolidated on 2026-06-11 against `main`, shortly after the angle-optimizer merge (#122).

Contents:
1. [Problem structure](#1-problem-structure)
2. [The shipped solver pipeline](#2-the-shipped-solver-pipeline)
3. [Verified properties](#3-verified-properties)
4. [Negative results and pitfalls](#4-negative-results-and-pitfalls)
5. [Performance](#5-performance)
6. [Block-constraint derivation (DERIVE)](#6-block-constraint-derivation-derive)
7. [Open directions](#7-open-directions)
8. [Headless validation and build notes](#8-headless-validation-and-build-notes)
9. [Citations](#9-citations)

---

## 1. Problem structure

Minecraft (MC) movement is a discrete-time dynamical system advancing at 20 Hz. The keyboard inputs (keys, jump, sprint, sneak, potion effects, and the ground or air state of each tick) are fixed and given, so the only free variables are the per-tick yaw angles during air ticks. The yaw rotates the thrust vector applied in that tick and is therefore the entire steering authority. Vertical motion is independent of yaw, since it consists of gravity and the jump impulse alone. The per-tick feet height `y(t)` is consequently known exactly in advance, and only the horizontal `(X, Z)` trajectory depends on the decision variables.

The per-tick horizontal update is a byte-exact port of the 1.8.9 `moveFlying` and friction chain: $v^{+} = v + R(\theta)a$, followed by $p \leftarrow p + v^{+}$ and $v \leftarrow f v^{+}$, together with a small-velocity momentum cutoff and a jump impulse on grounded jump ticks. The player hitbox is 0.6 blocks wide (half-width 0.3) and 1.8 blocks tall (1.5 when sneaking). Blocks are arbitrary axis-aligned boxes such as slabs, skulls, and fences, captured as real hitboxes.

### 1.1 The horizontal dynamics are affine in constant-modulus inputs

Define the per-tick steering vector $u_t = R(\theta_t)\hat{a}_t$ with $\|u_t\| = m_t$. The modulus is constant and only the direction is free. Position at any tick is then affine in the $u_t$:

$$p_k = p_0 + \sum_{s \lt k} C(s,k)\,u_s, \qquad C(s,k) = (S_k - S_s)/\Phi_s$$

Here $\Phi_s$ denotes the prefix product of the per-tick friction factors and $S_k$ the corresponding prefix sum (named `fPre` and `sPre` in the code). The map $u \mapsto p$ is linear, and the only nonconvexity of the unconstrained problem is the modulus sphere. This is exactly the structure addressed by lossless convexification (LCvx) of fixed-thrust trajectory problems (Acikmese and Blackmore). The inner maximization $\max_{\|u\|=m} g \cdot u = m\|g\|$ is tight, so the Lagrangian dual has zero gap and admits the closed-form costate recovery $u^{*}_t = m_t\,g_t/\|g_t\|$. This property is what makes the microsecond fast path possible, and any future formulation that preserves the affine map and adds only convex cuts inherits the same machinery.

The boundary of the theorem matters in practice. LCvx covers control constraints, together with convex state constraints that are active only at isolated instants. A keep-out zone is a non-convex state constraint active over an interval and therefore falls outside the theorem. Collision can consequently not be folded into the dual without losing the zero gap, whereas a fixed convex cell face can, since it is just another linear wall. This single fact dictates the constraint-derivation architecture of section 6. A further caveat is that discrete-time LCvx is provably lossy: the relaxed solution can violate the modulus constraint at some temporal grid points, and the number of such points is bounded only under additional conditions (Luo et al. 2024, 2025). For this reason every closed-form result is gated by byte-exact re-certification instead of trusting losslessness tick by tick.

### 1.2 The constraint alphabet

The solver consumes per-tick scalar or range bounds on `X`, `Z`, and `F` (facing), velocity bounds expressed as position differences ($\Delta X_t = X_t - X_{t-1}$), and exactly one MAX or MIN objective on one axis at one tick. Position walls are linear in $u$. Facing walls are not, and currently force the CMA-ES fallback (section 7, item 2). One alphabet extension is known to be worth adding when the need arises: oblique half-spaces $a^T p_t \le b$ are supported natively by the affine map and are necessary to wrap diagonal corridors or the sheared swept volume of section 1.3 tightly.

### 1.3 The swept collision model

Collision is not an endpoint check. MC's `Entity.moveEntity` resolves the intended displacement one axis at a time, in the order Y, then X, then Z, offsetting the hitbox after each axis. `SweptCollision` is the byte-exact port. Several consequences follow.

First, collision is a property of the swept segment from $p_t$ to $p_{t+1}$, and endpoint-only checks silently pass corner clips. This caused real in-game failures. Second, the resolution is asymmetric. The X clamp tests the player's start-of-tick Z, so crossing a block in X requires being Z-clear on the previous tick, while the Z clamp tests the already-moved X, so X may be cleared in the same tick as a Z crossing. The effective keep-out region is therefore a sheared parallelogram volume rather than a symmetric Minkowski box. In practice it is encoded as a per-tick keep-out half-space that is active on the in-band tick and on the following tick (the "+1 dilation"). Third, temporal reachability couples the constraints over time. A condition such as "be east of the skull" may be unreachable before some tick `t*` and must hold before "cross south of the wall" can. Reactive, greedy, or per-tick-local fixing thrashes on such chains and does not converge (section 4). Fourth, no safety margin is affordable. The canonical hard jump clears by roughly `6.6e-7` blocks, and padding walls by even `1e-3` makes such jumps infeasible, so approximations must be tight, exact, or adaptively tightened rather than conservatively inflated. Finally, from 1.14 onward the X/Z resolution order additionally switches on `sign(|v_x| - |v_z|)`, which becomes relevant when porting beyond the 1.8.9 and 1.12 semantics.

`ExactJumpModel`, the collision-free byte-exact forward model, equals the real `SimulatorEntity` for any strictly-outside path, and `SweptCollision` is byte-exact MC collision. Together they make every candidate algorithm fully self-validatable headlessly, without a human in the loop or an in-game run (section 8).

---

## 2. The shipped solver pipeline

`AngleSolverEngine.runJob` (worker thread) routes a solve as follows.

| stage | class | role |
|---|---|---|
| fast path (single jump) | `ClosedFormSolve` -> `CostateDualSolver` | The LCvx closed-form dual on `JumpLinearModel` finds globally optimal facings in roughly 10 to 140 us per solve. A margin ladder with byte-exact re-certification returns null rather than an uncertified result. |
| direction retry | `runJob` | Feasibility is objective-independent, but the closed form certifies only the objective's optimal vertex, so the other Solve-For directions are tried before giving up. |
| multi-jump span | `LongRunSolver` | Receding-horizon decomposition (section 2.1). The engine routes multi-jump spans there directly, because the monolithic dual was measured not to converge across dozens of jumps. |
| duality-gap closer | `SlpSolve` | Trust-region sequential LP on the facings, judging every iterate on the byte-exact forward's wall slacks with the closed-form friction coupling as the exact Jacobian. Invoked per window, only after the margin ladder failed in every direction (section 2.1). |
| fallback | `SolveCore` + `CmaesJumpHarness` | Multistart CMA-ES on the byte-exact model, followed by `BucketAscentPolish` (a strictly feasible compass ascent). The met/total count is reported faithfully. |
| smoothing | `SmoothingPolish` | Smooths the free ticks of underdetermined solves toward their neighbors. Internal gates keep byte-exact feasibility and the achieved objective. |

Every path reports the facing chain that Apply actually realizes, namely `toGameFacings(wrapAll(yaws))`, so the reported trajectory is bit-for-bit the in-game one. No path can report a false success: the closed form re-certifies byte-exactly, the SLP returns null unless its final byte-exact violation meets the tolerance, `LongRunSolver` re-verifies the full concatenation, `BlockSolver.ok()` requires a swept-clean and landed path (the dormant block-solving path, section 2.2), and the CMA-ES path reports its met/total count faithfully.

### 2.1 The long-run solver as a receding horizon

Long multi-jump runs solve from scratch in about 0.1 s, using only the start state, the physics structure, and the constraints. The reference problem is j001 (353 ticks, 30 jumps, 81 constraints, a recapture of the former `deserthard-v12` save). No recorded trajectory and no per-fixture tuning are involved. The design follows from measurement.

The convex dual solves a single jump to global optimality in microseconds and keeps converging for windows of up to about ten jumps, but not across a full run. On the full span the dual hits its iteration cap 14 to 88 blocks away from feasibility, and more iterations do not help: raising the cap from 100 to 1000 to 8000 moved the error from 14.4 to 17.7 to 15.5 blocks at a cost of 146 s. CMA-ES at several hundred dimensions (one yaw per tick) is equally ineffective.

The solver therefore slides a window of ten jumps over the run. Each window is solved to global optimality by the dual, trying alternate Solve-For directions since feasibility is objective-independent. The first three jumps of the window are committed, their exact byte-exact exit state (position, velocity, and yaw) is chained into the next window's seed, and the window slides on. The window overlap acts as lookahead: a committed jump's exit is, by construction, the entry of a feasible continuation of (window - commit) jumps, so the commitment cannot render the following jumps infeasible. Exactly this coupling defeats a greedy jump-by-jump chain. The construction is multiple shooting with the per-jump dual as the inner solver, in which the overlap plays the role of backward-reachability pruning of feasible entries.

The measured coupling horizon is about five jumps. On j001 a lookahead of four or less fails while five or more solves, and the greedy variant fails outright. The shipped configuration commits three jumps, which leaves a lookahead of seven and thus a margin above the measured horizon. If a commitment gets the chain stuck, the solver retries with a commitment of one. A window-size ladder of 10 -> 7 -> ... -> 1 covers cases where a full ten-jump window does not solve (sizes that clamp to an already-tried window near the run's end are skipped), and a byte-exact verification of the full concatenated path provides the final check. Robustness comes from every window being solved by the convex dual, with the SLP of section 2.1.1 as the byte-exactly gated closer where the dual carries a gap: there are no local optima, no initial guesses, and no tuning parameters, so the result is invariant to the incidental details that break local search.

Two failed approaches shaped this design. Warm-starting from the editor's recorded trajectory reached feasibility only from starts within about 0.5 blocks, that is, when the answer was already known, and a variant of the same problem shifted by one tick exposed this dependence. A monolithic local search over all decision variables at once (waypoint guess, global Gauss-Newton, then polish) landed in different basins or plateaus depending on incidental details, and the same one-tick shift made it stall one sine bucket short of feasibility.

Should a future run exhibit a longer coupling horizon than the window provides, the principled upgrade is explicit multiple shooting with backward-reachable feasible-entry boxes as seam constraints. The simple receding horizon has not needed it so far.

#### 2.1.1 The duality gap on cross-seam-coupled windows, and the SLP closer

The dual is not gap-free on every window. The zero-gap argument requires the recovery $u^{*}_t = m_t\,g_t/\|g_t\|$ to be the unique inner maximizer, and a multi-jump window whose walls couple across seams can break that: on j021 (four jumps, 13 constraints; X must cross a wall between consecutive ticks and then hold a band about 0.04 wide) the dual converges to a 5e-9 projected-gradient residual while the recovered trajectory remains 0.34 blocks infeasible, at every margin rung and regardless of iteration budget (verified at 20 000 iterations). Before the closer existed, this degraded the window ladder into greedy single-jump commits whose seam states doomed the later jumps, and the engine fell into the CMA-ES multistart: FAST and BALANCED failed outright and THOROUGH needed about 176 s.

`SlpSolve` closes such gaps primally. The window is still exactly linear in the per-tick inputs, so it runs a trust-region sequential LP on the facings, seeded from the dual recovery at margin 0 (globally informed even when infeasible), with two exactness properties: every iterate is judged on the byte-exact forward's wall slacks, so float drift and the sine-bucket lattice are inside the loop rather than margin-patched afterwards, and the LP Jacobian is the closed-form friction coupling rotated by 90 degrees ($du/d\theta = i\,u$), no finite differences. Phase 1 minimizes the worst exact slack until at least 1e-6 inside (about 16 LPs on j021); phase 2 improves the real objective while staying strictly inside. It is budgeted at 60 LPs (about 0.2 s worst case), runs once per window only after the margin ladder failed in every direction (its phase 1 is objective-independent, so the other directions would fail identically), and cannot report a false success. j021 now solves 13 of 13 at every effort in about 150 ms, where the old THOROUGH near-solution also sat about 1 block short of the certified objective.

#### 2.1.2 Centered lead-in windows

A lead-in window's objective is only a surrogate ("any feasible"), so hugging walls there is pure liability: the margin-0 vertex quantizes fragilely (burning ladder rungs) and commits extreme seam states that can doom the next jumps. Lead-ins therefore solve centered. The closed form tries the largest margin first (`optimizeRobust`; the dual's vertex hugs the margin-tightened walls, so the first margin that certifies is the realized clearance on every active wall), and the SLP fallback deepens phase-1 clearance toward 0.02 and skips the hugging phase (`optimizeCentered`). Only the last window, which carries the real objective, still hugs. This dropped j001 from about 133 ms to about 95 ms, since lead-ins certify on the first robust rung instead of climbing the ascending ladder. The single-jump fast path and direct `ClosedFormSolve.optimize` callers are untouched.

### 2.2 Status of block solving (DERIVE) on `main`

`BlockSolver` (the forced-crossing-tick homotopy planner of section 6.1), `FreeSpaceDecomposition`, and the engine glue (`AngleSolverEngine.solveFromBlocks`) are present in `main` but currently dormant. No UI on `main` invokes them and no committed test covers them. The block-picker UI (Forge 1.8.9 keybinds, the in-world overlay, and the `BlockPicker` port) exists only on the unmerged `features/angle-optimizer` branch. The derive test harness together with the j154 fixtures was deleted at that branch's `65701fc` and is recoverable only from its git history (`65701fc~1`). When validated there, the planner matched the hand-solved j154 path to about 1e-4 and generalized across every obstacle subset of the j154 arc. The headless validation recipe of section 8.2 is the path to re-validating that work when it lands.

---

## 3. Verified properties

The code audit of 2026-06-10 verified the following claims by reading the float chains against the MC 1.8.9 semantics they port, by re-deriving the mathematics, or by measurement, with a baseline of 35 of 35 tests green. They are recorded here so that they are not re-derived.

- The linear-model coefficients `C(s,k) = (S[k] - S[s])/fPre[s]` follow exactly from unrolling `v_{t+1} = (v_t + u_t) * f4_t` and are numerically stable by construction, since the coefficient is bounded by $\sum 0.91^k \approx 11$ and therefore cannot grow exponentially even over hundreds of ticks.
- The zero-duality-gap argument is correct generically. The degenerate case of a vanishing costate is converted into a fallback by the objective-direction default combined with byte-exact re-certification. Generically is the operative word: a multi-jump window with cross-seam wall coupling can carry a genuine duality gap (section 2.1.1, measured on j021 after this audit), which is why the SLP closer exists.
- `McSineTable` generation and lookup are bit-identical to MC, including expression order. Both distinct yaw-to-rad casts (`0.017453292F` in `jump()` and `yaw*(float)PI/180F` in `moveFlying`) are ported.
- The per-axis (1.8 and 1.12) versus combined-XZ (after 1.12, applied to the 1.21 loader) inertia selection is correct. The threshold applies to the post-friction carry at the top of the tick, the jump impulse fires only when grounded, and strafe is disabled on grounded jump ticks in both the exact and the linear model.
- `toGameFacings` is the bit-exact model of Apply for the normal wrapped-absolute path, and locked rows resynchronize the float chain in both.
- The `LongRunSolver` window slicing is sound. Seam constraints are enforced in the window that commits them and re-checked as trivial tick-0 constraints in the next, velocity pairs straddling a commit seam stay enforced, and the committed coverage is gapless.
- The dual's convergence machinery (the `U_TOL` stationarity test, the free-set selection, and the Cholesky factorization of the damped positive-semidefinite Hessian with adaptive Levenberg damping) is sound.
- Determinism holds. Seeds are fixed everywhere, parallelStream results are order-preserving, and the worker handoff publishes through a single volatile field with the cancel token checked before publishing.

Five audit findings were fixed the same day and are recorded for traceability. The long-run result is now verified and reported on the facing chain Apply realizes, where previously the seam-chained facings were verified, a latent one-ulp re-rounding risk. The result panel now evaluates F-mode constraints on the same facing array the solver scored, where previously the two differed by a small epsilon. EQ constraints are now treated internally as ranges of plus or minus the met tolerance, where previously they silently disqualified the fast path and the polish. The margin ladder now stops at the first unbounded dual, since infeasibility is monotone along the ladder. The trivial-constraint feasibility check is now exact, where previously a tick-0 constraint violated by 1e-9 exhausted the full ladder and the CMA-ES fallback before the unavoidable negative answer.

Two magic numbers in the dual are deliberate and documented rather than changed. `DIVERGE_PGRES = 4.0` is the stall-bail floor. Removing it broke j020's closed-form solve and made j001 slower, so the gate protects in both directions. Should it ever misfire, the remedy is to make it relative to the initial residual. `LAMBDA_CAP = 1e9` declares divergence in absolute multiplier units and is adequate at current problem scales.

Three code constructs look duplicated but are deliberate and should not be unified: `wrap` versus `wrapDelta`, the ladder margin applied inside the dual versus `compileWall`'s margin parameter, and the result panel's `MET_TOL` versus the solver's `FEAS_TOL`.

---

## 4. Negative results and pitfalls

The following results are negative and are recorded to prevent repetition. The first five are general, and the rest are specific to block solving and constraint derivation.

1. Velocity must never be pinned at segment seams. Matching seam velocity to a reference within a tolerance creates a precision floor of about 2e-3 that the byte-exact solve cannot reach, and it accumulates drift: a 1e-2 band accumulated to 0.34 blocks by the fifth jump. The correct approach is to chain the exact state and never to constrain velocity for stitching.
2. Row yaws are per-tick deltas, while the recorded `state.yaw` is absolute. The absolute facing used by the move at tick `t` is `getState(t+1).yaw`, and the +1 offset matters. Being off by one produces over 18 blocks of divergence, and feeding deltas as absolutes produces over 100.
3. The round trip from absolute facing to delta to `toGameFacings` is not bit-exact over long runs. It drifts by about 0.5 blocks over the full span of the reference run when reproducing recorded facings, which ruled out the approach of reproducing the recorded run segment by segment. The issue is moot for the shipped solver, which solves from scratch in one self-consistent model, but it still applies to anything that replays recorded yaw data through the model.
4. CMA-ES is ineffective at high dimension, regardless of warm starts or budget, and failed at several hundred dimensions in every configuration. Its value is limited to the low-dimensional fallback.
5. When a `JumpSpec` is built only to call `maxViolation`, its objective tick must still lie within the segment. Nothing validates the tick at construction or compilation, and `maxViolation` ignores the objective, but any later consumer of the objective (the linear model, the objective read-out) indexes per-tick arrays with it.
6. Endpoint-only collision checks are wrong (section 1.3) and were the actual cause of three in-game failures.
7. Safety margins on derived walls are not affordable (section 1.3). Padding an edge by even 1e-3 makes extremely tight jumps infeasible.
8. Deriving walls from a recorded route fails, because the route does not exist until after solving. A bad attempt yields useless walls, for example a north-of-obstacle wall on the landing tick, which renders the spec infeasible.
9. Reactive nearest-exit and per-tick side flipping thrash, since the homotopy becomes inconsistent, and reactive cutting planes driven by the swept oracle do not converge on lookahead-coupled corner wraps, even with a depth-first search over pass sides and delayed-crossing options. The convergent variant of the idea is the principled SCP shell of section 6.2, which differs by a trust region, simultaneous multi-tick cuts, homotopy-aware initialization, and an L1 exact penalty.

---

## 5. Performance

The remaining speed headroom was deliberately deferred on 2026-06-10. About 100 ms for a from-scratch 353-tick, 30-jump solve is interactive for an off-thread one-shot action (j001 takes roughly 95 ms since the centered lead-ins of section 2.1.2, and j002/j003 solve all four directions in 15 to 36 ms), and single jumps sit at the structural floor of one dual solve plus certification, roughly 10 to 140 us. The decision is to revisit when one of three conditions becomes true. The first is that solving becomes an inner loop, for example through block derivation, automatic routing, or batch re-solving that issues many solves per interaction. In that case cross-window warm starts (section 5.1) are the item to build. The second is that runs grow several-fold past about 350 ticks, scaling 150 ms toward seconds. The third is that an F (facing) constraint is used on a multi-jump run. That is a capability gap rather than a speed problem, and section 7, item 2 describes the fix to build the moment the need appears.

The time profile on j001 (measured before the centered lead-ins) was as follows. Eight window solves account for essentially all of the 150 ms. The margin ladder's small rungs exhaust the `MAX_ITER = 100` budget without certifying, and no window certifies below a margin of 6e-4, because sine-bucket quantization noise accumulates past those margins in windows of over 100 ticks. Of roughly 2200 dual iterations, only about 160 belong to certifying rungs. The centered lead-ins (section 2.1.2) cut exactly this waste, since a lead-in now certifies on the first robust rung, which is what moved j001 to about 95 ms.

Three prototyped speedups were measured and rejected and should not be retried in the same form. In the table, a negative percentage denotes a speedup relative to the baseline and a positive percentage a slowdown.

| prototype | j001 | j002/j003 | verdict |
|---|---|---|---|
| lead-in windows start the ladder at 6e-4, full ladder on failure | -28 % | some directions +60 % | no net improvement |
| stall-bail at any residual (dropping the `DIVERGE_PGRES` floor) | +33 % | mixed | breaks j020, rejected |
| per-run rung memory (starting at the last certifying rung) | +45 % | worse | cold mid-ladder starts are expensive and the ratchet over-tightens |

The common negative result is that skipping rungs is counterproductive: the warm-start chain provides a measurable benefit. A cold dual solve at any rung costs about 100 iterations on degenerate windows, so skipping rungs trades cheap warm iterations for expensive cold ones, and which rung profile is faster is problem-dependent. The shipped centered lead-ins are not a retry of these prototypes: lead-ins run a separate largest-first ladder whose goal is clearance rather than objective, while the real-objective window keeps the full ascending ladder and its warm chain.

### 5.1 Cross-window warm starts and smaller improvements

Consecutive windows share about 70 % of their walls, and wall identity is stable (name plus absolute tick). Seeding window k+1's dual at each rung from window k's multipliers would preserve the warm chain across windows instead of paying about 100 cold iterations per window and rung. The estimate is a speedup of 2.5 to 5x on long runs, which would bring j001 to roughly 30 to 60 ms. It requires an optional lambda-by-wall-name seed on `CostateDualSolver`.

Smaller known improvements follow, in descending value. `ExactJumpModel.stepRange` is exercised only through `forward`, which always starts at tick 0, so its incremental form is unused. It recomputes only the tail `[from, n)` bit-identically, which at least halves the dominant cost of the single-tick perturbation loops in `BucketAscentPolish` and `CmaesJumpHarness.polish` on long runs, and it should be wired up as part of any Phase-2 global-ascent work. Capping `buildHessian`'s inner loop at each wall's last coupled tick roughly halves the dominant per-iteration cost. `JumpLinearModel` and the compiled walls can be reused across the four direction retries, since only the objective vectors change. Thread-local scratch on the CMA-ES path is worthwhile only if that path's latency ever matters. A performance canary fixture with a `maxSolveMs` of about 2 s would catch a silent return of the 2.1 s behavior that preceded the receding horizon without introducing intermittent CI failures.

---

## 6. Block-constraint derivation (DERIVE)

The overall problem decomposes into two parts. SOLVE maps constraints and an objective to yaws and is solved, so the pipeline of section 2 can be treated as an oracle. DERIVE maps block hitboxes to a per-tick constraint set that admits a swept-clean landing, and it remains the open problem.

### 6.1 The current planner (`BlockSolver`) and the canonical hard case

The current method is a forced-crossing-tick geometric homotopy planner, headless-validated on the feature branch and dormant on `main` (section 2.2). The travel axis is the larger seed-to-land separation, and the perpendicular axis carries the keep-outs. Each obstacle is wrapped on the perpendicular side nearest the launch corridor. Because the swept X-clamp tests the start-of-tick travel coordinate, a forced crossing tick k\* pins the travel coordinate just past the tightest obstacle's exit edge, which makes the descent rate, and hence the keep-out window, deterministic rather than solver-dependent. The value of k\* is enumerated over a small window, and the keep-outs receive the +1 dilation of section 1.3. The objective is swept as well: the pad edge often sits at the reachability frontier, so the toward-pad objective lands on it while the away objective overshoots by about 1e-6, and all four endpoint objectives are tried. Every candidate is verified with `SweptCollision` and the landing footprint, and `Result.ok()` is never reported for a clipping or missing path.

The canonical hard case is j154, hand-solved in-game. A two-block wall stands between start and land, with a skull just east of it. The player can pass neither over nor through, so the path must wrap the southeast corner: stay north of the head early, drift east by mid-flight, and cross south late. These constraints interlock with lookahead, since east must precede south and an early south crossing is unreachable. This is the case that defeats every reactive approach.

Three limitations motivated further research. The heuristics (side selection, the k\* window, the +1 dilation) are bespoke and do not obviously generalize to dense clusters or three-dimensional wraps. The outer enumeration offers no completeness or optimality structure. Collision lives in two places, an approximate constraint set and an exact verifier, which invites false-pass and false-fail defects at the seams. Four alternatives were tried in a structured search and none beat this planner: homotopy-class enumeration, reachability corridors, reactive forced exits, and fixed descent schedules.

### 6.2 Research synthesis

A literature pass across five areas (lossless convexification, safe corridors and MILP, graphs of convex sets, SCP/SDF/MPCC, and swept CCD with homotopy and kinodynamic planning) converged with no surviving contradictions. One caveat applies: some quantitative figures rest on search summaries rather than full-text reads of the primary PDFs, so exact theorem numbers should be re-verified before any argument is built on them.

The organizing principle is the theorem boundary of section 1.1. Its admissible workaround, holding the per-tick keep-out as a fixed convex cell outside the inner solve, is simultaneously the LCvx-compatible form, the safe-corridor face set, and a GCS node. The resulting architecture is the following.

> GCS (or GCS\*) serves as the outer corridor selector and homotopy enumerator over cheap axis-aligned free-space cells, wrapping the existing closed-form dual as the inner per-cell solver, gated by the exact swept oracle. GCS supplies the discrete structure: completeness, tight corridor selection, and implicit homotopy enumeration. LCvx supplies what GCS cannot, namely the certified fixed-modulus inner solve, since the modulus sphere is a non-convex equality that no GCS node can represent. The oracle supplies what neither can, the exact asymmetric swept truth. GCS optimality is empirical rather than a priori: the relaxation is a lower bound and the rounded path an upper bound, and the gap is observed to be tight rather than theoretically bounded.

The supporting findings are each independently useful.

- IRIS and SFC region inflation should be skipped for AABB worlds. The free space is the complement of axis-aligned boxes, so its convex cells are axis-aligned rectangles obtainable by a coordinate sweep with no SDP involved, and ellipsoid inflation degenerates at gaps near 1e-6 in any case. `FreeSpaceDecomposition` implements this decomposition together with its adjacency graph, and the corridor selection layer on top is the missing half.
- For homotopy enumeration the state of the art is the h-signature (Bhattacharya, Likhachev, and Kumar), which augments graph search with a partial signature so that a single search enumerates the K distinct classes. With affine dynamics the per-tick forward-reachable sets are disks of radius `sum(gains * m_t)`, so the temporal admissibility of a class or cell is a closed-form radius test.
- Big-M MILP encodings are dominated by the perspective (convex-hull) formulation, and GCS is that formulation applied to corridor selection.
- SCP with signed distances is the only collision-in-solver paradigm that preserves the fast convex inner solve, but every published collision model inflates through `d_safe`, epsilon, or alpha margins, which the no-margin requirement of section 1.3 forbids. The actionable recipe is an SCP shell (trust region plus L1 exact penalty) that linearizes our own exact asymmetric swept signed distance with zero margin and is gated by the exact oracle. It differs from the failed reactive cutting plane (section 4, item 9) in exactly the parts that secure convergence.
- MPCC and contact-implicit methods are the worst fit, since LICQ and MFCQ fail at every feasible point and exactness holds only in a limit that permits interpenetration. Conservative-inclusion CCD libraries are safe but round toward false positives and require a positive standoff, which is incompatible with zero-clearance ground truth. Since segment-versus-AABB tests are already exact and closed-form, no CCD library is needed.
- SST\* (kinodynamic sampling against the black-box clamp) is the completeness backstop when no convex cell captures the sweep, and it can be tuned offline via the headless harness.

### 6.3 Target architecture

```
BLOCKS (AABBs)
  [A] plane-sweep free-space decomposition -> axis-aligned cells      (DONE: FreeSpaceDecomposition)
  [B] GCS/GCS* over cells, edges pruned by reachability disks,        (NEW: the outer layer)
      h-signature of the chosen path = homotopy class (replaces k*)
  [C] selected corridor -> cell faces as constraints for the          (REUSE: ClosedFormSolve,
      existing closed-form dual (+ oblique half-spaces, section 1.2)   minimal alphabet extension)
  [D] tight cell-boundary riding: SCP shell, zero margin              (NEW: only if needed)
  [E] GATE: exact swept oracle + landing footprint                    (REUSE: SweptCollision)
  [F] fallback: SST* against the black-box clamp                      (NEW: completeness backstop)
```

This architecture deletes from `BlockSolver` the bespoke side selection, the k\* enumeration window, the +1-dilation heuristic, and the duplication of collision across two places. Collision then lives in exactly one place, the gate.

---

## 7. Open directions

The following items are ranked by value per effort, together with their triggers.

1. Phase-2 long-run objective polish, the known remaining work. `LongRunSolver` returns a feasible run, but only the final window optimizes the real objective. The options are to re-solve the final k windows with progressively earlier seams under the committed prefix, where each re-solve is one window dual, or to run a global strictly feasible `BucketAscentPolish` on the byte-exact model starting from the feasible run. The latter is tractable at several hundred dimensions precisely because it only improves and never searches. `stepRange` (section 5.1) should be wired up first.
2. Facing walls in the closed form, a capability item rather than a speed item. An F constraint is a sector constraint on the input direction. The inner maximization over the set where $\|u\| = m$ and $\hat{u}$ lies in the sector is still closed-form, since the costate direction can be clamped to the nearer sector edge. The dual stays convex and the zero-gap argument survives. Today a single F constraint anywhere disqualifies the entire fast path. This item should be built when the third trigger of section 5 fires.
3. Cross-window warm starts (section 5.1), when a trigger of section 5 fires.
4. The GCS corridor layer over `FreeSpaceDecomposition` (section 6.3), when block solving becomes active again.
5. Folding the single-jump path into `LongRunSolver`. A one-jump run is one final window with the full ladder, so the fold deletes the `countJumps` branch at zero behavioral change. The cost to weigh is one extra call layer on the microsecond path.
6. `JumpPhysicsInputs.jumpTick` survives only as a fallback and for `BlockSolver`'s launch footprint. Collapsing it to the mask plus a `firstJumpTick()` helper removes a field with two sources of truth.
7. Direction-parallel window solves, a latency improvement only on windows whose first direction fails. Since j001 never fails a direction, this should be measured on a fixture that does before being built.

---

## 8. Headless validation and build notes

### 8.1 The committed harness

`core/src/test/resources/captures/` is the shared capture library (j001 through j021). The folders `resources/problems/<check>/` define which check each capture must pass: `solve/` requires that the capture still solves through the live engine within an optional time budget, and `closedform/` requires a byte-exact feasible, on-objective, and fast closed-form solve. `ProblemsTest` discovers everything, so adding coverage amounts to dropping a capture or an `.expect.json` sidecar into a folder. See `core/src/test/java/.../anglesolver/TESTS.md` and `resources/problems/README.md`.

For reading old branches and history, j001, j002, and j003 are recaptures of the former `deserthard-v12`, `-v7`, and `-vfail` saves, and the j154 block-derive fixtures live only in the `features/angle-optimizer` history (section 2.2).

j021 (rina v1 01, the duality-gap fixture of section 2.1.1) predates the `angleSolver.seed` field, so its launch state at tick 136 was reconstructed by replaying rows 0 to 135 through `ExactJumpModel` and validated bit-exact: replaying the capture's recorded failed yaws from the reconstructed seed reproduces all 13 recorded outcome positions to the full printed precision. The seed's Y components are not physical (the model does not clamp Y onto surfaces) and are irrelevant, since every constraint is X/Z and tick 136 is a grounded jump tick whose Y velocity is overwritten by the jump impulse.

The committed captures do not carry a `debug[]` array. `ProblemFixture` rebuilds the box trajectory from the recorded ticks. The save format does support debug saves (with "save debug values" enabled), which carry per-tick recorded state usable as a debugging oracle for fact-checking, for example by forwarding candidate facings through `ExactJumpModel` and comparing positions to measure drift. A recorded path is never an input the production solver may assume, since normal solves have no path given.

One measurement caveat applies. The engine `solve()` wall clock includes the worker-thread spawn overhead and must not be used for the microsecond fast-path number. The fast path is timed by running `ClosedFormSolve.optimize` in a tight loop, while `SolveBenchmark` provides the end-to-end number.

### 8.2 Self-validation recipe for DERIVE work

```
SaveIO.parseSafe(file) -> seed scenario from file.angleSolver.seed -> blocks from file.angleSolver.selectedBlocks
constraints = DERIVE(blocks, scenario, feetY[])              <- the thing under test
yaws = SolveCore.optimize(model, JumpSpec(scenario, constraints, objective), ...)
path = model.forward(scenario, scenario.toGameFacings(yaws))
VALID <=> no SweptCollision.firstHit over all moves AND path[N] in land footprint
```

`VALID` is ground truth. Both directions should stay covered: a recorded working solution must verify swept-clean (no false positives), and a known-clipping solution must be flagged (no false negatives).

### 8.3 Build notes

`gradle.properties` pins a Windows JDK, which is overridden with `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`. The loader modules require Gradle plugins (`fabric-loom`, `unimined`) that are unreachable offline, so for `:core` tests `settings.gradle` is trimmed to `include 'core'` and restored afterwards. Core is Java 8 with JUnit 4: `./gradlew :core:test -Dorg.gradle.java.home=...`.

---

## 9. Citations

Lossless convexification / fixed-modulus
- Acikmese & Ploen, "Convex Programming Approach to Powered Descent Guidance for Mars Landing," *JGCD* 30(5), 2007. https://doi.org/10.2514/1.27553
- Acikmese & Blackmore, "Lossless convexification of a class of optimal control problems with non-convex control constraints," *Automatica* 47(2), 2011. https://www.sciencedirect.com/science/article/abs/pii/S0005109810004516
- Harris & Acikmese, "Lossless convexification ... for state constrained linear systems," *Automatica* 50(9), 2014. https://www.sciencedirect.com/science/article/abs/pii/S0005109814002362
- Malyuta et al., "Convex Optimization for Trajectory Generation" (tutorial), *IEEE CSM*, 2022. https://arxiv.org/abs/2106.09125
- Luo et al., "Revisiting Lossless Convexification: Theoretical Guarantees for Discrete-time Optimal Control Problems," arXiv:2410.09748, 2024. https://arxiv.org/abs/2410.09748
- Luo, Spada, Acikmese, "Discrete-Time Lossless Convexification for Pointing Constraints," arXiv:2501.06931, 2025. https://arxiv.org/abs/2501.06931

Convex decomposition / safe corridors / MILP
- Deits & Tedrake, "Computing Large Convex Regions of Obstacle-Free Space Through Semidefinite Programming" (IRIS), WAFR 2014. https://groups.csail.mit.edu/robotics-center/public_papers/Deits14.pdf
- Liu, Watterson, Kumar et al., "Planning Dynamically Feasible Trajectories ... Safe Flight Corridors," *RAL* 2017. https://github.com/sikang/DecompUtil
- Richards & How, "Aircraft Trajectory Planning with Collision Avoidance Using MILP," ACC 2002. https://www.et.byu.edu/~beard/papers/library/RichardsEtAl02.pdf
- Kronqvist, Misener, Tsay, "P-split formulations: A class of intermediate formulations between big-M and convex hull for disjunctive constraints," *Math. Programming*, 2025. https://arxiv.org/abs/2202.05198

Graph of Convex Sets
- Marcucci, Umenberger, Parrilo, Tedrake, "Shortest Paths in Graphs of Convex Sets," *SIAM J. Opt.* 34(1), 2024. https://arxiv.org/abs/2101.11565
- Marcucci, Petersen, von Wrangel, Tedrake, "Motion planning around obstacles with convex optimization," *Science Robotics* 8(84), 2023. https://github.com/RobotLocomotion/gcs-science-robotics
- Chia, Jiang, Graesdal, Kaelbling, Tedrake, "GCS\*: Forward Heuristic Search on Implicit Graphs of Convex Sets," WAFR 2024. https://arxiv.org/abs/2407.08848
- Osburn, Peterson, Salmon, "Systematic Constraint Formulation and Collision-Free Trajectory Planning Using Space-Time Graphs of Convex Sets," arXiv:2508.10203, 2025. https://arxiv.org/abs/2508.10203

Collision-in-solver: SCP / SDF / MPCC
- Schulman et al., "Finding Locally Optimal, Collision-Free Trajectories with Sequential Convex Optimization" (TrajOpt), RSS 2013. https://www.roboticsproceedings.org/rss09/p31.pdf
- Zucker, Ratliff, Dragan et al., "CHOMP," *IJRR* 32(9-10), 2013. https://www.ri.cmu.edu/pub_files/2013/5/CHOMP_IJRR.pdf
- Posa, Cantu, Tedrake, "A direct method for trajectory optimization of rigid bodies through contact," *IJRR* 33(1), 2014. https://dl.acm.org/doi/10.1177/0278364913506757
- Mao, Dueri, Szmuk, Acikmese, "Successive Convexification of Non-Convex Optimal Control Problems with State Constraints," 2017. https://arxiv.org/pdf/1701.00558
- Li et al., "On the Surprising Robustness of Sequential Convex Optimization for Contact-Implicit Motion Planning" (the CRISP solver), arXiv:2502.01055, 2025. https://arxiv.org/abs/2502.01055

Swept CCD / homotopy / kinodynamic
- Tang, Kim, Manocha, "C2A: Controlled Conservative Advancement for Continuous Collision Detection," ICRA 2009. https://graphics.ewha.ac.kr/C2A/C2A.pdf
- Li, Ferguson et al., "Incremental Potential Contact (IPC)," SIGGRAPH 2020 (the positive-standoff contact model). https://ipc-sim.github.io/
- Li, Kaufman, Jiang, "Codimensional Incremental Potential Contact" (introduces additive CCD), SIGGRAPH 2021. https://arxiv.org/abs/2012.04457
- Wang, Ferguson et al., "A Large-scale Benchmark and an Inclusion-based Algorithm for CCD," *ACM TOG*, 2021. https://dl.acm.org/doi/10.1145/3460775
- Bhattacharya, Likhachev, Kumar, "Topological Constraints in Search-Based Robot Path Planning" (h-signature), AAAI 2010 / *Auton. Robots* 2012. https://www.lehigh.edu/~sub216/local-files/topology_AURO_author_version_57596.pdf
- Li, Littlefield, Bekris, "Asymptotically Optimal Sampling-based Kinodynamic Planning (SST/SST*)," *IJRR* 2016. https://arxiv.org/abs/1407.2896
- "Collisions" (axis-sequential Y-X-Z collide-and-slide), Minecraft Parkour Wiki. https://www.mcpk.wiki/wiki/Collisions
