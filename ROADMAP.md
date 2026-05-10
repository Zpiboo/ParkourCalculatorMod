## Done
- [x] Configurable keybind to open UI
- [x] Multi-module restructure: extract Minecraft-free UI / data / selection code
      into a shared `core/` module; current Fabric mod becomes
      `loader-fabric-1.21.10/`. See `REFACTOR_PLAN.md` and `REFACTOR_RESULT.md`.

## v1.1.0 - Multi-loader support (next)

This phase comes before the UI / playback work below — adding the second loader
is what battle-tests the multi-module abstraction and surfaces the real port
interfaces. The Mojmap migration on the Fabric loader is *not* a precondition;
1.8.9 lives in SRG/MCP-land and is independent.

- [ ] Add `loader-forge-1.8.9/` — reuses `core/`. First real consumer of any
      `core/.../ports/` interfaces; the simulator gets re-implemented against
      1.8.9's `EntityPlayer`. Requires:
      - Confirming imgui-java 1.90.0 is Java 8-compatible bytecode (`javap -v`),
        or downgrading core's compileOnly to 1.86.11 if not.
      - A small LWJGL 2 ImGui bridge in this loader (no published lwjgl2 backend
        exists; the `binding` jar itself is LWJGL-agnostic).
- [ ] Add `loader-forge-1.12.2/` — same pattern, different mappings era.
- [ ] Add `loader-neoforge-1.21/` (or similar modern loader). Mojmap-native;
      revisit Fabric-side mappings at this point if cross-loader code-sharing
      on the loader side becomes desirable.

## v1.2.0 - UI Polish
- [ ] Color-coded boxes by movement state
- [ ] Fix: Don't toggle when chat is open

## v1.3.0 - Playback & Visualization
- [ ] Save/load and playback macros
- [ ] Hitboxes instead of dots
- [ ] Subtick visualization (Y->X->Z)

## Future
- [ ] Mojmap migration on the Fabric 1.21.10 loader (only useful once a modern
      Mojmap-native loader is added and we want to share loader-side helpers)
- [ ] Position bruteforcing system
- [ ] Distance checking between hitboxes