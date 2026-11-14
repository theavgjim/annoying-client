package main

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// LaunchAccount is the identity passed to the game.
type LaunchAccount struct {
	Name, UUID, AccessToken, UserType string
}

// Game prepares files and runs Minecraft.
type Game struct {
	paths    Paths
	settings *SettingsStore
	mods     *ModManager
	events   *EventHub

	mu      sync.Mutex
	busy    bool
	cmd     *exec.Cmd
	started time.Time
	logs    []string
}

func newGame(p Paths, s *SettingsStore, m *ModManager, e *EventHub) *Game {
	return &Game{paths: p, settings: s, mods: m, events: e}
}

func (g *Game) Running() bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.cmd != nil
}

func (g *Game) Busy() bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.busy || g.cmd != nil
}

func (g *Game) Logs() []string {
	g.mu.Lock()
	defer g.mu.Unlock()
	return append([]string{}, g.logs...)
}

func (g *Game) addLog(line string) {
	g.mu.Lock()
	g.logs = append(g.logs, line)
	if len(g.logs) > 4000 {
		g.logs = append([]string(nil), g.logs[len(g.logs)-3000:]...)
	}
	g.mu.Unlock()
	g.events.Send("log", line)
}

func (g *Game) begin() error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.cmd != nil {
		return errors.New("minecraft is already running")
	}
	if g.busy {
		return errors.New("another task is already running")
	}
	g.busy = true
	return nil
}

func (g *Game) end() {
	g.mu.Lock()
	g.busy = false
	g.mu.Unlock()
	g.events.Send("state", nil)
}

func (g *Game) progress(step, steps int, p Progress) {
	overall := (float64(step) + p.Fraction) / float64(steps)
	g.events.Send("progress", map[string]any{"stage": p.Stage, "fraction": overall, "done": p.Done, "total": p.Total, "bytes": p.Bytes, "totalBytes": p.TotalBytes})
}

// prepared holds everything needed to start the game.
type prepared struct {
	version   *VersionJSON
	classpath []string
	java      string
}

// launchSpec says which game folder and mods to start with. The normal launch uses the active profile;
// "Normal FPS Boosted" uses the default profile without Annoying Client (see plain.go).
type launchSpec struct {
	paths       Paths
	mods        *ModManager
	extraJVM    []string     // added after the memory arguments
	versionType string       // shown on the title screen
	beforeStart func() error // runs after the downloads, before Java starts
}

func (g *Game) defaultSpec() launchSpec {
	return launchSpec{paths: g.paths, mods: g.mods, versionType: "Annoying Client"}
}

// Prepare downloads and verifies the game, libraries, assets, Java and core mods.
func (g *Game) Prepare(ctx context.Context) (*prepared, error) {
	return g.prepare(ctx, g.defaultSpec())
}

