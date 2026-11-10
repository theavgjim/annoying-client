package main

import (
	"context"
	"crypto/subtle"
	"embed"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web
var webFS embed.FS

type App struct {
	paths    Paths
	settings *SettingsStore
	auth     *AuthManager
	mods     *ModManager
	game     *Game
	events   *EventHub
	token    string

	loginMu sync.Mutex
	login   *DeviceLogin
}

func (a *App) routes() http.Handler {
	mux := http.NewServeMux()
	static, _ := fs.Sub(webFS, "web")
	files := http.FileServer(http.FS(static))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" || r.URL.Path == "/index.html" {
			// The page must be opened with the session token; it is passed on to API calls.
			if subtle.ConstantTimeCompare([]byte(r.URL.Query().Get("t")), []byte(a.token)) != 1 {
				http.Error(w, "Open the launcher from AnnoyingClient.exe.", http.StatusForbidden)
				return
			}
			w.Header().Set("Cache-Control", "no-store")
		}
		files.ServeHTTP(w, r)
	})
	api := func(path string, h func(r *http.Request) (any, error)) {
		mux.HandleFunc(path, func(w http.ResponseWriter, r *http.Request) {
			if subtle.ConstantTimeCompare([]byte(r.Header.Get("X-Launcher-Token")), []byte(a.token)) != 1 {
				http.Error(w, "forbidden", http.StatusForbidden)
				return
			}
			out, err := h(r)
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("Cache-Control", "no-store")
			if err != nil {
				w.WriteHeader(http.StatusBadRequest)
				_ = json.NewEncoder(w).Encode(map[string]string{"error": capitalize(err.Error())})
				return
			}
			if out == nil {
				out = map[string]bool{"ok": true}
			}
			_ = json.NewEncoder(w).Encode(out)
		})
	}

	mux.HandleFunc("/api/events", a.handleEvents)
	api("/api/state", func(r *http.Request) (any, error) { return a.state(), nil })
	api("/api/settings", a.handleSettings)
	api("/api/login/start", a.handleLoginStart)
	api("/api/logout", func(r *http.Request) (any, error) { a.auth.Logout(); a.events.Send("state", nil); return nil, nil })
	api("/api/play", a.handlePlay)
	api("/api/stop", func(r *http.Request) (any, error) { return nil, a.game.Stop() })
	api("/api/logs", func(r *http.Request) (any, error) { return a.game.Logs(), nil })
	api("/api/mods", func(r *http.Request) (any, error) { return a.mods.List() })
	api("/api/mods/updates", func(r *http.Request) (any, error) { return a.mods.CheckUpdates(r.Context()) })
	api("/api/mods/update-all", a.handleUpdateAll)
	api("/api/mods/search", a.handleSearch)
	api("/api/mods/install", a.handleInstall)
	api("/api/mods/toggle", a.handleToggle)
	api("/api/mods/remove", a.handleRemove)
	api("/api/performance", a.handlePerformance)
	api("/api/performance/apply", a.handleApplyPerformance)
	api("/api/open", a.handleOpen)
	api("/api/repair", func(r *http.Request) (any, error) {
		if err := a.game.Repair(); err != nil {
			return nil, err
		}
		a.events.notify("success", "Game files reset", "They will be downloaded and verified on the next start")
		return nil, nil
	})
	return mux
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	return strings.ToUpper(s[:1]) + s[1:]
}

func decode(r *http.Request, v any) error {
	if r.Method != http.MethodPost {
		return errors.New("POST required")
	}
	return json.NewDecoder(http.MaxBytesReader(nil, r.Body, 1<<20)).Decode(v)
}

func (a *App) handleEvents(w http.ResponseWriter, r *http.Request) {
	if subtle.ConstantTimeCompare([]byte(r.URL.Query().Get("t")), []byte(a.token)) != 1 {
		http.Error(w, "forbidden", http.StatusForbidden)
		return
	}
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-store")
	c := a.events.subscribe()
	defer a.events.unsubscribe(c)
	fmt.Fprint(w, ": connected\n\n")
	flusher.Flush()
	ping := time.NewTicker(15 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case msg := <-c:
			fmt.Fprintf(w, "data: %s\n\n", msg)
			flusher.Flush()
		case <-ping.C:
			fmt.Fprint(w, ": ping\n\n")
			flusher.Flush()
		}
	}
}

