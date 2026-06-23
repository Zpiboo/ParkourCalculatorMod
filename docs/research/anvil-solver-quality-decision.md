# Anvil solver-quality investigation, and the colliding run-up search direction

Investigation + design record (2026-06-23). Question raised: the `claude/brave-planck-lyr5ng` branch got
within ~1.3e-6 of a known-good byte-exact result on the close-range anvil fixture; can we port that into
the shipped solver to "replicate the solver quality"? The investigation reframed the goal twice. This
records the evidence, the corrected understanding of the problem, and the chosen direction so the reasoning
is not lost.

## What the branch actually was

- Its only production change was raising knob *ceilings* in `AngleSolverState`; no algorithm changed.
- Its headline result (anvil X `186.82489091`) was seeded from an external Wolfram continuous solve that is
  not committed. The in-tool cold best was the prior `~186.824818`.
- Its #1 recommended lever, the ILS ratchet, is already shipped as `IlsPolish`.

## What the spikes measured (live solver, headless `:core:test`)

Fixture `1x1.875bm_bfly_to_anvil_close` (3 jumps, n=38, MAX X@38; known-good = `186.82489222324986`).

| run | gap to known-good | note |
|---|---|---|
| shipped CUSTOM + ILS exhaustive (120s) | 2.34e-4 | meets 16/16, but against a deliberately relaxed landing wall (below) |
| + 180s more isolated ILS from that seed | 1.90e-4 | a crawl, not a basin hop |
| CMA on a ported smooth (continuous) model | ~3.3e-4 | finds a smooth *local* optimum, not the global |
| SLP ascent on the smooth model | no ascent | local method, stuck in CMA's basin |

Structural facts: (1) the shipped plateau is a basin problem, not a budget problem; (2) known-good is
byte-feasible but smooth-INFEASIBLE, so no continuous method can reach it; (3) the smooth problem is
nonconvex/multi-basin, so local SLP + CMA-multistart plateau ~3.3e-4 short and only a heavy global QCQP
solver could reach the continuous ceiling.

## The jump does not actually land

The committed landing wall is set ~0.001 *below* the true block edge on purpose, so the near-miss
trajectory renders instead of failing. The real landing edge is the AABB clamp at `~186.825`
(`GOAL = 186.82499998807907`).

- The best byte-exact path ever (known-good `186.82489222`) is **-1.0777e-4** short of the edge: it does
  not land. This is the "so close yet so far" gap that motivated the whole grind.
- The research's continuous **ceiling** (`~186.8248951`) and convex **upper bound** (`~186.82489297`) both
  sit ~1.05-1.07e-4 *below* the edge. **The free-flight model's own ceiling is below the landing
  requirement.** So global-QCQP (which only reaches that ceiling) would close the optimization gap and
  *still not land the jump*. The remaining gap is a structural free-flight ceiling, not an optimizer
  weakness. (Caveat: confirm `186.82489297` is a *rigorous* relaxation bound, not Wolfram's best-found,
  before treating free-flight unsolvability as proven; a clean SOCP/SDP bound would settle it.)

## Loop mm, correctly understood

