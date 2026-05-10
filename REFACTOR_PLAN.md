# Refactor Plan: Multi-Version Architecture

This document is the output of Step 1 (inventory) and Step 2 (port design) of the
multi-version refactor. Step 3+ are not started yet.

Branch: `multi-version-refactor` (already checked out; not creating
`refactor/extract-core` per user's git policy).

---

## Step 1 — File Inventory

Every `.java` file under `src/client/java/de/legoshi/` classified.

Target legend:
- **CORE** — moves to `core/` (Java 8, no MC). May require Java-21 → Java-8 refactor.
- **LOADER** — stays in `loader-fabric-1.21.10/` (Java 21, full MC access).
- **SPLIT** — contains both MC and pure logic; needs methods extracted on each side.

| File | Target | Reason | Refactor needed |
|------|--------|--------|-----------------|
| `ParkourCalculatorClient.java` | LOADER | Fabric `ClientModInitializer`. Imports `MinecraftClient`, `KeyBinding`, `Camera`, `Vec3d`, `Matrix4f`, `MatrixStack`, `VertexConsumerProvider`, GLFW, Fabric API. Top-level orchestration. | Rename to `ParkourCalculatorFabric`, move into `de.legoshi.parkourcalc.fabric` package, also wire core ports here. |
| `MovementSimulator.java` | LOADER | Creates and drives a `Player` subclass; reads `MinecraftClient.player` / `ClientWorld`. Cannot run without MC physics. | Returns simulated path. The output type can remain `Vec3d` since `BoxController` (also loader) consumes it. No port needed. |
| `SimulatorEntity.java` | LOADER | `extends net.minecraft.entity.player.PlayerEntity`. The whole point of this class is to ride MC's tick logic. | None. |
| `SimulatorInput.java` | LOADER | `extends net.minecraft.client.input.Input`. Uses `Vec2f`, `PlayerInput`. | Reads `InputRow` (a core type) — keep that import; `InputRow` is in core. |
| `BoxController.java` | LOADER | Camera, Vec3d, MatrixStack, MinecraftClient, attack-key check throughout. Picking/drag math is MC-agnostic in principle, but every input/output is a `Vec3d` and the rendered objects are MC `Box`s. Splitting buys nothing concrete today. | Stays whole. Java 21 features (`record DragState`, `var hit`, `boxes.getFirst()`) all stay — loader is Java 21. |
| `BoxInfo.java` | LOADER | Holds `net.minecraft.util.math.Box`; rendering goes through `DebugRenderer`. | None. |
| `imgui/ImGuiImpl.java` | LOADER | Bootstraps ImGui against MC's framebuffer; uses `GlStateManager`, `RenderSystem`, LWJGL natives, GLFW. Task explicitly assigns ImGui bootstrap to the loader. | None — stays as-is, just moved into the loader package. |
| `imgui/RenderInterface.java` | CORE | Imports only `imgui.ImGuiIO`. | None. |
| `ui/InputData.java` | CORE | Imports only `java.util`. | None. |
| `ui/InputRow.java` | CORE | Imports only `java.util`. | None. |
| `ui/InputOverlay.java` | CORE | Imports only `imgui.*` and `java.*`. | **Replace `Math.clamp(rowsToAdd.get(), 1, 100)` (line 268; Java 21) with `Math.max(1, Math.min(100, ...))`.** This Java-21 use was not flagged in the task's "validated facts". |
| `ui/KeyDragSelect.java` | CORE | Imports only `imgui.ImGui` and `java.util`. | None. |
| `ui/OverlayManager.java` | CORE | Imports only `imgui.*` and `java.util`. | Replace `for (var entry : overlays.entrySet())` (line 54) with explicit `for (Map.Entry<String, OverlayEntry> entry : ...)`. |
| `ui/SelectionManager.java` | CORE (with rewrite) | Imports `MinecraftClient` + `GLFW` *only* to read ctrl/shift in `getModifierState()`. The rest is pure. | (1) Replace `record ModifierState(boolean ctrl, boolean shift)` with a plain final-field class. (2) Replace `getModifierState()`'s GLFW reads with `ImGui.getIO().getKeyCtrl()` / `getKeyShift()`. ImGui's IO is already populated for those bits by `KeyboardMixin`. No loader port needed. |
| `mixin/InGameHudMixin.java` | LOADER | Mixin. | None. |
| `mixin/KeyboardMixin.java` | LOADER | Mixin + GLFW + Fabric `KeyBindingHelper`. | None. |
| `mixin/MinecraftClientMixin.java` | LOADER | Mixin. | None. |
| `mixin/MouseMixin.java` | LOADER | Mixin. | None. |
| `mixin/WorldRendererMixin.java` | LOADER | Mixin. | None. |

### Counts
- CORE: 7 files (`RenderInterface`, `InputData`, `InputRow`, `InputOverlay`, `KeyDragSelect`, `OverlayManager`, `SelectionManager`).
- LOADER: 12 files (everything else, including all mixins, simulation, rendering, ImGui bootstrap, and Fabric entry point).
- SPLIT: 0 (no file has both pure-logic methods and MC-call methods that are worth separating today).

### Java-21 features that have to be patched on the way to core
The task's "validated facts" listed only 2 records and several `var` usages. Two more Java-21 uses were missed:

| File | Line | Feature | Fix |
|------|------|---------|-----|
| `ui/InputOverlay.java` | 268 | `Math.clamp(int,int,int)` (Java 21) | `Math.max(1, Math.min(100, rowsToAdd.get()))` |
| `BoxController.java` | 45 | `List#getFirst()` (Java 21, sequenced collections) | LOADER — no fix needed |
| `ui/SelectionManager.java` | 107 | `record ModifierState` | Plain class with final fields |
| `ui/OverlayManager.java` | 54 | `var` in for-each | Explicit `Map.Entry<String, OverlayEntry>` |
| `BoxController.java` | 60 | `var hit` | LOADER — no fix needed |
| `BoxController.java` | 159 | `record DragState` | LOADER — no fix needed |

So 3 actual code-rewrite spots in core-bound files: one `Math.clamp`, one `var`, one `record`.

---

## Step 2 — Ports (call-site verified)

The task's pseudocode listed six likely ports: `WorldView`, `PlayerStateProvider`,
`WorldRenderer`, `ChatLogger`, `KeybindRegistry`, `ImGuiBootstrap`.

**Verifying against actual call sites of files that move to core:**

| Core file | Calls into MC? | Through what API? | Port needed? |
|-----------|----------------|-------------------|--------------|
| `RenderInterface` | No | — | No |
| `InputData` | No | — | No |
| `InputRow` | No | — | No |
| `InputOverlay` | No | Only `imgui.*` and core types; callbacks are `Runnable`s constructed in loader | No |
| `KeyDragSelect` | No | Only `imgui.ImGui` | No |
| `OverlayManager` | No | Only `imgui.*` | No |
| `SelectionManager` | Yes — *only* ctrl/shift read | GLFW + `MinecraftClient` | **No** — switch to `ImGui.getIO().getKeyCtrl()/getKeyShift()`, which is already populated by `KeyboardMixin`. |

### Conclusion
**Zero ports are needed for this refactor.**

Every core file's MC dependency either:
- Doesn't exist (most of them), or
- Can be satisfied by `imgui-java`'s public API (`SelectionManager`'s modifier read).

