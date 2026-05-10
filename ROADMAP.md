## Done
- [x] Configurable keybind to open UI
- [x] Multi-module restructure: extract Minecraft-free UI / data / selection code
      into a shared `core/` module; current Fabric mod becomes
      `loader-fabric-1.21.10/`. See `REFACTOR_PLAN.md` and `REFACTOR_RESULT.md`.

## v1.1.0 - UI Polish
- [ ] Color-coded boxes by movement state
- [ ] Fix: Don't toggle when chat is open

## v1.2.0 - Playback & Visualization
- [ ] Save/load and playback macros
- [ ] Hitboxes instead of dots
- [ ] Subtick visualization (Y->X->Z)

## v1.3.0+ - Multi-version support
- [ ] Mojmap migration on the Fabric 1.21.10 loader (precondition for new loaders)
- [ ] Add Forge 1.8.9 loader (`loader-forge-1.8.9/`) — reuses `core/`. Will surface
      the first real port interfaces (`WorldView`, `PlayerStateProvider`, etc.)
- [ ] Add Forge 1.12.2 loader
- [ ] Add NeoForge / Fabric 26.x loader

## Future
- [ ] Position bruteforcing system
- [ ] Distance checking between hitboxes