An early framing (taken from the branch's `anvil-cold-solve.md`) called loop mm a "hitbox extension /
collision technique." That is wrong per the project glossary (CONTEXT.md): **loop mm is repeated backward
momentum** -- moving backward to open up run-up space so the player accelerates over more distance and
carries more velocity into the jump, looping back and forth to accumulate a little more each pass. It is a
*momentum* technique. The user's precise statement of the optimization: go backward to get off the block
and extend momentum, but too far/fast backward and you cannot recover forward in time; find the backward
excursion that maximizes forward traversal at the jump.

In-model / out-of-model boundary (corrected):

- **In model:** the velocity bookkeeping of going backward then forward. Given per-tick inputs and which
  ticks are on-ground vs airborne, the model integrates velocity over the yaws. With the run-up ticks in
  the solve window, the optimizer can in principle trade backward speed against forward recovery; the
  sweet spot is just where the objective peaks.
- **Not in model (the real blocker):** *when the player is on the block vs airborne.* The solver takes the
  on-ground/airborne state per tick from the recording (`slipAt(t)`), and never recomputes it from
  position. But "how far back before you drop off the block" and the extra-airborne-tick are determined by
  colliding position against the level, which the free-flight model does not simulate. The ground state
  depends on the trajectory being optimized (circular), and breaking that circle is exactly "collision in
  the loop."

So the lever that can actually land these jumps is **modeling the run-up momentum with
collision-determined ground state**, not a stronger free-flight optimizer. Global-QCQP is the wrong tool
(its ceiling is below the edge); lattice enumeration only reaches known-good (also short).

## Chosen direction: a byte-exact colliding forward model + run-up search

Decision: build collision into the *model the optimizer evaluates*, not into the optimizer. CMA-ES and ILS
are model-agnostic (they call `model.forward`), so a new `CollidingJumpModel implements ForwardModel`
drops in unchanged. Each tick: the existing free-flight step, then clamp against the level's block AABBs
(MC `moveEntity` order Y -> X -> Z), set `onGround`, zero the clamped-axis velocity, and read slipperiness
off the block stood on. `SweptCollision` already ports the hard part (the asymmetric clamp order) but only
*detects* the first hit; the work is extending it to *apply* a full clamped move.

Consequences accepted:
- **Convex solvers do not apply.** `ClosedFormSolve` / `SlpSolve` / costate dual assume position is linear
  in the inputs (collision-free). The run-up search is CMA-ES / ILS only.
- **Rugged objective.** Collision adds discontinuities (`onGround` flips, clamps); the landscape is harder
  than the smooth free-flight one. CMA/ILS tolerate this.
- **Byte-exactness is the risk and the point.** The collider must nail step-up, ground snap, the
  `onGround` flag, per-axis velocity zeroing, and slip-from-block, pinned bit-for-bit against the real
  `SimulatorEntity` (as `ModernStepRegressionTest` pins the free-flight model). Speed is fine: colliding
  against a handful of AABBs per tick is cheap; the "no entity in the inner loop" rule was about the whole
  `SimulatorEntity`, not the collision math.

## Hard prerequisite: persist world geometry at capture

The headless core has no blocks (`selectedBlocks` is empty; it stores only *derived* corridor
constraints; `isBlockSolid` is false headless; the block-picker path is shelved). A headless byte-exact
collider therefore needs the local world geometry captured and saved. Chosen: the loader snapshots
collision boxes in a generous region around the run-up + jump (covering the backward-excursion space) at
capture time and persists them (the `BlockSel` AABB save format can be extended); the headless collider
loads them. Open: the region/extent policy (the excursion is open-ended, so capture generously and warn
when the search reaches the captured boundary).

## Plan (scope: 1.8.9 first, the anvil version)

1. Extend `SweptCollision` from detect -> apply: full Y->X->Z clamp returning the clamped position,
   `onGround`, per-axis velocity zeroing, step-up, and the block stood on (for slipperiness).
2. `CollidingJumpModel implements ForwardModel`: free-flight step + clamp per tick, ground state from
   position. Pure core (AABB is a core/sim type).
3. World-geometry capture: loader snapshots collision boxes in a region; persist; headless collider loads.
   Region policy + captured-boundary warning.
4. Byte-exact validation: a regression test pinning `CollidingJumpModel` against `SimulatorEntity` on
   collision captures (the user records loop-mm run-ups in-game).
5. Run-up solve mode: include run-up ticks in the window, search yaws with CMA/ILS on the colliding model,
   objective = landing X (collision landing). Surface the optimal backward excursion.
6. Later: 1.12.2 / 1.21.10 collision variants; whether inputs beyond yaw are ever searched (currently out
   of scope).

## What we are NOT doing, and why

- Not building a global-QCQP free-flight optimizer: its ceiling is below the landing edge, so it cannot
  land anvil regardless of how good it is.
- Not treating loop mm as a hitbox/collision trick: it is momentum; the only collision aspect is the
  ground/air state, handled by the colliding model above.

## Committed fixtures (for #186 / #178)

These captures live in `core/src/test/resources/captures/` as a fixture library; no `ProblemsTest`
check references them yet (no sidecar), they are committed so the work in #186 / #178 has concrete data.

| Fixture | What it is |
|---|---|
| `loopmm-3jump-lands.json` | The reach-failure witness. A hand-made 3-jump loop-mm route (start tick 38, land 71, jumps at 38/50/62, the 12-tick flat cycle, MAX Z@71) that **lands**: byte-exact Z@71 = -279.29973, feasible. Proof a feasible landing exists in the model. |
| `loopmm-3jump-solver-misses.json` | Same jump, the **solver's** result (CMA-ES): Z@71 = -279.30585, feasible but **0.0058 short of landing**. The optimizer-reach failure motivating #186. |
| `1x1.875bm_bfly_to_anvil_close.json` | The anvil near-ceiling fixture (3 jumps, MAX X@38). Reproduction data for this doc's anvil numbers. Note: its committed landing wall is set ~0.001 below the true edge so the near-miss renders. |
| `j008-bfneo-to-anvil-loopmm.json`, `anvil-best-facings.txt` | The anvil parkour in the j008 frame, plus the known-good byte-exact facings (X = 186.82489222324986). |

When #186 / #178 work starts, give these a `solve` sidecar with a `refObjective` so they become real
regression checks (e.g. the solver must reach >= -279.29973 on `loopmm-3jump-lands`, i.e. must land).
