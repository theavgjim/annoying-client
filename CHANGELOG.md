# Changelog

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