func (a *App) state() map[string]any {
	s := a.settings.Get()
	var account any
	if acc := a.auth.Current(); acc != nil {
		account = map[string]string{"name": acc.Name, "uuid": acc.UUID}
	}
	mods, _ := a.mods.List()
	enabled := 0
	for _, m := range mods {
		if m.Enabled {
			enabled++
		}
	}
	a.loginMu.Lock()
	login := a.login
	a.loginMu.Unlock()
	_, officialErr := os.Stat(minecraftDir())
	return map[string]any{
		"version":          launcherVersion,
		"minecraftVersion": minecraftVersion,
		"loaderVersion":    loaderVersion,
		"settings":         s,
		"account":          account,
		"login":            login,
		"running":          a.game.Running(),
		"busy":             a.game.Busy(),
		"modsEnabled":      enabled,
		"modsTotal":        len(mods),
		"totalMemoryMB":    totalMemoryMB(),
		"recommendedMB":    recommendedMemoryMB(),
		"officialLauncher": officialErr == nil,
		"dataDir":          a.paths.Data,
		"os":               runtime.GOOS,
		"testAccount":      isTestBuild(),
	}
}

func (a *App) handleSettings(r *http.Request) (any, error) {
	if r.Method == http.MethodGet {
		return a.settings.Get(), nil
	}
	var in Settings
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	out, err := a.settings.Update(func(s *Settings) {
		fps := s.FPSPreset
		*s = in
		s.FPSPreset = fps
	})
	a.events.Send("state", nil)
	return out, err
}

func (a *App) handleLoginStart(r *http.Request) (any, error) {
	login, err := a.auth.StartLogin(a.settings.Get().ClientID, func(acc *Account, err error) {
		a.loginMu.Lock()
		a.login = nil
		a.loginMu.Unlock()
		if err != nil {
			a.events.notify("error", "Sign-in failed", capitalize(err.Error()))
		} else {
			a.events.notify("success", "Signed in", "Welcome, "+acc.Name)
		}
		a.events.Send("state", nil)
	})
	if err != nil {
		return nil, err
	}
	a.loginMu.Lock()
	a.login = login
	a.loginMu.Unlock()
	openURL(login.VerificationURI)
	return login, nil
}

func (a *App) handlePlay(r *http.Request) (any, error) {
	var in struct {
		Mode string `json:"mode"`
	}
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	if in.Mode == "" {
		in.Mode = a.settings.Get().LaunchMode
	}
	go a.play(in.Mode)
	return nil, nil
}

func (a *App) play(mode string) {
	ctx := context.Background()
	fail := func(err error) {
		logf("launch failed: %v", err)
		a.events.notify("error", "Could not start Minecraft", capitalize(err.Error()))
		a.events.Send("progress", nil)
		a.events.Send("state", nil)
	}
	switch mode {
	case "official":
		if err := a.game.begin(); err != nil {
			fail(err)
			return
		}
		err := func() error {
			defer a.game.end()
			if err := a.prepareOfficial(ctx); err != nil {
				return err
			}
			_, profile, err := fetchFabricProfile(ctx, a.paths)
			if err != nil {
				return err
			}
			return a.game.PlayOfficial(profile)
		}()
		if err != nil {
			fail(err)
			return
		}
		a.events.Send("progress", nil)
		a.events.notify("success", "Opening the Minecraft Launcher", "Pick the \"Annoying Client\" profile and press Play")
	default:
		acc, err := a.launchAccount(ctx)
		if err != nil {
			fail(err)
			return
		}
		err = a.game.Launch(ctx, acc, func(code int, d time.Duration) {
			if code != 0 && d < 2*time.Minute {
				a.events.notify("error", "Minecraft closed unexpectedly", fmt.Sprintf("Exit code %d. Open the Console tab for details.", code))
			}
		})
		if err != nil {
			fail(err)
			return
		}
		a.events.Send("progress", nil)
		a.events.notify("success", "Minecraft is starting", "Have fun!")
	}
}

