package main

import (
	"archive/zip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
)

const (
	versionManifestURL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
	javaRuntimesURL    = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
	assetBaseURL       = "https://resources.download.minecraft.net/"
	librariesURL       = "https://libraries.minecraft.net/"
)

type Artifact struct {
	Path string `json:"path"`
	SHA1 string `json:"sha1"`
	Size int64  `json:"size"`
	URL  string `json:"url"`
}

type Rule struct {
	Action string `json:"action"`
	OS     *struct {
		Name    string `json:"name"`
		Arch    string `json:"arch"`
		Version string `json:"version"`
	} `json:"os"`
	Features map[string]bool `json:"features"`
}

type Library struct {
	Name      string `json:"name"`
	URL       string `json:"url"` // maven repository base (Fabric libraries)
	SHA1      string `json:"sha1"`
	Size      int64  `json:"size"`
	Downloads *struct {
		Artifact *Artifact `json:"artifact"`
	} `json:"downloads"`
	Rules []Rule `json:"rules"`
}

type VersionJSON struct {
	ID           string `json:"id"`
	InheritsFrom string `json:"inheritsFrom"`
	Type         string `json:"type"`
	MainClass    string `json:"mainClass"`
	Arguments    struct {
		Game []json.RawMessage `json:"game"`
		JVM  []json.RawMessage `json:"jvm"`
	} `json:"arguments"`
	AssetIndex struct {
		ID   string `json:"id"`
		SHA1 string `json:"sha1"`
		Size int64  `json:"size"`
		URL  string `json:"url"`
	} `json:"assetIndex"`
	Assets      string              `json:"assets"`
	Downloads   map[string]Artifact `json:"downloads"`
	Libraries   []Library           `json:"libraries"`
	JavaVersion struct {
		Component    string `json:"component"`
		MajorVersion int    `json:"majorVersion"`
	} `json:"javaVersion"`
}

// ---- rules -------------------------------------------------------------------------------

func mojangOSName() string {
	switch runtime.GOOS {
	case "windows":
		return "windows"
	case "darwin":
		return "osx"
	default:
		return "linux"
	}
}

func rulesAllow(rules []Rule, features map[string]bool) bool {
	if len(rules) == 0 {
		return true
	}
	allowed := false
	for _, r := range rules {
		match := true
		if r.OS != nil {
			if r.OS.Name != "" && r.OS.Name != mojangOSName() {
				match = false
			}
			if r.OS.Arch != "" {
				arch := runtime.GOARCH
				if r.OS.Arch == "x86" && arch != "386" {
					match = false
				} else if r.OS.Arch == "arm64" && arch != "arm64" {
					match = false
				}
			}
			if r.OS.Version != "" {
				if re, err := regexp.Compile(r.OS.Version); err == nil && !re.MatchString(osVersion()) {
					match = false
				}
			}
		}
		for name, want := range r.Features {
			if features[name] != want {
				match = false
			}
		}
		if match {
			allowed = r.Action == "allow"
		}
	}
	return allowed
}

// ---- maven coordinates -------------------------------------------------------------------

// mavenPath converts "group:artifact:version[:classifier][@ext]" to a repository path.
func mavenPath(coord string) (string, error) {
	ext := "jar"
	if at := strings.LastIndex(coord, "@"); at >= 0 {
		ext = coord[at+1:]
		coord = coord[:at]
	}
	parts := strings.Split(coord, ":")
	if len(parts) < 3 {
		return "", fmt.Errorf("invalid maven coordinate %q", coord)
	}
	group := strings.ReplaceAll(parts[0], ".", "/")
	name := parts[1] + "-" + parts[2]
	if len(parts) > 3 {
		name += "-" + parts[3]
	}
	return group + "/" + parts[1] + "/" + parts[2] + "/" + name + "." + ext, nil
}

// libraryKey identifies a library regardless of version ("group:artifact[:classifier]").
func libraryKey(coord string) string {
	parts := strings.Split(strings.SplitN(coord, "@", 2)[0], ":")
	if len(parts) < 3 {
		return coord
	}
	key := parts[0] + ":" + parts[1]
	if len(parts) > 3 {
		key += ":" + parts[3]
	}
	return key
}

// ---- version resolution ------------------------------------------------------------------

type versionManifest struct {
	Versions []struct {
		ID   string `json:"id"`
		URL  string `json:"url"`
		SHA1 string `json:"sha1"`
	} `json:"versions"`
}

