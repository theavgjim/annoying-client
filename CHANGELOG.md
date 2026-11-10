# Changelog

## 1.1.0 (Minecraft 1.21.11)

- **Launcher profiles:** separate game folders per profile, with a "1.21.11 optimized" template of 50
  pinned, checksum-verified Modrinth mods.
- **Mod Configs tab:** every installed mod with its icon; opens mods' own settings screens through the
  Mod Menu entrypoint (Mod Menu not required) and edits .json/.properties/.toml config files in place.
- **Cosmetics:** 7 HD capes and 4 particle trails, a Wardrobe tab with a turnable player preview, coins
  with a daily reward, and admin tools (coins, cosmetics, roles, announcements).
- **Cosmetics server** (`server/`): Go, Mojang-verified sign-in, JSON database, audit log, Docker image.
- The ClickGUI window is 20 px taller to fit the new tabs.

## 1.0.0 — Minecraft 1.21.11

First release of ANNOYING CLIENT.

- **ClickGUI:** draggable window, category sidebar, module cards with animated switches, expandable
  settings, search, tooltips, smooth scrolling, open/close and tab animations, Themes and Client tabs.
- **Settings:** boolean, number (slider), mode (dropdown), color (HSV picker with alpha, rainbow and hex),
  string and keybind (keyboard and mouse buttons 3-5), each with validation and JSON serialization.
- **HUD:** Watermark, FPS, Coordinates, Clock, Keystrokes, Armor HUD, CPS, Ping and Module List, each with
  position, scale, background and color settings. Drag-and-drop HUD editor with snapping.
- **Modules:** Fullbright, Custom Crosshair, Zoom, HUD Colors, Screenshot Manager, No Hurt Cam, Low Fire,
  Background FPS, Notifications, Profile Switcher, Theme Switcher, ClickGUI and HUD Editor.
- **Themes:** Midnight Purple, Dark Blue, Crimson, Emerald and Monochrome, with live customization of
  colors, opacity, animation speed, GUI scale, font, text shadow and blur.
- **Configuration:** Gson JSON profiles with auto-load, debounced atomic auto-save, and recovery from
  invalid or corrupted files.
- **Notifications:** animated toasts for module toggles, profiles, screenshots, errors and information.
- **Tests:** automated in-game test, run in both the development and the production environment.