// dataVersion returns the game's data version, downloading the client JAR if needed (cached after that).
func (a *App) dataVersion(ctx context.Context, report func(Progress)) (int, error) {
	jar := filepath.Join(a.paths.Versions, minecraftVersion, minecraftVersion+".jar")
	if v := worldVersion(jar); v > 0 {
		return v, nil
	}
	vanilla, err := fetchVanillaVersion(ctx, a.paths, minecraftVersion)
	if err != nil {
		return 0, err
	}
	c := vanilla.Downloads["client"]
	if err := downloadAll(ctx, "Downloading Minecraft", []DownloadJob{{URL: c.URL, Path: jar, SHA1: c.SHA1, Size: c.Size}}, 1, report); err != nil {
		return 0, err
	}
	if v := worldVersion(jar); v > 0 {
		return v, nil
	}
	return 0, errors.New("cannot read the game's data version")
}

// prepareOfficial does the part of Prepare that the official launcher does not do for us.
func (a *App) prepareOfficial(ctx context.Context) error {
	if err := a.paths.ensure(); err != nil {
		return err
	}
	a.game.progress(0, 3, Progress{Stage: "Installing Annoying Client"})
	if err := a.mods.EnsureCore(ctx); err != nil {
		return err
	}
	if _, err := os.Stat(filepath.Join(a.paths.Instance, "options.txt")); errors.Is(err, os.ErrNotExist) {
		a.game.progress(1, 3, Progress{Stage: "Applying the Balanced preset"})
		if dv, err := a.dataVersion(ctx, func(p Progress) { a.game.progress(1, 3, p) }); err == nil {
			if preset := findPreset("balanced"); preset != nil {
				_ = applyOptions(a.paths.Instance, preset.Options, dv)
				_, _ = a.settings.Update(func(s *Settings) { s.FPSPreset = "balanced" })
			}
		}
	}
	a.game.progress(2, 3, Progress{Stage: "Opening the Minecraft Launcher", Fraction: 1})
	return nil
}

func (a *App) handleSearch(r *http.Request) (any, error) {
	q := r.URL.Query()
	offset, _ := strconv.Atoi(q.Get("offset"))
	hits, total, err := a.mods.Search(r.Context(), q.Get("q"), q.Get("sort"), offset)
	if err != nil {
		return nil, fmt.Errorf("cannot reach Modrinth: %w", err)
	}
	return map[string]any{"hits": hits, "total": total}, nil
}

func (a *App) handleInstall(r *http.Request) (any, error) {
	var in struct {
		ProjectID string `json:"projectId"`
	}
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	if a.game.Running() {
		return nil, errors.New("close Minecraft before changing mods")
	}
	names, err := a.mods.Install(r.Context(), in.ProjectID)
	if err != nil {
		return nil, err
	}
	a.events.Send("state", nil)
	return names, nil
}

func (a *App) handleUpdateAll(r *http.Request) (any, error) {
	if a.game.Running() {
		return nil, errors.New("close Minecraft before updating mods")
	}
	names, err := a.mods.UpdateAll(r.Context())
	a.events.Send("state", nil)
	return names, err
}

func (a *App) handleToggle(r *http.Request) (any, error) {
	var in struct {
		File    string `json:"file"`
		Enabled bool   `json:"enabled"`
	}
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	if a.game.Running() {
		return nil, errors.New("close Minecraft before changing mods")
	}
	err := a.mods.SetEnabled(in.File, in.Enabled)
	a.events.Send("state", nil)
	return nil, err
}

func (a *App) handleRemove(r *http.Request) (any, error) {
	var in struct {
		File string `json:"file"`
	}
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	if a.game.Running() {
		return nil, errors.New("close Minecraft before changing mods")
	}
	err := a.mods.Remove(in.File)
	a.events.Send("state", nil)
	return nil, err
}

func (a *App) handlePerformance(r *http.Request) (any, error) {
	mods := make([]PerfMod, len(performanceMods))
	copy(mods, performanceMods)
	icons := a.perfIcons(r.Context())
	for i := range mods {
		mods[i].Installed = a.mods.HasProject(mods[i].ProjectID)
		mods[i].IconURL = icons[mods[i].ProjectID]
	}
	return map[string]any{"mods": mods, "presets": fpsPresets, "current": a.settings.Get().FPSPreset}, nil
}

