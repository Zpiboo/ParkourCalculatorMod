# Parkour Calculator

A TAS input planning mod for Minecraft. Simulate and visualize parkour movements before executing them.

## Features

- Plan movement inputs tick-by-tick
- Visualize predicted path in the world
- Drag start position to test different setups
- Pin windows for quick access

## Installation

1. Install [Fabric Loader](https://fabricmc.net/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest release
4. Place the `.jar` in your `mods` folder
5. Launch Minecraft

## Usage

Press `L` to open the calculator.

### Adding Inputs

1. Click key columns to toggle inputs
2. Drag across columns to set multiple keys
3. Enter YAW values for rotation

### Managing Rows

- Right-click to add rows
- Click to select, Ctrl+Click to multi-select
- Shift+Click for range selection
- Delete key to remove selected rows
- Drag rows to reorder

### Moving Start Position

Click and drag the first box in the world to reposition.

## Controls

| Key | Action |
|-----|--------|
| `L` | Toggle UI |
| `ESC` | Close UI |
| `Ctrl+Click` | Toggle selection |
| `Shift+Click` | Range select |
| `Right-Click` | Context menu |

## Building from Source

Requires JDK 21.

```bash
./gradlew :loader-fabric-1.21.10:build
```

The output JAR lands in `loader-fabric-1.21.10/build/libs/`.

The repo is a Gradle multi-module project: `core/` holds Minecraft-free UI code (Java 8 compatible), and `loader-fabric-1.21.10/` is the Fabric mod itself. See `CLAUDE.md` for architecture details.

## Contributing

This project uses [Conventional Commits](https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please) to automate versioning. Use these prefixes in PR titles (squash-merge keeps `main` clean — branch commits can be anything):

| Prefix | Effect on version |
|---|---|
| `feat:` | minor bump (e.g. `1.0.0` → `1.1.0`) |
| `fix:` | patch bump (e.g. `1.0.0` → `1.0.1`) |
| `feat!:` or `BREAKING CHANGE:` in body | major bump (e.g. `1.0.0` → `2.0.0`) |
| `chore:`, `docs:`, `refactor:`, `test:`, `ci:` | no bump |

### Workflow

1. Create a feature branch off `main`, commit freely.
2. Open a PR with a conventional title (e.g. `feat: add 1.20 support`).
3. Squash-merge into `main`.
4. release-please opens (or updates) a "Release PR" on `main` showing the next version + a CHANGELOG entry.
5. Merge the Release PR when you want to ship — release-please tags the commit, creates a GitHub Release, and CI attaches the three loader jars.
