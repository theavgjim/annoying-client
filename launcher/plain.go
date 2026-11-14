package main

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// "Normal FPS Boosted Minecraft": the selected profile's game folder, settings and mods, started WITHOUT
// the Annoying Client mod. The mods folder itself is never changed: the enabled mods are
// linked (or copied) into a separate folder, and Fabric is pointed at it with -Dfabric.modsFolder.

// plainModsFolder lives inside the game folder and is passed as a relative path: the game always runs
// with the game folder as its working directory, and a relative path never has spaces in it (Windows user
// folders often do, and the official launcher splits Java arguments on spaces).
const plainModsFolder = "mods-without-annoying-client"

const plainOfficialKey = officialProfileKey + "-plain"
const plainOfficialName = "Normal FPS Boosted (no Annoying Client)"

// isClientMod reports whether a mod file is Annoying Client itself.
func isClientMod(path string) bool {
	name := filepath.Base(path)
	if strings.HasPrefix(name, "annoying-client-") {
		return true
	}
	id, _, _, _ := readModInfo(path)
	return id == "annoyingclient"
}

// syncPlainMods makes dst hold exactly the enabled mods of src except Annoying Client. Returns their names.
func syncPlainMods(src, dst string) ([]string, error) {
	if err := os.MkdirAll(dst, 0o755); err != nil {
		return nil, err
	}
	old, _ := os.ReadDir(dst)
	for _, e := range old {
		if !e.IsDir() {
			_ = os.Remove(filepath.Join(dst, e.Name()))
		}
	}
	entries, err := os.ReadDir(src)
	if err != nil {
		return nil, err
	}
	var names []string
	for _, e := range entries {
		n := e.Name()
		if e.IsDir() || !strings.HasSuffix(n, ".jar") { // .jar.disabled stays off
			continue
		}
		from := filepath.Join(src, n)
		if isClientMod(from) {
			continue
		}
		to := filepath.Join(dst, n)
		if err := os.Link(from, to); err != nil {
			if err := copyFile(from, to); err != nil {
				return nil, fmt.Errorf("cannot prepare %s: %w", n, err)
			}
		}
		names = append(names, n)
	}
	sort.Strings(names)
	return names, nil
}

func copyFile(from, to string) error {
	in, err := os.Open(from)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(to + ".tmp")
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		return err
	}
	if err := out.Close(); err != nil {
		return err
	}
	return os.Rename(to+".tmp", to)
}

// plainTarget returns the selected profile's paths and mod manager: the profile the player uses Annoying
// Client with. Sharing its game folder keeps every setting (options, keybinds, video and mod settings,
// resource packs, servers, worlds) the same in both modes, in both directions.
func (a *App) plainTarget() (Paths, *ModManager, Profile, error) {
	a.profileMu.Lock()
	defer a.profileMu.Unlock()
	return a.paths, a.mods, a.profiles.ActiveProfile(), nil
}

// plainInfo lists the mods the plain mode will start with (for the launcher page).
func (a *App) plainInfo() (any, error) {
	_, mods, profile, err := a.plainTarget()
	if err != nil {
		return nil, err
	}
	list, err := mods.List()
	if err != nil {
		return nil, err
	}
	var out []map[string]any
	for _, m := range list {
		if !m.Enabled || isClientMod(filepath.Join(mods.dir, m.File)) {
			continue
		}
		out = append(out, map[string]any{"name": m.Name, "version": m.Version, "file": m.File, "icon": m.IconURL})
	}
	return map[string]any{"profile": profile.Name, "mods": out}, nil
}

// playPlain starts "Normal FPS Boosted Minecraft" through the official Minecraft Launcher: it adds (or
// updates) a "Normal FPS Boosted (no Annoying Client)" profile there and opens the launcher.
func (a *App) playPlain() {
	ctx := context.Background()
	fail := func(err error) {
		logf("plain launch failed: %v", err)
		a.events.notify("error", "Could not start Minecraft", capitalize(err.Error()))
		a.events.Send("progress", nil)
		a.events.Send("state", nil)
	}
	paths, mods, _, err := a.plainTarget()
	if err != nil {
		fail(err)
		return
	}
	if err := a.game.begin(); err != nil {
		fail(err)
		return
	}
	err = func() error {
		defer a.game.end()
		if err := a.prepareOfficialFor(ctx, paths, mods); err != nil {
			return err
		}
		names, err := syncPlainMods(paths.Mods, filepath.Join(paths.Instance, plainModsFolder))
		if err != nil {
			return err
		}
		logf("plain mode: %d mods without Annoying Client", len(names))
		_, profile, err := fetchFabricProfile(ctx, paths)
		if err != nil {
			return err
		}
		return a.game.playOfficialProfile(profile, plainOfficialKey, plainOfficialName, paths.Instance, []string{"-Dfabric.modsFolder=" + plainModsFolder})
	}()
	if err != nil {
		fail(err)
		return
	}
	a.events.Send("progress", nil)
	a.events.notify("success", "Opening the Minecraft Launcher", "Pick the \""+plainOfficialName+"\" profile and press Play")
}
