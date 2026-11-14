package main

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// "Normal FPS Boosted Minecraft": the selected profile's game folder, settings and mods, started WITHOUT
// the Annoying Client mod. The mods folder itself is never changed: the enabled mods are
// linked (or copied) into a separate folder, and Fabric is pointed at it with -Dfabric.modsFolder.

// plainModsFolder lives inside the game folder.
const plainModsFolder = "mods-without-annoying-client"

// plainOfficialKey is the Minecraft Launcher profile that versions 1.3.2-1.3.3 created for this mode.
const plainOfficialKey = officialProfileKey + "-plain"

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

// playPlain starts "Normal FPS Boosted Minecraft" directly from this launcher (like Direct play), with the
// signed-in Microsoft account, the selected profile's game folder and its mods minus Annoying Client.
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
	acc, err := a.launchAccount(ctx)
	if err != nil {
		fail(err)
		return
	}
	// Versions 1.3.2 and 1.3.3 added a profile for this mode to the Minecraft Launcher; it is not used any more.
	removeOfficialProfile(plainOfficialKey)
	dst := filepath.Join(paths.Instance, plainModsFolder)
	spec := launchSpec{
		paths:       paths,
		mods:        mods,
		extraJVM:    []string{"-Dfabric.modsFolder=" + dst},
		versionType: "Fabric",
		beforeStart: func() error {
			names, err := syncPlainMods(paths.Mods, dst)
			if err != nil {
				return err
			}
			logf("plain mode: %d mods without Annoying Client", len(names))
			return nil
		},
	}
	err = a.game.LaunchSpec(ctx, acc, spec, func(code int, d time.Duration) {
		if code != 0 && d < 2*time.Minute {
			a.events.notify("error", "Minecraft closed unexpectedly", fmt.Sprintf("Exit code %d. Open the Console tab for details.", code))
		}
	})
	if err != nil {
		fail(err)
		return
	}
	a.events.Send("progress", nil)
	a.events.notify("success", "Minecraft is starting", "Normal FPS Boosted: your mods and settings, without Annoying Client")
}
