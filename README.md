# ANNOYING CLIENT

A modular quality-of-life and visual client for **Minecraft Java Edition**, built on **Fabric**.

> Status: **Stage 1 of 8 complete** (project setup). The ClickGUI, HUD, modules and
> configuration system are being built in the stages listed under [Roadmap](#roadmap).

## Versions

| Component      | Version            | Why                                                                 |
|----------------|--------------------|---------------------------------------------------------------------|
| Minecraft      | 1.21.11            | Newest release that runs on Java 21 with full Fabric support        |
| Fabric Loader  | 0.20.1             | Latest stable loader                                                |
| Fabric API     | 0.141.6+1.21.11    | Latest Fabric API build for 1.21.11                                 |
| Fabric Loom    | 1.14.10            | Loom line for 1.21.11 (needs Gradle 9.2+)                           |
| Gradle         | 9.2.1 (wrapper)    | Required by Loom 1.14                                               |
| Java           | 21                 | Required by Minecraft 1.21.x                                        |
| Mappings       | Official Mojang    | The names Minecraft 26.x ships with, so a later port stays small    |

Gson ships with Minecraft, so configuration needs no extra dependency.

## Building

Requirements: a **JDK 21** on your `PATH` (or `JAVA_HOME`). Gradle is provided by the wrapper.

```bash
./gradlew build
```

The mod JAR is written to `build/libs/annoying-client-<version>+mc1.21.11.jar`.

## Running a development instance

```bash
./gradlew runClient
```

The first run downloads Minecraft, its libraries and assets, which takes a few minutes.

## IntelliJ IDEA

1. *File → Open* and choose this folder's `build.gradle`, then *Open as Project*.
2. Set the project SDK to Java 21 (*File → Project Structure → Project*).
3. Wait for the Gradle sync. Loom generates a **Minecraft Client** run configuration.
4. Optional: run `./gradlew genSources` to get readable, commented Minecraft sources.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Put the Annoying Client JAR **and** [Fabric API](https://modrinth.com/mod/fabric-api) (1.21.11 build) into `.minecraft/mods`.
3. Start the *fabric-loader-1.21.11* profile in the launcher.

Settings are stored in `.minecraft/config/annoyingclient/`.

## Project layout

```
annoying-client/
├── build.gradle, settings.gradle, gradle.properties   Gradle + Loom build
├── gradlew, gradle/wrapper/                           Gradle 9.2.1 wrapper
└── src/main/
    ├── java/com/annoyingclient/
    │   ├── AnnoyingClient.java    Client entry point (ClientModInitializer)
    │   ├── core/                  Lifecycle and startup order
    │   ├── event/                 Event bus and event types
    │   ├── manager/               Module, keybind, HUD, theme, notification, config managers
    │   ├── module/                Module base class and categories
    │   │   └── impl/              Concrete modules
    │   ├── setting/               Typed, serializable settings
    │   ├── gui/clickgui/          The ClickGUI screen
    │   ├── gui/component/         Reusable widgets
    │   ├── hud/                   HUD manager, base element, HUD editor
    │   │   └── element/           Concrete HUD elements
    │   ├── config/                Gson JSON configuration and profiles
    │   ├── theme/                 Themes and live customization
    │   ├── notification/          Animated notifications
    │   ├── render/                Rounded rectangles, shadows, text helpers
    │   ├── util/                  Animation and color utilities
    │   └── mixin/                 Mixins into Minecraft classes
    └── resources/
        ├── fabric.mod.json              Mod metadata (id: annoyingclient)
        ├── annoyingclient.mixins.json   Mixin configuration
        └── assets/annoyingclient/       Icon, language files, textures
```

## Roadmap

- [x] **Stage 1 — Project setup:** Gradle, Fabric, dependencies, structure, initializer, launches in dev
- [ ] **Stage 2 — Core architecture:** module framework, module manager, settings, keybinds, events
- [ ] **Stage 3 — ClickGUI:** window, categories, modules, settings components, search, animations, themes
- [ ] **Stage 4 — HUD:** HUD manager, elements, HUD editor, drag-and-drop, scaling
- [ ] **Stage 5 — Configuration:** JSON serialization, auto save/load, profiles
- [ ] **Stage 6 — Modules:** all initial modules connected to settings and keybinds
- [ ] **Stage 7 — Polish:** animations, fonts, colors, notifications, performance
- [ ] **Stage 8 — Release:** JAR, clean-install test, documentation, versioned releases

## Fair play

Annoying Client contains only visual and quality-of-life features. It has no cheats,
no anti-cheat bypasses, no exploits and no gameplay automation.

## License

MIT. See [LICENSE](LICENSE).