var (
	perfIconMu    sync.Mutex
	perfIconCache map[string]string
)

// perfIcons fetches the performance mods' icon URLs from Modrinth once per session.
func (a *App) perfIcons(ctx context.Context) map[string]string {
	perfIconMu.Lock()
	defer perfIconMu.Unlock()
	if perfIconCache != nil {
		return perfIconCache
	}
	ids := make([]string, len(performanceMods))
	for i, m := range performanceMods {
		ids[i] = m.ProjectID
	}
	raw, _ := json.Marshal(ids)
	var projects []modrinthProject
	if err := getJSON(ctx, modrinthAPI+"/projects?ids="+url.QueryEscape(string(raw)), &projects); err != nil {
		return map[string]string{}
	}
	perfIconCache = map[string]string{}
	for _, p := range projects {
		perfIconCache[p.ID] = p.IconURL
	}
	return perfIconCache
}

func (a *App) handleApplyPerformance(r *http.Request) (any, error) {
	var in struct {
		Preset string   `json:"preset"`
		Mods   []string `json:"mods"` // project IDs that should be installed
	}
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	if a.game.Running() {
		return nil, errors.New("close Minecraft before optimizing")
	}
	var installed []string
	want := map[string]bool{}
	for _, id := range in.Mods {
		want[id] = true
	}
	for _, pm := range performanceMods {
		has := a.mods.HasProject(pm.ProjectID)
		switch {
		case want[pm.ProjectID] && !has:
			a.events.Send("progress", map[string]any{"stage": "Installing " + pm.Name, "fraction": 0.5})
			names, err := a.mods.Install(r.Context(), pm.ProjectID)
			if err != nil {
				a.events.Send("progress", nil)
				return nil, fmt.Errorf("%s: %w", pm.Name, err)
			}
			installed = append(installed, names...)
		case !want[pm.ProjectID] && has:
			if err := a.mods.RemoveProject(pm.ProjectID); err != nil {
				return nil, err
			}
		}
	}
	a.events.Send("progress", nil)
	if in.Preset != "" {
		preset := findPreset(in.Preset)
		if preset == nil {
			return nil, errors.New("unknown preset")
		}
		dv, err := a.dataVersion(r.Context(), func(p Progress) {
			a.events.Send("progress", map[string]any{"stage": "Downloading Minecraft (for its settings format)", "fraction": p.Fraction})
		})
		a.events.Send("progress", nil)
		if err != nil {
			return nil, fmt.Errorf("cannot apply the preset: %w", err)
		}
		if err := applyOptions(a.paths.Instance, preset.Options, dv); err != nil {
			return nil, err
		}
		_, _ = a.settings.Update(func(s *Settings) { s.FPSPreset = in.Preset })
	}
	a.events.Send("state", nil)
	return map[string]any{"installed": installed}, nil
}

func (a *App) handleOpen(r *http.Request) (any, error) {
	var in struct {
		What string `json:"what"`
	}
	if err := decode(r, &in); err != nil {
		return nil, err
	}
	dirs := map[string]string{
		"game": a.paths.Instance, "mods": a.paths.Mods, "data": a.paths.Data,
		"screenshots": filepath.Join(a.paths.Instance, "screenshots"), "logs": filepath.Join(a.paths.Instance, "logs"),
	}
	if in.What == "approval" {
		openURL("https://aka.ms/mce-reviewappid")
		return nil, nil
	}
	if in.What == "azure" {
		openURL("https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade")
		return nil, nil
	}
	dir, ok := dirs[in.What]
	if !ok {
		return nil, errors.New("unknown folder")
	}
	_ = os.MkdirAll(dir, 0o755)
	openURL(dir)
	return nil, nil
}

// openURL opens a web address or folder with the system's default handler.
func openURL(target string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", target)
	case "darwin":
		cmd = exec.Command("open", target)
	default:
		cmd = exec.Command("xdg-open", target)
	}
	if err := cmd.Start(); err != nil {
		logf("cannot open %s: %v", target, err)
	}
}
