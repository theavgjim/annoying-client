package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
)

// The update manifest lives in the GitHub repository; the release script rewrites it:
//
//	{"version": "1.2.0", "notes": "...", "published": "2026-11-10",
//	 "windows": {"url": "https://.../AnnoyingClient.exe", "sha256": "...", "size": 8220160}}
const defaultUpdateURL = "https://raw.githubusercontent.com/theavgjim/annoying-client/main/docs/update.json"

type updateAsset struct {
	URL    string `json:"url"`
	SHA256 string `json:"sha256"`
	Size   int64  `json:"size"`
}

type updateManifest struct {
	Version   string       `json:"version"`
	Notes     string       `json:"notes"`
	Published string       `json:"published"`
	Windows   *updateAsset `json:"windows"`
	Linux     *updateAsset `json:"linux"`
	Darwin    *updateAsset `json:"darwin"`
}

// UpdateInfo is what the UI shows when a newer launcher exists.
type UpdateInfo struct {
	Version   string `json:"version"`
	Notes     string `json:"notes"`
	Published string `json:"published"`
	asset     updateAsset
}

type Updater struct {
	mu        sync.Mutex
	url       string
	available *UpdateInfo
	lastCheck time.Time
	lastErr   string
	restart   chan struct{}
}

func newUpdater() *Updater {
	url := os.Getenv("ANNOYING_UPDATE_URL") // tests point this at a local manifest
	if url == "" {
		url = defaultUpdateURL
	}
	return &Updater{url: url, restart: make(chan struct{}, 1)}
}

// versionNewer reports whether a is a higher dotted version than b ("1.10.0" > "1.9.2").
func versionNewer(a, b string) bool {
	pa, pb := strings.Split(strings.TrimPrefix(a, "v"), "."), strings.Split(strings.TrimPrefix(b, "v"), ".")
	for i := 0; i < max(len(pa), len(pb)); i++ {
		var x, y int
		if i < len(pa) {
			x, _ = strconv.Atoi(strings.SplitN(pa[i], "-", 2)[0])
		}
		if i < len(pb) {
			y, _ = strconv.Atoi(strings.SplitN(pb[i], "-", 2)[0])
		}
		if x != y {
			return x > y
		}
	}
	return false
}

// Check downloads the manifest and records a newer version for this OS, if there is one.
func (u *Updater) Check(ctx context.Context) (*UpdateInfo, error) {
	var m updateManifest
	err := getJSON(ctx, u.url, &m)
	u.mu.Lock()
	defer u.mu.Unlock()
	u.lastCheck = time.Now()
	if err != nil {
		u.lastErr = "Can't check for updates: " + err.Error()
		return nil, errors.New(u.lastErr)
	}
	u.lastErr = ""
	asset := map[string]*updateAsset{"windows": m.Windows, "linux": m.Linux, "darwin": m.Darwin}[runtime.GOOS]
	if asset == nil || asset.URL == "" || len(asset.SHA256) != 64 || !versionNewer(m.Version, launcherVersion) {
		u.available = nil
		return nil, nil
	}
	if !strings.HasPrefix(asset.URL, "https://") && !strings.HasPrefix(u.url, "http://127.0.0.1") {
		u.available = nil
		return nil, errors.New("the update download is not served over https; ignoring it")
	}
	u.available = &UpdateInfo{Version: m.Version, Notes: m.Notes, Published: m.Published, asset: *asset}
	return u.available, nil
}

func (u *Updater) Available() *UpdateInfo {
	u.mu.Lock()
	defer u.mu.Unlock()
	if u.available == nil {
		return nil
	}
	c := *u.available
	return &c
}

func (u *Updater) LastError() string {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.lastErr
}

// Apply downloads the new launcher, verifies its SHA-256, swaps it in for the running executable
// (renaming a running program is allowed on Windows; overwriting it is not) and starts it. The new
// process takes over this port and token, so the open window reconnects on its own.
func (u *Updater) Apply(ctx context.Context, port int, token string, report func(Progress)) error {
	info := u.Available()
	if info == nil {
		return errors.New("no update is available")
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	if resolved, err := filepath.EvalSymlinks(exe); err == nil {
		exe = resolved
	}
	newPath := exe + ".new"
	oldPath := exe + ".old"
	_ = os.Remove(newPath)
	job := DownloadJob{URL: info.asset.URL, Path: newPath, SHA256: info.asset.SHA256, Size: info.asset.Size, Exec: true}
	if err := downloadAll(ctx, "Downloading Annoying Client "+info.Version, []DownloadJob{job}, 1, report); err != nil {
		_ = os.Remove(newPath)
		return fmt.Errorf("update download failed: %w", err)
	}
	_ = os.Remove(oldPath)
	if err := os.Rename(exe, oldPath); err != nil {
		_ = os.Remove(newPath)
		return fmt.Errorf("can't replace the launcher (is it in a read-only folder?): %w", err)
	}
	if err := os.Rename(newPath, exe); err != nil {
		_ = os.Rename(oldPath, exe) // put the working launcher back
		return fmt.Errorf("can't install the update: %w", err)
	}
	_ = os.Chmod(exe, 0o755)
	args := []string{"--port", strconv.Itoa(port), "--resume"}
	for i, a := range os.Args[1:] {
		if a == "--data" || a == "-data" {
			if i+2 < len(os.Args) {
				args = append(args, "--data", os.Args[i+2])
			}
		}
	}
	cmd := exec.Command(exe, args...)
	cmd.Env = append(os.Environ(), "ANNOYING_RESUME_TOKEN="+token)
	if err := cmd.Start(); err != nil {
		_ = os.Rename(exe, newPath)
		_ = os.Rename(oldPath, exe)
		return fmt.Errorf("can't start the updated launcher: %w", err)
	}
	logf("updated to %s; handing over to pid %d", info.Version, cmd.Process.Pid)
	select {
	case u.restart <- struct{}{}:
	default:
	}
	return nil
}

// cleanupOldExecutable removes the previous launcher left behind by an update (it may still be
// exiting for a few seconds on Windows, so this retries).
func cleanupOldExecutable() {
	exe, err := os.Executable()
	if err != nil {
		return
	}
	if resolved, err := filepath.EvalSymlinks(exe); err == nil {
		exe = resolved
	}
	old := exe + ".old"
	for i := 0; i < 15; i++ {
		if err := os.Remove(old); err == nil || errors.Is(err, os.ErrNotExist) {
			return
		}
		time.Sleep(2 * time.Second)
	}
}
