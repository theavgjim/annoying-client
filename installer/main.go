// Annoying Client Installer
//
// Installs ANNOYING CLIENT into the official Minecraft Launcher:
//   - the Fabric Loader version profile (versions/fabric-loader-<loader>-<mc>/)
//   - an isolated game directory (.minecraft/annoying-client) with Annoying Client and Fabric API in mods/
//   - an "Annoying Client" launcher profile with its own icon
//
// Everything it installs is embedded in the executable, so installation works offline. The launcher
// downloads Minecraft itself and Fabric Loader's libraries the first time the profile is started.
package main

import (
	"bufio"
	"bytes"
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const (
	clientName       = "Annoying Client"
	clientVersion    = "1.1.0"
	minecraftVersion = "1.21.11"
	loaderVersion    = "0.20.1"
	profileKey       = "annoying-client"
	instanceDirName  = "annoying-client"
	markerFile       = "annoying-client-install.json"

	clientJarName    = "annoying-client-1.1.0+mc1.21.11.jar"
	fabricApiJarName = "fabric-api-0.141.6+1.21.11.jar"
)

var versionID = "fabric-loader-" + loaderVersion + "-" + minecraftVersion

//go:embed payload/annoying-client-1.1.0+mc1.21.11.jar
var clientJar []byte

//go:embed payload/fabric-api-0.141.6+1.21.11.jar
var fabricApiJar []byte

//go:embed payload/fabric-loader-profile.json
var loaderProfile []byte

//go:embed payload/icon.png
var iconPNG []byte

// installMarker records what this installer created, so uninstalling only removes our own files.
type installMarker struct {
	ClientVersion   string    `json:"clientVersion"`
	VersionID       string    `json:"versionId"`
	CreatedVersion  bool      `json:"createdVersionDir"`
	InstalledAt     time.Time `json:"installedAt"`
	InstalledMods   []string  `json:"installedMods"`
	MinecraftFolder string    `json:"minecraftFolder"`
}

func main() {
	dirFlag := flag.String("dir", "", "path to the .minecraft folder (default: the standard location)")
	installFlag := flag.Bool("install", false, "install without showing the menu")
	uninstallFlag := flag.Bool("uninstall", false, "uninstall without showing the menu")
	noPause := flag.Bool("no-pause", false, "do not wait for Enter before exiting")
	flag.Parse()

	enableConsoleColors()
	setConsoleTitle(clientName + " Installer")
	in := bufio.NewReader(os.Stdin)
	exitCode := 0
	defer func() {
		if !*noPause {
			fmt.Print("\nPress Enter to close this window...")
			_, _ = in.ReadString('\n')
		}
		os.Exit(exitCode)
	}()

	banner()

	mcDir := *dirFlag
	if mcDir == "" {
		mcDir = defaultMinecraftDir()
	}
	mcDir = filepath.Clean(mcDir)
	info("Minecraft folder: %s", mcDir)

	if st, err := os.Stat(mcDir); err != nil || !st.IsDir() {
		fail("The Minecraft folder was not found.")
		fmt.Println("  Install the official Minecraft Launcher and start it once, then run this installer again.")
		fmt.Println("  If Minecraft is installed somewhere else, run:  AnnoyingClient-Installer.exe -dir \"<path to .minecraft>\"")
		exitCode = 1
		return
	}

	action := ""
	switch {
	case *installFlag:
		action = "install"
	case *uninstallFlag:
		action = "uninstall"
	default:
		warn("Close the Minecraft Launcher before continuing, or it may overwrite the new profile.")
		fmt.Println()
		fmt.Println("  [1] Install " + clientName + " " + clientVersion + " (Minecraft " + minecraftVersion + ")")
		fmt.Println("  [2] Uninstall")
		fmt.Println("  [3] Exit")
		fmt.Print("\nChoose an option: ")
		choice, _ := in.ReadString('\n')
		switch strings.TrimSpace(choice) {
		case "1", "":
			action = "install"
		case "2":
			action = "uninstall"
		default:
			return
		}
		fmt.Println()
	}

	var err error
	if action == "install" {
		err = install(mcDir)
	} else {
		err = uninstall(mcDir)
	}
	if err != nil {
		fail("%v", err)
		exitCode = 1
	}
}

// ---- install ------------------------------------------------------------------------------

func install(mcDir string) error {
	// 1. Fabric Loader version profile
	versionDir := filepath.Join(mcDir, "versions", versionID)
	_, statErr := os.Stat(versionDir)
	createdVersion := errors.Is(statErr, fs.ErrNotExist)
	if err := os.MkdirAll(versionDir, 0o755); err != nil {
		return fmt.Errorf("cannot create %s: %w", versionDir, err)
	}
	if !json.Valid(loaderProfile) {
		return errors.New("embedded Fabric Loader profile is damaged; download the installer again")
	}
	if err := writeFileAtomic(filepath.Join(versionDir, versionID+".json"), loaderProfile); err != nil {
		return err
	}
	// The launcher expects a jar next to the json; the real game jar comes from the inherited vanilla version.
	jarPath := filepath.Join(versionDir, versionID+".jar")
	if _, err := os.Stat(jarPath); errors.Is(err, fs.ErrNotExist) {
		if err := os.WriteFile(jarPath, nil, 0o644); err != nil {
			return err
		}
	}
	ok("Fabric Loader %s for Minecraft %s", loaderVersion, minecraftVersion)

	// 2. Game directory with mods
	instance := filepath.Join(mcDir, instanceDirName)
	mods := filepath.Join(instance, "mods")
	if err := os.MkdirAll(mods, 0o755); err != nil {
		return fmt.Errorf("cannot create %s: %w", mods, err)
	}
	removed, err := removeOldJars(mods)
	if err != nil {
		return err
	}
	for _, name := range removed {
		info("Removed old %s", name)
	}
	if err := writeFileAtomic(filepath.Join(mods, clientJarName), clientJar); err != nil {
		return err
	}
	ok("%s %s", clientName, clientVersion)
	if err := writeFileAtomic(filepath.Join(mods, fabricApiJarName), fabricApiJar); err != nil {
		return err
	}
	ok("Fabric API 0.141.6")

	// Bring over the player's video/controls settings and server list the first time.
	for _, f := range []string{"options.txt", "servers.dat"} {
		copied, err := copyIfMissing(filepath.Join(mcDir, f), filepath.Join(instance, f))
		if err != nil {
			warn("Could not copy %s: %v", f, err)
		} else if copied {
			ok("Copied your %s", f)
		}
	}

	// 3. Launcher profile(s)
	updated := 0
	for _, name := range launcherProfileFiles(mcDir) {
		if err := addLauncherProfile(name, instance); err != nil {
			return fmt.Errorf("cannot update %s: %w", filepath.Base(name), err)
		}
		updated++
	}
	ok("Launcher profile \"%s\" (%d launcher file(s) updated)", clientName, updated)

	// 4. Marker for uninstall
	marker := installMarker{
		ClientVersion: clientVersion, VersionID: versionID, CreatedVersion: createdVersion || previouslyCreatedVersion(instance),
		InstalledAt: time.Now(), InstalledMods: []string{clientJarName, fabricApiJarName}, MinecraftFolder: mcDir,
	}
	data, _ := json.MarshalIndent(marker, "", "  ")
	if err := writeFileAtomic(filepath.Join(instance, markerFile), data); err != nil {
		return err
	}

	fmt.Println()
	success("%s %s is installed!", clientName, clientVersion)
	fmt.Println()
	fmt.Println("  1. Open the Minecraft Launcher.")
	fmt.Println("  2. Select the \"" + clientName + "\" profile next to the Play button.")
	fmt.Println("  3. Press Play. The first start downloads Minecraft " + minecraftVersion + " and takes a minute.")
	fmt.Println("  4. In game, press RIGHT SHIFT to open the menu.")
	fmt.Println()
	info("Game folder: %s", instance)
	return nil
}

func previouslyCreatedVersion(instance string) bool {
	data, err := os.ReadFile(filepath.Join(instance, markerFile))
	if err != nil {
		return false
	}
	var m installMarker
	return json.Unmarshal(data, &m) == nil && m.CreatedVersion
}

// removeOldJars deletes earlier Annoying Client and Fabric API jars from our own mods folder.
func removeOldJars(mods string) ([]string, error) {
	entries, err := os.ReadDir(mods)
	if err != nil {
		return nil, err
	}
	var removed []string
	for _, e := range entries {
		n := strings.ToLower(e.Name())
		if e.IsDir() || !strings.HasSuffix(n, ".jar") {
			continue
		}
		if (strings.HasPrefix(n, "annoying-client-") && e.Name() != clientJarName) ||
			(strings.HasPrefix(n, "fabric-api-") && e.Name() != fabricApiJarName) {
			if err := os.Remove(filepath.Join(mods, e.Name())); err != nil {
				return removed, fmt.Errorf("cannot remove old %s: %w", e.Name(), err)
			}
			removed = append(removed, e.Name())
		}
	}
	return removed, nil
}

func copyIfMissing(src, dst string) (bool, error) {
	if _, err := os.Stat(dst); err == nil {
		return false, nil
	}
	data, err := os.ReadFile(src)
	if errors.Is(err, fs.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, os.WriteFile(dst, data, 0o644)
}

// launcherProfileFiles lists the launcher profile files to update. The Microsoft Store / Xbox app
// launcher uses launcher_profiles_microsoft_store.json; the classic launcher uses launcher_profiles.json.
func launcherProfileFiles(mcDir string) []string {
	files := []string{filepath.Join(mcDir, "launcher_profiles.json")}
	store := filepath.Join(mcDir, "launcher_profiles_microsoft_store.json")
	if _, err := os.Stat(store); err == nil {
		files = append(files, store)
	}
	return files
}

func readProfiles(path string) (map[string]any, error) {
	root := map[string]any{}
	data, err := os.ReadFile(path)
	if errors.Is(err, fs.ErrNotExist) {
		return map[string]any{"profiles": map[string]any{}, "settings": map[string]any{}, "version": 3}, nil
	}
	if err != nil {
		return nil, err
	}
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber() // keep numbers exactly as the launcher wrote them
	if err := dec.Decode(&root); err != nil {
		return nil, fmt.Errorf("the file is not valid JSON (%v); start the launcher once to repair it", err)
	}
	// Keep a backup of the launcher's file before the first change.
	backup := path + ".annoying-client-backup"
	if _, err := os.Stat(backup); errors.Is(err, fs.ErrNotExist) {
		_ = os.WriteFile(backup, data, 0o644)
	}
	return root, nil
}

func writeProfiles(path string, root map[string]any) error {
	data, err := json.MarshalIndent(root, "", "  ")
	if err != nil {
		return err
	}
	return writeFileAtomic(path, data)
}

func addLauncherProfile(path, instance string) error {
	root, err := readProfiles(path)
	if err != nil {
		return err
	}
	profiles, _ := root["profiles"].(map[string]any)
	if profiles == nil {
		profiles = map[string]any{}
		root["profiles"] = profiles
	}
	now := time.Now().UTC().Format("2006-01-02T15:04:05.000Z")
	created := now
	if old, ok := profiles[profileKey].(map[string]any); ok {
		if c, ok := old["created"].(string); ok {
			created = c
		}
	}
	profiles[profileKey] = map[string]any{
		"name":          clientName,
		"type":          "custom",
		"created":       created,
		"lastUsed":      now, // newest profile is preselected in the launcher
		"lastVersionId": versionID,
		"gameDir":       instance,
		"icon":          "data:image/png;base64," + base64.StdEncoding.EncodeToString(iconPNG),
	}
	return writeProfiles(path, root)
}

// ---- uninstall ----------------------------------------------------------------------------

func uninstall(mcDir string) error {
	instance := filepath.Join(mcDir, instanceDirName)
	var marker installMarker
	markerData, markerErr := os.ReadFile(filepath.Join(instance, markerFile))
	if markerErr == nil {
		_ = json.Unmarshal(markerData, &marker)
	}

	for _, path := range launcherProfileFiles(mcDir) {
		if _, err := os.Stat(path); err != nil {
			continue
		}
		root, err := readProfiles(path)
		if err != nil {
			return fmt.Errorf("cannot read %s: %w", filepath.Base(path), err)
		}
		if profiles, ok := root["profiles"].(map[string]any); ok {
			delete(profiles, profileKey)
		}
		if err := writeProfiles(path, root); err != nil {
			return err
		}
	}
	ok("Removed the \"%s\" launcher profile", clientName)

	mods := filepath.Join(instance, "mods")
	for _, name := range []string{clientJarName, fabricApiJarName} {
		if err := os.Remove(filepath.Join(mods, name)); err == nil {
			ok("Removed %s", name)
		}
	}
	if _, err := removeOldJars(mods); err != nil && !errors.Is(err, fs.ErrNotExist) {
		warn("%v", err)
	}

	// Only remove the Fabric Loader version if this installer created it and no other profile uses it.
	if marker.CreatedVersion && !versionInUse(mcDir, versionID) {
		if err := os.RemoveAll(filepath.Join(mcDir, "versions", versionID)); err == nil {
			ok("Removed Fabric Loader version %s", versionID)
		}
	} else {
		info("Kept Fabric Loader version %s (it existed before or another profile uses it)", versionID)
	}
	_ = os.Remove(filepath.Join(instance, markerFile))

	fmt.Println()
	success("%s was uninstalled.", clientName)
	info("Your worlds, screenshots and settings were kept in: %s", instance)
	info("Delete that folder yourself if you no longer need them.")
	return nil
}

func versionInUse(mcDir, id string) bool {
	for _, path := range launcherProfileFiles(mcDir) {
		root, err := readProfiles(path)
		if err != nil {
			return true // be safe
		}
		profiles, _ := root["profiles"].(map[string]any)
		for _, p := range profiles {
			if m, ok := p.(map[string]any); ok && m["lastVersionId"] == id {
				return true
			}
		}
	}
	return false
}

// ---- helpers ------------------------------------------------------------------------------

func defaultMinecraftDir() string {
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

func writeFileAtomic(path string, data []byte) error {
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return fmt.Errorf("cannot write %s: %w", path, err)
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("cannot write %s (is the game or launcher running?): %w", path, err)
	}
	return nil
}

const (
	purple = "\x1b[38;2;139;92;246m"
	blue   = "\x1b[38;2;59;130;246m"
	green  = "\x1b[38;2;34;197;94m"
	red    = "\x1b[38;2;239;68;68m"
	yellow = "\x1b[38;2;245;158;11m"
	gray   = "\x1b[38;2;150;150;170m"
	bold   = "\x1b[1m"
	reset  = "\x1b[0m"
)

func banner() {
	fmt.Println()
	fmt.Println(bold + purple + "   ANNOYING " + blue + "CLIENT" + reset + gray + "   Installer " + clientVersion + "  |  Minecraft " + minecraftVersion + reset)
	fmt.Println(gray + "   ------------------------------------------------------------" + reset)
	fmt.Println()
}

func info(f string, a ...any)    { fmt.Printf(gray+"  "+f+reset+"\n", a...) }
func ok(f string, a ...any)      { fmt.Printf(green+"  [OK] "+reset+f+"\n", a...) }
func warn(f string, a ...any)    { fmt.Printf(yellow+"  [!] "+f+reset+"\n", a...) }
func fail(f string, a ...any)    { fmt.Printf(red+bold+"  [ERROR] "+reset+red+f+reset+"\n", a...) }
func success(f string, a ...any) { fmt.Printf(bold+purple+"  "+f+reset+"\n", a...) }
