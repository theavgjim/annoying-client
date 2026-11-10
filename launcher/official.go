package main

import (
	"bytes"
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

//go:embed web/icon.png
var iconPNG []byte

const officialProfileKey = "annoying-client"

// PlayOfficial prepares the instance and hands the launch to the official Minecraft Launcher,
// which signs in with the player's Microsoft account. The launcher profile points at this
// launcher's game folder, so mods, settings and optimizations are shared between both modes.
func (g *Game) PlayOfficial(fabricProfile []byte, profileID, profileName string) error {
	mc := minecraftDir()
	if st, err := os.Stat(mc); err != nil || !st.IsDir() {
		return errors.New("the official Minecraft Launcher is not installed (no .minecraft folder). Install it from minecraft.net, start it once, then try again")
	}
	versionDir := filepath.Join(mc, "versions", fabricVersionID)
	if err := os.MkdirAll(versionDir, 0o755); err != nil {
		return err
	}
	if err := writeFileAtomic(filepath.Join(versionDir, fabricVersionID+".json"), fabricProfile); err != nil {
		return err
	}
	jar := filepath.Join(versionDir, fabricVersionID+".jar")
	if _, err := os.Stat(jar); errors.Is(err, fs.ErrNotExist) {
		_ = os.WriteFile(jar, nil, 0o644)
	}
	s := g.settings.Get()
	profile := map[string]any{
		"name":          officialName(profileID, profileName),
		"type":          "custom",
		"lastUsed":      time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"lastVersionId": fabricVersionID,
		"gameDir":       g.paths.Instance,
		"javaArgs":      strings.Join(jvmArgs(s), " "),
		"icon":          "data:image/png;base64," + base64.StdEncoding.EncodeToString(iconPNG),
	}
	if !s.Fullscreen {
		profile["resolution"] = map[string]int{"width": s.Width, "height": s.Height}
	}
	files := []string{filepath.Join(mc, "launcher_profiles.json")}
	if _, err := os.Stat(filepath.Join(mc, "launcher_profiles_microsoft_store.json")); err == nil {
		files = append(files, filepath.Join(mc, "launcher_profiles_microsoft_store.json"))
	}
	for _, f := range files {
		if err := upsertLauncherProfile(f, officialKey(profileID), profile); err != nil {
			return fmt.Errorf("cannot update %s: %w", filepath.Base(f), err)
		}
	}
	return startOfficialLauncher()
}

func officialKey(profileID string) string {
	if profileID == "default" {
		return officialProfileKey
	}
	return officialProfileKey + "-" + profileID
}

func officialName(profileID, profileName string) string {
	if profileID == "default" {
		return "Annoying Client"
	}
	return "Annoying Client - " + profileName
}

func upsertLauncherProfile(path, key string, profile map[string]any) error {
	root := map[string]any{}
	data, err := os.ReadFile(path)
	switch {
	case errors.Is(err, fs.ErrNotExist):
		root = map[string]any{"profiles": map[string]any{}, "settings": map[string]any{}, "version": 3}
	case err != nil:
		return err
	default:
		dec := json.NewDecoder(bytes.NewReader(data))
		dec.UseNumber()
		if err := dec.Decode(&root); err != nil {
			return fmt.Errorf("the file is not valid JSON; start the Minecraft Launcher once to repair it")
		}
		backup := path + ".annoying-client-backup"
		if _, err := os.Stat(backup); errors.Is(err, fs.ErrNotExist) {
			_ = os.WriteFile(backup, data, 0o644)
		}
	}
	profiles, _ := root["profiles"].(map[string]any)
	if profiles == nil {
		profiles = map[string]any{}
		root["profiles"] = profiles
	}
	if old, ok := profiles[key].(map[string]any); ok && old["created"] != nil {
		profile["created"] = old["created"]
	} else {
		profile["created"] = profile["lastUsed"]
	}
	profiles[key] = profile
	out, err := json.MarshalIndent(root, "", "  ")
	if err != nil {
		return err
	}
	return writeFileAtomic(path, out)
}

func startOfficialLauncher() error {
	switch runtime.GOOS {
	case "windows":
		var candidates []string
		for _, env := range []string{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"} {
			if base := os.Getenv(env); base != "" {
				candidates = append(candidates,
					filepath.Join(base, "Minecraft Launcher", "MinecraftLauncher.exe"),
					filepath.Join(base, "Programs", "Minecraft Launcher", "MinecraftLauncher.exe"))
			}
		}
		for _, c := range candidates {
			if _, err := os.Stat(c); err == nil {
				return exec.Command(c).Start()
			}
		}
		// Microsoft Store / Xbox app version of the launcher.
		return exec.Command("explorer.exe", `shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft`).Start()
	case "darwin":
		return exec.Command("open", "-a", "Minecraft").Start()
	default:
		if path, err := exec.LookPath("minecraft-launcher"); err == nil {
			return exec.Command(path).Start()
		}
		return errors.New("the profile was added; start the Minecraft Launcher and pick \"Annoying Client\"")
	}
}