func (g *Game) prepare(ctx context.Context, spec launchSpec) (*prepared, error) {
	const steps = 6
	s := g.settings.Get()
	paths := spec.paths
	if err := paths.ensure(); err != nil {
		return nil, err
	}
	g.progress(0, steps, Progress{Stage: "Checking game version"})
	vanilla, err := fetchVanillaVersion(ctx, paths, minecraftVersion)
	if err != nil {
		return nil, err
	}
	fabric, _, err := fetchFabricProfile(ctx, paths)
	if err != nil {
		return nil, err
	}
	v := mergeVersions(vanilla, fabric)

	clientJarPath := filepath.Join(paths.Versions, minecraftVersion, minecraftVersion+".jar")
	client, ok := vanilla.Downloads["client"]
	if !ok {
		return nil, errors.New("mojang's version file has no client download")
	}
	jobs := []DownloadJob{{URL: client.URL, Path: clientJarPath, SHA1: client.SHA1, Size: client.Size}}
	libJobs, classpath, err := libraryJobs(paths, v)
	if err != nil {
		return nil, err
	}
	jobs = append(jobs, libJobs...)
	if err := downloadAll(ctx, "Downloading game files", jobs, s.DownloadThreads, func(p Progress) { g.progress(1, steps, p) }); err != nil {
		return nil, err
	}
	classpath = append(classpath, clientJarPath)

	g.progress(2, steps, Progress{Stage: "Checking assets"})
	assets, err := assetJobs(ctx, paths, vanilla)
	if err != nil {
		return nil, err
	}
	if err := downloadAll(ctx, "Downloading assets", assets, s.DownloadThreads, func(p Progress) { g.progress(2, steps, p) }); err != nil {
		return nil, err
	}

	java := strings.TrimSpace(s.JavaPath)
	if java == "" {
		g.progress(3, steps, Progress{Stage: "Checking Java"})
		java, err = ensureJava(ctx, paths, vanilla.JavaVersion.Component, s.DownloadThreads, func(p Progress) { g.progress(3, steps, p) })
		if err != nil {
			return nil, err
		}
	} else if _, err := os.Stat(java); err != nil {
		return nil, fmt.Errorf("the Java path in Settings does not exist: %s", java)
	}

	g.progress(4, steps, Progress{Stage: "Installing Annoying Client"})
	if err := spec.mods.EnsureCore(ctx); err != nil {
		return nil, fmt.Errorf("cannot install core mods: %w", err)
	}
	if _, err := os.Stat(filepath.Join(paths.Instance, "options.txt")); errors.Is(err, os.ErrNotExist) {
		// First start: use the Balanced preset so the game runs well out of the box.
		if preset := findPreset("balanced"); preset != nil {
			_ = applyOptions(paths.Instance, preset.Options, worldVersion(clientJarPath))
			_, _ = g.settings.Update(func(s *Settings) { s.FPSPreset = "balanced" })
		}
	}
	g.progress(5, steps, Progress{Stage: "Ready", Fraction: 1})
	return &prepared{version: v, classpath: classpath, java: java}, nil
}

func classpathSeparator() string {
	if os.PathListSeparator == ';' {
		return ";"
	}
	return ":"
}

// buildArgs expands the version's argument templates for this account and these settings.
func (g *Game) buildArgs(p *prepared, acc LaunchAccount, s Settings, spec launchSpec) []string {
	vars := map[string]string{
		"auth_player_name":    acc.Name,
		"version_name":        p.version.ID,
		"game_directory":      spec.paths.Instance,
		"assets_root":         g.paths.Assets,
		"assets_index_name":   p.version.AssetIndex.ID,
		"auth_uuid":           acc.UUID,
		"auth_access_token":   acc.AccessToken,
		"clientid":            "",
		"auth_xuid":           "",
		"user_type":           acc.UserType,
		"version_type":        spec.versionType,
		"natives_directory":   g.paths.Natives,
		"launcher_name":       "annoying-client",
		"launcher_version":    launcherVersion,
		"classpath":           strings.Join(p.classpath, classpathSeparator()),
		"classpath_separator": classpathSeparator(),
		"library_directory":   g.paths.Libraries,
		"resolution_width":    fmt.Sprint(s.Width),
		"resolution_height":   fmt.Sprint(s.Height),
	}
	features := map[string]bool{"has_custom_resolution": !s.Fullscreen}
	expand := func(raw []json.RawMessage) []string {
		var out []string
		for _, r := range raw {
			var str string
			if json.Unmarshal(r, &str) == nil {
				out = append(out, substitute(str, vars))
				continue
			}
			var cond struct {
				Rules []Rule          `json:"rules"`
				Value json.RawMessage `json:"value"`
			}
			if json.Unmarshal(r, &cond) != nil || !rulesAllow(cond.Rules, features) {
				continue
			}
			var single string
			var multi []string
			if json.Unmarshal(cond.Value, &single) == nil {
				out = append(out, substitute(single, vars))
			} else if json.Unmarshal(cond.Value, &multi) == nil {
				for _, m := range multi {
					out = append(out, substitute(m, vars))
				}
			}
		}
		return out
	}
	args := jvmArgs(s)
	args = append(args, spec.extraJVM...)
	args = append(args, expand(p.version.Arguments.JVM)...)
	args = append(args, p.version.MainClass)
	args = append(args, expand(p.version.Arguments.Game)...)
	if s.Fullscreen {
		args = append(args, "--fullscreen")
	}
	return args
}

