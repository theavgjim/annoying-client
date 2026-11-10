package main

import (
	"os"
	"path/filepath"
	"runtime"
)

// Paths is the launcher's data layout. Everything lives in one folder so it can be moved or deleted cleanly:
//
//	<data>/runtime     Java runtimes downloaded from Mojang
//	<data>/libraries   game and Fabric libraries
//	<data>/assets      game assets (sounds, languages, icons)
//	<data>/versions    version JSON and client JAR
//	<data>/instance    the game directory: mods, saves, options.txt, screenshots...
type Paths struct {
	Data, Runtime, Libraries, Assets, Versions, Instance, Mods, Logs, Natives string
}

func defaultDataDir() string {
	home, _ := os.UserHomeDir()
	switch runtime.GOOS {
	case "windows":
		if appData := os.Getenv("APPDATA"); appData != "" {
			return filepath.Join(appData, ".annoyingclient")
		}
		return filepath.Join(home, "AppData", "Roaming", ".annoyingclient")
	case "darwin":
		return filepath.Join(home, "Library", "Application Support", "AnnoyingClient")
	default:
		return filepath.Join(home, ".annoyingclient")
	}
}

func newPaths(data string) Paths {
	p := Paths{
		Data:      data,
		Runtime:   filepath.Join(data, "runtime"),
		Libraries: filepath.Join(data, "libraries"),
		Assets:    filepath.Join(data, "assets"),
		Versions:  filepath.Join(data, "versions"),
		Instance:  filepath.Join(data, "instance"),
		Logs:      filepath.Join(data, "logs"),
	}
	p.Mods = filepath.Join(p.Instance, "mods")
	p.Natives = filepath.Join(data, "natives")
	return p
}

// withInstance returns the paths with the game folder switched to dir (one per profile).
func (p Paths) withInstance(dir string) Paths {
	p.Instance = dir
	p.Mods = filepath.Join(dir, "mods")
	return p
}

func (p Paths) ensure() error {
	for _, d := range []string{p.Data, p.Runtime, p.Libraries, p.Assets, p.Versions, p.Instance, p.Mods, p.Logs, p.Natives} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			return err
		}
	}
	return nil
}

// minecraftDir is the official launcher's folder (used for the "Play via Minecraft Launcher" mode).
func minecraftDir() string {
	home, _ := os.UserHomeDir()
	switch runtime.GOOS {
	case "windows":
		if appData := os.Getenv("APPDATA"); appData != "" {
			return filepath.Join(appData, ".minecraft")
		}
		return filepath.Join(home, "AppData", "Roaming", ".minecraft")
	case "darwin":
		return filepath.Join(home, "Library", "Application Support", "minecraft")
	default:
		return filepath.Join(home, ".minecraft")
	}
}
