# Refactor Result: Multi-Version Architecture (Phase 1)

Branch: `multi-version-refactor` (kept on existing branch instead of creating
`refactor/extract-core` per user's git policy).

Companion document: `REFACTOR_PLAN.md` — Step 1 inventory and Step 2 port design.

---

## Final structure

```
ParkourCalculatorMod/
  settings.gradle              ← multi-module include
  build.gradle                 ← shared group/version
  gradle.properties            ← shared mod_version, maven_group, JDK pin
  gradle/, gradlew, gradlew.bat
  core/
    build.gradle               ← Java 8 lib, compileOnly imgui-java 1.90.0
    src/main/java/de/legoshi/parkourcalc/core/
      imgui/RenderInterface.java
      ui/InputData.java
      ui/InputOverlay.java
      ui/InputRow.java
      ui/KeyDragSelect.java
      ui/OverlayManager.java
      ui/SelectionManager.java
  loader-fabric-1.21.10/
    build.gradle               ← Fabric Loom, Java 21, depends on :core
    gradle.properties          ← Fabric/MC version pins
    src/client/
      java/de/legoshi/parkourcalc/fabric/
        ParkourCalculatorFabric.java   (was ParkourCalculatorClient)
        BoxController.java
        BoxInfo.java
        MovementSimulator.java
        SimulatorEntity.java
        SimulatorInput.java
        imgui/ImGuiImpl.java
        mixin/InGameHudMixin.java
        mixin/KeyboardMixin.java
        mixin/MinecraftClientMixin.java
        mixin/MouseMixin.java
        mixin/WorldRendererMixin.java
      resources/
        fabric.mod.json (entrypoint updated)
        parkourcalculator.client.mixins.json (package updated)
        assets/...
```

## Final counts

| Module | .java files | LOC (incl. blank/comments) |
|--------|-------------|----------------------------|
| `core/` | 7 | 775 |
| `loader-fabric-1.21.10/` | 12 | 1138 |
| Total | 19 | 1913 |

Pre-refactor total was the same 19 files (no new files added, no files deleted).

## Port interfaces created

**None.**

Per Step 2's call-site analysis (see `REFACTOR_PLAN.md`), the seven core-bound files
have zero call sites that require a Minecraft-backed abstraction. The user signed off
on Option 1 (no speculative ports) before Step 3 began.

The only file that touched MC API and was still moving to core (`SelectionManager`,
which read ctrl/shift via GLFW + `MinecraftClient`) was migrated to read those bits
from `ImGui.getIO().getKeyCtrl()` / `getKeyShift()` instead. ImGui's IO is already
populated by `KeyboardMixin` in the loader, so no new interface was needed.

The `core/.../ports/` and `core/.../model/` directories the original task spec
suggested were not created.

## Surprises and friction points

### 1. The task's "validated facts" missed Java-21 features in core-bound files

The task's reference said the only Java-9+ features in the codebase were two
`record` declarations and several `var` usages in three files. In addition to those,
two more Java-21-only APIs were used:

- `Math.clamp(int, int, int)` in `InputOverlay.java:268` — replaced with
  `Math.max(1, Math.min(100, ...))`.
- `List#getFirst()` (sequenced collections) in `BoxController.java:45` — left
  alone because `BoxController` stays in the loader (Java 21).

So three actual rewrite points landed in core: one `Math.clamp`, one `var` in
`OverlayManager`, one `record` in `SelectionManager`.

### 2. ImGui API renamed between 1.86.11 and 1.90.0

The task originally pinned core's compileOnly imgui-java to **1.86.11** (the
Java-8-compatible line for the future Forge 1.8.9 path). That broke immediately:
`ImGuiSelectableFlags.AllowOverlap` (used by `InputOverlay.renderRowNumber`) only
exists in 1.89+. In 1.86.11 the same flag is named `AllowItemOverlap`.

Initial resolution was a numeric-literal workaround
(`private static final int IMGUI_SELECTABLE_FLAGS_ALLOW_OVERLAP = 1 << 4;`).

