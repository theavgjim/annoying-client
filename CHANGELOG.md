# Changelog

## 1.3.2 (Minecraft 1.21.11)

- **Removed the Watermark and Module List** HUD elements and the title-screen watermark, so the HUD looks like a
  normal client. The FPS, Coordinates, Ping and CPS displays move up into the free space.
- **Normal FPS Boosted Minecraft always opens through the Minecraft Launcher** (no Microsoft sign-in needed in
  this launcher): pick the "Normal FPS Boosted (no Annoying Client)" profile there and press Play.

## 1.3.1 (Minecraft 1.21.11)

- **Normal FPS Boosted Minecraft** (launcher sidebar): starts Minecraft with your mods from the
  Annoying Client profile, but without the Annoying Client mod. Same worlds, options and FPS settings. Clicking
  it starts the game right away. Works with Direct launch and with the Minecraft Launcher (adds a
  "Normal FPS Boosted (no Annoying Client)" profile there). Your mods folder is never changed: the enabled
  mods are linked into a separate folder and Fabric is pointed at it.

## 1.3.0 (Minecraft 1.21.11)

- **Mod Configs: changes apply without restarting.** Saving a config file reloads it into the running mod for
  mods built on Cloth Config or YetAnotherConfigLib, and for Sodium and Iris. Every file shows how it applies:
  "Applies instantly", "Needs a restart" (mixin switches such as Lithium's, which no mod can change while
  running) or "Applies on restart" (then the mod's Settings button changes it instantly).
- **Mod Configs: more features.** Favorites (star a mod to pin it to the top), filters (All, With settings,
  Favorites), libraries hidden unless switched on, search by name, author or description, a details panel with
  the description, license and Website / Issues / Source links, "Open folder", and Undo to put a file back
  as it was. Sodium, Sodium Extra and Reese's Sodium Options get a Settings button (Video Settings).
- **Coins and cosmetics are kept.** The cosmetics server can back its database up to a secret GitHub gist
  (`GITHUB_TOKEN`), so the free host's restarts no longer reset it. The client signs back in by itself
  after a server restart.
- **Saturation** (Visual): gold outlines on the hunger bar show your saturation, flashing drumsticks show what
  the food in your hand restores, and "Show Numbers" writes the exact values above the bar.
- **Potion Effects HUD**: your active effects with icons, level and time left (red when harmful, blinking for
  the last 10 seconds). Sort by time, name or good-first; hides the vanilla corner icons while it's on.
- Removed the wavy capes option. Thin capes stay.

## 1.2.2 (Minecraft 1.21.11)

- **Thin capes** (on by default): every cape is drawn about a third as thick as vanilla.
- A toggle under the player preview in the Wardrobe, and in Visual > Cosmetics.

## 1.2.1 (Minecraft 1.21.11)

- **Official cosmetics server:** the client connects to `https://annoying-client.onrender.com` by default,
  so capes, coins and the Wardrobe work without setup. Type `off` as the Server URL to disable it.
- Sign-in waits up to 70 seconds for the free server to wake up, and retries every 2 minutes after a failure.

## 1.2.0 (Minecraft 1.21.11)

- **Launcher auto-update:** the launcher checks `docs/update.json` at startup and every 6 hours and shows
  "Update available" on the Play page. One click downloads the new exe, verifies its SHA-256, swaps it in
  and restarts. A failed or tampered download leaves the current launcher untouched. Can be turned off in Settings.
- **Owner label:** accounts in the server's `ADMINS` list show as "Owner" in the Wardrobe and the admin player list.
- **release.sh:** one command sets the version, builds everything and publishes the update.

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