The orchestration callbacks (`onDataChanged`, `onSetPlayerPosition`) that `InputOverlay`
exposes are already `Runnable`s constructed in `ParkourCalculatorClient`. That existing
indirection is enough — no new interface needs to be defined.

The simulation, rendering, drag interaction, and ImGui bootstrap **all stay in the loader**,
so none of the ports the task suggested (`WorldView`, `PlayerStateProvider`, `WorldRenderer`,
`ChatLogger`, `KeybindRegistry`, `ImGuiBootstrap`) have a current consumer in core.

### Implications for Step 4 / Step 6
- Step 4 (port interfaces): **no interfaces or DTO classes get created**. The `core/.../ports/` and `core/.../model/` directories will not exist after this refactor; we add them only if and when a future loader needs to share logic with core.
- Step 6 (wiring): `ParkourCalculatorCore.init(...)` registry is unnecessary since there's nothing to register. The Fabric mod initializer continues to construct the core UI objects directly (`new InputOverlay(inputData, ...)`) and pass MC-side runnables in.

### When ports *will* become necessary
The first port will appear when the *second* loader (e.g. Forge 1.8.9) is added and we
want any of these in core:
- A* / bruteforce algorithms that need to read block AABBs from the world → `WorldView`.
- Chat output of bruteforce results → `ChatLogger`.
- Cross-version simulation harness that doesn't extend `PlayerEntity` directly → would
  require porting Minecraft's tick logic into core, at which point `WorldView` and a
  block-AABB DTO model both become required.

None of those exist today, so building the interfaces now would be speculative.

### Recommendation / open question for the user

The task spec assumes a non-trivial port surface and asks for `core/.../ports/` and
`core/.../model/` directories with interfaces and DTOs to be created in Step 4. Based on
the actual call sites, **none of those are justified yet**.

Two ways forward — please pick one before Step 3 starts:

1. **Pragmatic (recommended)**: skip ports entirely for this pass. Move the 7 core files,
   patch the 3 Java-21 spots, get the multi-module build green. Add ports in the next
   refactor when the second loader (or core-side simulation/pathfinding) actually needs
   them. End state still satisfies all 9 validation criteria in Step 7.
2. **Speculative**: still create the six ports + their DTOs as no-op interfaces with empty
   loader implementations, anticipating future use. Costs ~6 files of dead code now;
   buys forward shape that may or may not match the eventual second-loader needs.

I'd go with (1). Trade-off: when we add the second loader, we'll do the port-extraction
work then, and we'll have to revisit `ParkourCalculatorClient`'s direct construction of
core types — but that revisit is cheap because the core types are already isolated by
package.

---

## Status

- [x] Step 1: inventory
- [x] Step 2: port design (verified empty)
- [ ] Step 3: Gradle structure — **blocked on user decision (option 1 vs 2 above)**
- [ ] Step 4–8: not started