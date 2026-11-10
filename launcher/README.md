# Annoying Client Launcher

`AnnoyingClient.exe` is a standalone launcher for Annoying Client: it installs Minecraft 1.21.11 with Fabric,
manages mods from Modrinth, optimizes FPS, and starts the game.

## Features
- **Play:** downloads and verifies the game, libraries, assets and Mojang's Java 21 runtime (parallel, SHA-1 checked,
  resumable through re-verification), installs Annoying Client + Fabric API, and launches.
  - *Via Minecraft Launcher* (works for everyone): writes an "Annoying Client" profile that uses this launcher's
    game folder, mods, Java flags and window size, then opens the official launcher, which handles sign-in.
  - *Direct*: Microsoft device-code sign-in -> Xbox Live -> XSTS -> Minecraft, with token refresh. Tokens are
    encrypted with Windows DPAPI. Requires an Azure application ID that Mojang approved for Minecraft (Settings).
- **Mods:** search Modrinth (Fabric, 1.21.11 only), one-click install with required dependencies, enable/disable,
  remove, update checks by file hash, update all. Annoying Client and Fabric API are protected.
- **Performance:** Max FPS / Balanced / Quality video presets written into `options.txt` (other settings kept),
  one-click Sodium, Lithium, FerriteCore, Entity Culling and ImmediatelyFast, memory slider with a recommendation for
  your PC, and G1 (Aikar's flags) or generational ZGC.
- **Console:** live game output with filter and copy, plus a Stop button.
- **Settings:** launch mode, window size, fullscreen, Java path, extra JVM arguments, download threads, client ID,
  folder shortcuts and "Repair game files".

## How it works
The launcher is a Go program (standard library only) with an embedded web UI. It listens on `127.0.0.1` with a
random port and a per-session secret token, and opens the UI as an app window through Microsoft Edge (or Chrome).
It exits when that window is closed and the game is not running. Data lives in `%APPDATA%\.annoyingclient`:
`runtime/`, `libraries/`, `assets/`, `versions/`, `instance/` (the game folder) and `logs/launcher.log`.

## Build
```bash
./build.sh
```
