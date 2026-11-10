// Annoying Client Launcher: installs, optimizes and launches Minecraft with Annoying Client.
//
// The launcher is a small local web server with an embedded UI. It opens as an app window through
// Microsoft Edge (installed on every Windows 10/11 PC) or Chrome, and exits when that window closes
// (unless the game is still running).
package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

const launcherVersion = "1.0.0"

var logger = log.New(os.Stderr, "", log.LstdFlags)

func logf(format string, args ...any) { logger.Printf(format, args...) }

func main() {
	dataDir := flag.String("data", defaultDataDir(), "launcher data folder")
	noWindow := flag.Bool("no-window", false, "do not open a window; print the URL instead")
	port := flag.Int("port", 0, "port to listen on (0 = random)")
	flag.Parse()

	paths := newPaths(*dataDir)
	if err := paths.ensure(); err != nil {
		fatal("Cannot create the launcher folder %s: %v", paths.Data, err)
	}
	if f, err := os.OpenFile(filepath.Join(paths.Logs, "launcher.log"), os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644); err == nil {
		logger.SetOutput(io.MultiWriter(f, os.Stderr))
	}
	logf("Annoying Client Launcher %s (%s/%s), data: %s", launcherVersion, runtime.GOOS, runtime.GOARCH, paths.Data)

	lockPath := filepath.Join(paths.Data, "launcher.lock")
	if url := existingInstance(lockPath); url != "" && !*noWindow {
		logf("launcher already running; focusing %s", url)
		openWindow(url, paths)
		return
	}

	profiles := loadProfiles(paths.Data)
	paths = paths.withInstance(profiles.Dir(profiles.ActiveProfile().ID))
	if err := paths.ensure(); err != nil {
		fatal("Cannot create the profile folder %s: %v", paths.Instance, err)
	}

	tokenBytes := make([]byte, 24)
	_, _ = rand.Read(tokenBytes)
	events := newEventHub()
	settings := loadSettings(filepath.Join(paths.Data, "launcher.json"))
	mods := newModManager(paths.Mods)
	app := &App{
		paths: paths, settings: settings, auth: newAuthManager(filepath.Join(paths.Data, "account.dat")),
		mods: mods, game: newGame(paths, settings, mods, events), events: events, token: hex.EncodeToString(tokenBytes),
		profiles: profiles,
	}

	// Install Annoying Client and Fabric API right away, so they show up in the mod list before the first launch.
	go func() {
		if err := mods.EnsureCore(context.Background()); err != nil {
			logf("could not install core mods yet: %v", err)
		}
		events.Send("state", nil)
	}()

	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", *port))
	if err != nil {
		fatal("Cannot start the launcher: %v", err)
	}
	url := fmt.Sprintf("http://%s/?t=%s", ln.Addr().String(), app.token)
	_ = os.WriteFile(lockPath, []byte(url), 0o600)
	defer os.Remove(lockPath)

	srv := &http.Server{Handler: app.routes(), ReadHeaderTimeout: 10 * time.Second}
	go func() {
		if err := srv.Serve(ln); err != nil && err != http.ErrServerClosed {
			fatal("Launcher server stopped: %v", err)
		}
	}()

	// Exit when every launcher window has been closed for a while and the game is not running.
	var mu sync.Mutex
	clients := 0
	lastSeen := time.Now()
	everConnected := false
	events.onCount = func(n int) {
		mu.Lock()
		clients = n
		lastSeen = time.Now()
		if n > 0 {
			everConnected = true
		}
		mu.Unlock()
	}

	if *noWindow {
		fmt.Println(url)
	} else {
		openWindow(url, paths)
	}

	for {
		time.Sleep(2 * time.Second)
		mu.Lock()
		idle := clients == 0 && time.Since(lastSeen) > 8*time.Second
		waitedTooLong := !everConnected && time.Since(lastSeen) > 90*time.Second
		mu.Unlock()
		if *noWindow {
			continue
		}
		if (idle && everConnected || waitedTooLong) && !app.game.Busy() {
			logf("launcher window closed; exiting")
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			_ = srv.Shutdown(ctx)
			cancel()
			return
		}
	}
}

// existingInstance returns the URL of a launcher that is already running, if any.
func existingInstance(lockPath string) string {
	data, err := os.ReadFile(lockPath)
	if err != nil {
		return ""
	}
	url := strings.TrimSpace(string(data))
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return ""
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return ""
	}
	return url
}

// openWindow shows the UI as a standalone app window (Edge or Chrome app mode), or in the default browser.
func openWindow(url string, p Paths) {
	profile := filepath.Join(p.Data, "ui")
	args := []string{"--app=" + url, "--user-data-dir=" + profile, "--window-size=1200,760",
		"--no-first-run", "--no-default-browser-check", "--disable-features=Translate,msEdgeSidebarV2"}
	for _, browser := range appBrowsers() {
		if _, err := os.Stat(browser); err != nil {
			if _, lerr := exec.LookPath(browser); lerr != nil {
				continue
			}
		}
		cmd := exec.Command(browser, args...)
		if err := cmd.Start(); err == nil {
			logf("opened window with %s", filepath.Base(browser))
			go func() { _ = cmd.Wait() }()
			return
		}
	}
	logf("no Edge/Chrome found; opening the default browser")
	openURL(url)
}

func appBrowsers() []string {
	switch runtime.GOOS {
	case "windows":
		var out []string
		for _, env := range []string{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"} {
			if base := os.Getenv(env); base != "" {
				out = append(out,
					filepath.Join(base, "Microsoft", "Edge", "Application", "msedge.exe"),
					filepath.Join(base, "Google", "Chrome", "Application", "chrome.exe"),
					filepath.Join(base, "BraveSoftware", "Brave-Browser", "Application", "brave.exe"))
			}
		}
		return out
	case "darwin":
		return []string{"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
	default:
		return []string{"microsoft-edge", "google-chrome", "chromium", "chromium-browser"}
	}
}

func fatal(format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	logf("%s", msg)
	showError("Annoying Client", msg)
	os.Exit(1)
}

// launchAccount returns the account for a direct launch.
func (a *App) launchAccount(ctx context.Context) (LaunchAccount, error) {
	if acc, ok := devAccount(); ok {
		return acc, nil
	}
	acc, err := a.auth.Valid(ctx)
	if err != nil {
		return LaunchAccount{}, err
	}
	return LaunchAccount{Name: acc.Name, UUID: acc.UUID, AccessToken: acc.AccessToken, UserType: "msa"}, nil
}

func isTestBuild() bool {
	_, ok := devAccount()
	return ok
}