// fetchVanillaVersion downloads (or reuses) versions/<id>/<id>.json.
func fetchVanillaVersion(ctx context.Context, p Paths, id string) (*VersionJSON, error) {
	path := filepath.Join(p.Versions, id, id+".json")
	var manifest versionManifest
	if err := getJSON(ctx, versionManifestURL, &manifest); err != nil {
		// Offline: fall back to a previously downloaded copy.
		if v, lerr := readVersionFile(path); lerr == nil {
			logf("version manifest unavailable (%v); using cached %s", err, id)
			return v, nil
		}
		return nil, fmt.Errorf("cannot reach Mojang's version list: %w", err)
	}
	for _, v := range manifest.Versions {
		if v.ID == id {
			if err := downloadAll(ctx, "Game metadata", []DownloadJob{{URL: v.URL, Path: path, SHA1: v.SHA1}}, 1, func(Progress) {}); err != nil {
				return nil, err
			}
			return readVersionFile(path)
		}
	}
	return nil, fmt.Errorf("minecraft %s is not in Mojang's version list", id)
}

func readVersionFile(path string) (*VersionJSON, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var v VersionJSON
	if err := json.Unmarshal(data, &v); err != nil {
		return nil, fmt.Errorf("%s is damaged: %w", filepath.Base(path), err)
	}
	return &v, nil
}

// mergeVersions applies a child profile (Fabric) on top of its parent (vanilla).
// Child libraries come first and replace parent libraries with the same group:artifact.
func mergeVersions(parent, child *VersionJSON) *VersionJSON {
	merged := *parent
	merged.ID = child.ID
	if child.MainClass != "" {
		merged.MainClass = child.MainClass
	}
	seen := map[string]bool{}
	var libs []Library
	for _, l := range child.Libraries {
		seen[libraryKey(l.Name)] = true
		libs = append(libs, l)
	}
	for _, l := range parent.Libraries {
		if !seen[libraryKey(l.Name)] {
			libs = append(libs, l)
		}
	}
	merged.Libraries = libs
	merged.Arguments.Game = append(append([]json.RawMessage{}, parent.Arguments.Game...), child.Arguments.Game...)
	merged.Arguments.JVM = append(append([]json.RawMessage{}, parent.Arguments.JVM...), child.Arguments.JVM...)
	return &merged
}

// libraryJobs returns the download jobs and classpath entries for the libraries allowed on this OS.
func libraryJobs(p Paths, v *VersionJSON) ([]DownloadJob, []string, error) {
	var jobs []DownloadJob
	var classpath []string
	for _, l := range v.Libraries {
		if !rulesAllow(l.Rules, nil) {
			continue
		}
		var job DownloadJob
		switch {
		case l.Downloads != nil && l.Downloads.Artifact != nil && l.Downloads.Artifact.URL != "":
			a := l.Downloads.Artifact
			job = DownloadJob{URL: a.URL, Path: filepath.Join(p.Libraries, filepath.FromSlash(a.Path)), SHA1: a.SHA1, Size: a.Size}
		default:
			rel, err := mavenPath(l.Name)
			if err != nil {
				return nil, nil, err
			}
			base := l.URL
			if base == "" {
				base = librariesURL
			}
			if !strings.HasSuffix(base, "/") {
				base += "/"
			}
			job = DownloadJob{URL: base + rel, Path: filepath.Join(p.Libraries, filepath.FromSlash(rel)), SHA1: l.SHA1, Size: l.Size}
		}
		jobs = append(jobs, job)
		classpath = append(classpath, job.Path)
	}
	return jobs, classpath, nil
}

// ---- assets ------------------------------------------------------------------------------

type assetIndex struct {
	Objects map[string]struct {
		Hash string `json:"hash"`
		Size int64  `json:"size"`
	} `json:"objects"`
}

func assetJobs(ctx context.Context, p Paths, v *VersionJSON) ([]DownloadJob, error) {
	indexPath := filepath.Join(p.Assets, "indexes", v.AssetIndex.ID+".json")
	if err := downloadAll(ctx, "Asset index", []DownloadJob{{URL: v.AssetIndex.URL, Path: indexPath, SHA1: v.AssetIndex.SHA1, Size: v.AssetIndex.Size}}, 1, func(Progress) {}); err != nil {
		return nil, err
	}
	data, err := os.ReadFile(indexPath)
	if err != nil {
		return nil, err
	}
	var idx assetIndex
	if err := json.Unmarshal(data, &idx); err != nil {
		return nil, fmt.Errorf("asset index is damaged: %w", err)
	}
	seen := map[string]bool{}
	var jobs []DownloadJob
	for _, o := range idx.Objects {
		if len(o.Hash) < 2 || seen[o.Hash] {
			continue
		}
		seen[o.Hash] = true
		rel := o.Hash[:2] + "/" + o.Hash
		jobs = append(jobs, DownloadJob{URL: assetBaseURL + rel, Path: filepath.Join(p.Assets, "objects", o.Hash[:2], o.Hash), SHA1: o.Hash, Size: o.Size})
	}
	return jobs, nil
}