Final resolution (post-task follow-up): bumped core's compileOnly to
**1.90.0** to align with the loader's runtime, removed the numeric constant, and
restored the proper `ImGuiSelectableFlags.AllowOverlap` reference. This was the
right call — there's no future-proofing benefit to compiling core against an older
ImGui version when the same JVM runs both core and loader at runtime.

Implication for the eventual Forge 1.8.9 loader: it will need to bundle imgui-java
1.90.0 (or later) as well — or carry its own shim. The "1.86.11 is the Java 8 line"
constraint from the task spec is therefore deferred to that loader's task; core no
longer enforces it. If 1.90.0 turns out not to run on Java 8, that loader will need
a compatibility approach decided when it's built.

### 3. Fabric Loom multi-module: minor build.gradle adjustments

Three small things needed adapting after splitting Loom out of the root project:

1. The git-version block originally ran `git rev-list ...`.execute() with the
   subproject's working dir. Switched to `.execute(null, rootProject.projectDir)`
   so it still picks up the repo state.
2. The `jar` block's `from("LICENSE")` would have looked under
   `loader-fabric-1.21.10/LICENSE`. Switched to `rootProject.file("LICENSE")`.
3. `group = project.maven_group` removed from the loader's build.gradle —
   root's `allprojects { group = ... }` already sets it.

### 4. LWJGL version-conflict log noise (pre-existing, non-fatal)

`runClient` logs repeated `NoSuchMethodError` for
`org.lwjgl.stb.STBImageResize.nstbir_resize_uint8` from MC's `NativeImage` icon
resize threads. Dependency tree shows `lwjgl-stb 3.3.6` resolved correctly (MC's
expected version), so this is most likely an LWJGL native-vs-binding mismatch in the
runtime environment, not a classpath issue introduced by the refactor. User
confirmed the same noise existed pre-refactor and the UI works regardless. Flagged
here for the record; not in scope to fix in this task.

## Validation criteria — pass / fail

| # | Criterion | Result |
|---|-----------|--------|
| 1 | `:core:build` succeeds and `core-1.0.0.jar` contains no `net/minecraft`, `net/fabricmc`, or `org/lwjgl` references | **PASS** — verified by unzipping the jar and grepping class files; only `imgui/` references remain (expected). |
| 2 | `:loader-fabric-1.21.10:build` succeeds | **PASS** — produces `parkourcalculator-1.0.0+build.10.<sha>.dirty.jar`. |
| 3 | `:loader-fabric-1.21.10:runClient` launches MC 1.21.10 | **PASS** — confirmed by user. |
| 4 | Pressing `L` opens the calculator UI | **PASS** — confirmed by user. |
| 5 | Tick-by-tick input editing works | **PASS** — confirmed by user ("behaving correctly"). |
| 6 | Predicted path renders in the world | **PASS** — confirmed by user. |
| 7 | Drag-and-drop on the start box repositions the simulation start | **PASS** — confirmed by user. |
| 8 | Pin windows feature works | **PASS** — confirmed by user. |
| 9 | Selecting and deleting rows works | **PASS** — confirmed by user. |

User functional sign-off: "I was able to open the UI in minecraft and it was still
behaving correctly".

## Approximate time spent

~30 minutes total (assistant wall-clock), broken down roughly:
- Inventory + port design: ~5 min
- Gradle scaffolding + first dual-module build green: ~5 min
- File moves + package edits + cross-ref updates: ~10 min
- Java-8 compat fix iterations (Math.clamp, var, record, AllowOverlap): ~5 min
- Validation + reporting: ~5 min

## Items the user may want to follow up on

- The `build/` and `run/` directories left at the repo root from the pre-refactor
  layout are now stale; `./gradlew clean` will not remove them because they are
  outside any subproject. Safe to delete manually.
- The `org.gradle.java.home` pin in root `gradle.properties` is hardcoded to
  `C:\Program Files\Java\jdk-21`, which works on this machine but not on a
  collaborator's box. Consider switching to a toolchain spec at some point.
- When the second loader is added, verify imgui-java 1.90.0 actually runs on its
  target JVM (Java 8 for Forge 1.8.9). If not, that loader will need a different
  imgui-java version + a compatibility shim or a downgraded API surface in core.