func substitute(s string, vars map[string]string) string {
	for k, v := range vars {
		s = strings.ReplaceAll(s, "${"+k+"}", v)
	}
	return s
}

// redactArgs hides the access token in logs.
func redactArgs(args []string, token string) string {
	out := make([]string, len(args))
	for i, a := range args {
		if len(token) >= 16 && strings.Contains(a, token) {
			a = strings.ReplaceAll(a, token, "<access token>")
		}
		if strings.Contains(a, classpathSeparator()) && len(a) > 300 {
			a = "<classpath>"
		}
		out[i] = a
	}
	return strings.Join(out, " ")
}

// Launch starts Minecraft directly with the given account. It returns once the process has started.
func (g *Game) Launch(ctx context.Context, acc LaunchAccount, onExit func(code int, runtime time.Duration)) error {
	return g.LaunchSpec(ctx, acc, g.defaultSpec(), onExit)
}

// LaunchSpec starts the game with the given folders and extra arguments.
func (g *Game) LaunchSpec(ctx context.Context, acc LaunchAccount, spec launchSpec, onExit func(code int, runtime time.Duration)) error {
	if err := g.begin(); err != nil {
		return err
	}
	defer g.end()
	p, err := g.prepare(ctx, spec)
	if err != nil {
		return err
	}
	if spec.beforeStart != nil {
		if err := spec.beforeStart(); err != nil {
			return err
		}
	}
	s := g.settings.Get()
	args := g.buildArgs(p, acc, s, spec)
	cmd := exec.Command(p.java, args...)
	cmd.Dir = spec.paths.Instance
	hideConsole(cmd)
	stdout, _ := cmd.StdoutPipe()
	stderr, _ := cmd.StderrPipe()
	g.mu.Lock()
	g.logs = nil
	g.mu.Unlock()
	g.addLog("[launcher] " + filepath.Base(p.java) + " " + redactArgs(args, acc.AccessToken))
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("cannot start Java: %w", err)
	}
	g.mu.Lock()
	g.cmd = cmd
	g.started = time.Now()
	g.mu.Unlock()
	logf("game started (pid %d) as %s", cmd.Process.Pid, acc.Name)
	g.events.Send("state", nil)

	var wg sync.WaitGroup
	pump := func(r io.Reader) {
		defer wg.Done()
		sc := bufio.NewScanner(r)
		sc.Buffer(make([]byte, 64*1024), 1024*1024)
		for sc.Scan() {
			line := sc.Text()
			if len(acc.AccessToken) >= 16 {
				line = strings.ReplaceAll(line, acc.AccessToken, "<access token>")
			}
			g.addLog(line)
		}
	}
	wg.Add(2)
	go pump(stdout)
	go pump(stderr)
	go func() {
		wg.Wait()
		err := cmd.Wait()
		code := 0
		if err != nil {
			var ee *exec.ExitError
			if errors.As(err, &ee) {
				code = ee.ExitCode()
			} else {
				code = -1
			}
		}
		g.mu.Lock()
		dur := time.Since(g.started)
		g.cmd = nil
		g.mu.Unlock()
		g.addLog(fmt.Sprintf("[launcher] Minecraft exited with code %d after %s", code, dur.Round(time.Second)))
		logf("game exited with code %d", code)
		g.events.Send("state", nil)
		if onExit != nil {
			onExit(code, dur)
		}
	}()
	return nil
}

func (g *Game) Stop() error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.cmd == nil || g.cmd.Process == nil {
		return errors.New("minecraft is not running")
	}
	return g.cmd.Process.Kill()
}

// Repair deletes downloaded game files so they are fetched and verified again on the next start.
func (g *Game) Repair() error {
	if err := g.begin(); err != nil {
		return err
	}
	defer g.end()
	for _, dir := range []string{g.paths.Versions, g.paths.Libraries, filepath.Join(g.paths.Assets, "indexes"), g.paths.Runtime} {
		if err := os.RemoveAll(dir); err != nil {
			return err
		}
	}
	return g.paths.ensure()
}