// worldVersion reads the data version from the client JAR's version.json (needed when creating options.txt).
func worldVersion(clientJar string) int {
	zr, err := zip.OpenReader(clientJar)
	if err != nil {
		return 0
	}
	defer zr.Close()
	for _, f := range zr.File {
		if f.Name == "version.json" {
			rc, err := f.Open()
			if err != nil {
				return 0
			}
			defer rc.Close()
			data, _ := io.ReadAll(rc)
			var v struct {
				WorldVersion int `json:"world_version"`
			}
			_ = json.Unmarshal(data, &v)
			return v.WorldVersion
		}
	}
	return 0
}

// ---- Java runtime ------------------------------------------------------------------------

func javaPlatform() string {
	switch runtime.GOOS + "/" + runtime.GOARCH {
	case "windows/amd64":
		return "windows-x64"
	case "windows/arm64":
		return "windows-arm64"
	case "windows/386":
		return "windows-x86"
	case "darwin/arm64":
		return "mac-os-arm64"
	case "darwin/amd64":
		return "mac-os"
	case "linux/386":
		return "linux-i386"
	default:
		return "linux"
	}
}

// ensureJava downloads Mojang's Java runtime for this game version and returns the java executable.
func ensureJava(ctx context.Context, p Paths, component string, threads int, report func(Progress)) (string, error) {
	if component == "" {
		component = "java-runtime-delta"
	}
	home := filepath.Join(p.Runtime, component, javaPlatform())
	javaExe := filepath.Join(home, "bin", "java")
	if runtime.GOOS == "windows" {
		javaExe = filepath.Join(home, "bin", "javaw.exe")
	} else if runtime.GOOS == "darwin" {
		javaExe = filepath.Join(home, "jre.bundle", "Contents", "Home", "bin", "java")
	}
	marker := filepath.Join(home, ".complete")

	var all map[string]map[string][]struct {
		Manifest struct {
			SHA1 string `json:"sha1"`
			Size int64  `json:"size"`
			URL  string `json:"url"`
		} `json:"manifest"`
	}
	if err := getJSON(ctx, javaRuntimesURL, &all); err != nil {
		if _, serr := os.Stat(marker); serr == nil {
			return javaExe, nil // offline but already installed
		}
		return "", fmt.Errorf("cannot reach Mojang's Java download list: %w", err)
	}
	entries := all[javaPlatform()][component]
	if len(entries) == 0 {
		return "", fmt.Errorf("mojang has no %s runtime for %s; set a Java path in Settings", component, javaPlatform())
	}
	var files struct {
		Files map[string]struct {
			Type       string `json:"type"`
			Executable bool   `json:"executable"`
			Target     string `json:"target"`
			Downloads  struct {
				Raw Artifact `json:"raw"`
			} `json:"downloads"`
		} `json:"files"`
	}
	if err := getJSON(ctx, entries[0].Manifest.URL, &files); err != nil {
		return "", err
	}
	var jobs []DownloadJob
	for rel, f := range files.Files {
		target := filepath.Join(home, filepath.FromSlash(rel))
		switch f.Type {
		case "directory":
			if err := os.MkdirAll(target, 0o755); err != nil {
				return "", err
			}
		case "file":
			jobs = append(jobs, DownloadJob{URL: f.Downloads.Raw.URL, Path: target, SHA1: f.Downloads.Raw.SHA1, Size: f.Downloads.Raw.Size, Exec: f.Executable})
		}
	}
	if err := downloadAll(ctx, "Java runtime", jobs, threads, report); err != nil {
		return "", err
	}
	if runtime.GOOS != "windows" {
		for rel, f := range files.Files {
			if f.Type == "link" {
				link := filepath.Join(home, filepath.FromSlash(rel))
				if _, err := os.Lstat(link); err != nil {
					_ = os.MkdirAll(filepath.Dir(link), 0o755)
					_ = os.Symlink(f.Target, link)
				}
			}
		}
	}
	_ = os.WriteFile(marker, []byte(entries[0].Manifest.SHA1), 0o644)
	return javaExe, nil
}
