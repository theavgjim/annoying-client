package main

import (
	"archive/zip"
	"context"
	"crypto/sha1"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
)

const (
	modrinthAPI      = "https://api.modrinth.com/v2"
	fabricAPIProject = "P7dR8mSH"
	clientJarName    = "annoying-client-1.3.3+mc1.21.11.jar"
	clientModID      = "annoyingclient"
)

//go:embed embedded/annoying-client.jar
var clientJar []byte

// InstalledMod describes one jar in the mods folder.
type InstalledMod struct {
	File        string `json:"file"`
	ModID       string `json:"modId"`
	Name        string `json:"name"`
	Version     string `json:"version"`
	Description string `json:"description"`
	Enabled     bool   `json:"enabled"`
	Core        bool   `json:"core"` // required by Annoying Client; cannot be removed
	ProjectID   string `json:"projectId"`
	IconURL     string `json:"iconUrl"`
	Update      string `json:"update,omitempty"` // newer version number, when known
	SHA1        string `json:"-"`
}

// SearchHit is a Modrinth search result.
type SearchHit struct {
	ProjectID   string   `json:"project_id"`
	Slug        string   `json:"slug"`
	Title       string   `json:"title"`
	Description string   `json:"description"`
	IconURL     string   `json:"icon_url"`
	Downloads   int      `json:"downloads"`
	Author      string   `json:"author"`
	Categories  []string `json:"categories"`
	Installed   bool     `json:"installed"`
}

type modrinthFile struct {
	URL      string `json:"url"`
	Filename string `json:"filename"`
	Primary  bool   `json:"primary"`
	Size     int64  `json:"size"`
	Hashes   struct {
		SHA1   string `json:"sha1"`
		SHA512 string `json:"sha512"`
	} `json:"hashes"`
}

type modrinthVersion struct {
	ID            string         `json:"id"`
	ProjectID     string         `json:"project_id"`
	Name          string         `json:"name"`
	VersionNumber string         `json:"version_number"`
	VersionType   string         `json:"version_type"`
	Files         []modrinthFile `json:"files"`
	Dependencies  []struct {
		VersionID      string `json:"version_id"`
		ProjectID      string `json:"project_id"`
		DependencyType string `json:"dependency_type"`
	} `json:"dependencies"`
}

func (v *modrinthVersion) primaryFile() *modrinthFile {
	for i := range v.Files {
		if v.Files[i].Primary {
			return &v.Files[i]
		}
	}
	if len(v.Files) > 0 {
		return &v.Files[0]
	}
	return nil
}

// modIndex remembers which Modrinth project each jar came from (mods/.annoyingclient.json).
type modIndex struct {
	Files map[string]struct {
		ProjectID string `json:"projectId"`
		VersionID string `json:"versionId"`
		Title     string `json:"title"`
		IconURL   string `json:"iconUrl"`
	} `json:"files"`
}

type ModManager struct {
	mu    sync.Mutex
	dir   string
	index modIndex
}

func newModManager(dir string) *ModManager {
	m := &ModManager{dir: dir}
	m.loadIndex()
	return m
}

func (m *ModManager) indexPath() string { return filepath.Join(m.dir, ".annoyingclient.json") }

func (m *ModManager) loadIndex() {
	m.index = modIndex{}
	if data, err := os.ReadFile(m.indexPath()); err == nil {
		_ = json.Unmarshal(data, &m.index)
	}
	if m.index.Files == nil {
		m.index.Files = map[string]struct {
			ProjectID string `json:"projectId"`
			VersionID string `json:"versionId"`
			Title     string `json:"title"`
			IconURL   string `json:"iconUrl"`
		}{}
	}
}

func (m *ModManager) saveIndex() {
	data, _ := json.MarshalIndent(m.index, "", "  ")
	_ = writeFileAtomic(m.indexPath(), data)
}

func (m *ModManager) track(file, projectID, versionID, title, icon string) {
	e := m.index.Files[file]
	e.ProjectID, e.VersionID, e.Title, e.IconURL = projectID, versionID, title, icon
	m.index.Files[file] = e
}

// readModInfo reads fabric.mod.json from a jar.
func readModInfo(path string) (id, name, version, desc string) {
	zr, err := zip.OpenReader(path)
	if err != nil {
		return
	}
	defer zr.Close()
	for _, f := range zr.File {
		if f.Name != "fabric.mod.json" {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return
		}
		defer rc.Close()
		data, _ := io.ReadAll(io.LimitReader(rc, 1<<20))
		var meta struct {
			ID          string `json:"id"`
			Name        string `json:"name"`
			Version     string `json:"version"`
			Description string `json:"description"`
		}
		// Some mods put control characters in descriptions; be lenient.
		_ = json.Unmarshal(data, &meta)
		return meta.ID, meta.Name, meta.Version, meta.Description
	}
	return
}

func sha1File(path string) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	h := sha1.New()
	_, _ = io.Copy(h, f)
	return hex.EncodeToString(h.Sum(nil))
}

// List returns every mod jar (enabled and disabled), sorted by name.
func (m *ModManager) List() ([]InstalledMod, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	entries, err := os.ReadDir(m.dir)
	if err != nil {
		return nil, err
	}
	var mods []InstalledMod
	for _, e := range entries {
		name := e.Name()
		enabled := strings.HasSuffix(name, ".jar")
		if e.IsDir() || (!enabled && !strings.HasSuffix(name, ".jar.disabled")) {
			continue
		}
		path := filepath.Join(m.dir, name)
		id, title, version, desc := readModInfo(path)
		base := strings.TrimSuffix(name, ".disabled")
		mod := InstalledMod{File: name, ModID: id, Name: title, Version: version, Description: desc, Enabled: enabled}
		if mod.Name == "" {
			mod.Name = strings.TrimSuffix(base, ".jar")
		}
		if t, ok := m.index.Files[base]; ok {
			mod.ProjectID, mod.IconURL = t.ProjectID, t.IconURL
		}
		mod.Core = id == clientModID || id == "fabric-api"
		mods = append(mods, mod)
	}
	sort.Slice(mods, func(i, j int) bool {
		if mods[i].Core != mods[j].Core {
			return mods[i].Core
		}
		return strings.ToLower(mods[i].Name) < strings.ToLower(mods[j].Name)
	})
	return mods, nil
}

func (m *ModManager) safePath(file string) (string, error) {
	if file == "" || file != filepath.Base(file) || strings.HasPrefix(file, ".") {
		return "", errors.New("invalid file name")
	}
	return filepath.Join(m.dir, file), nil
}

// SetEnabled renames a jar to/from .jar.disabled.
func (m *ModManager) SetEnabled(file string, enabled bool) error {
	path, err := m.safePath(file)
	if err != nil {
		return err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	if id, _, _, _ := readModInfo(path); id == clientModID || id == "fabric-api" {
		return errors.New("annoying Client and Fabric API are required and cannot be disabled")
	}
	base := strings.TrimSuffix(file, ".disabled")
	target := filepath.Join(m.dir, base)
	if !enabled {
		target += ".disabled"
	}
	if target == path {
		return nil
	}
	return os.Rename(path, target)
}

func (m *ModManager) Remove(file string) error {
	path, err := m.safePath(file)
	if err != nil {
		return err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	if id, _, _, _ := readModInfo(path); id == clientModID || id == "fabric-api" {
		return errors.New("annoying Client and Fabric API are required and cannot be removed")
	}
	if err := os.Remove(path); err != nil {
		return err
	}
	delete(m.index.Files, strings.TrimSuffix(file, ".disabled"))
	m.saveIndex()
	return nil
}

// EnsureCore installs/updates the embedded Annoying Client jar and makes sure Fabric API is present.
func (m *ModManager) EnsureCore(ctx context.Context) error {
	m.mu.Lock()
	entries, _ := os.ReadDir(m.dir)
	want := sha1.Sum(clientJar)
	wantHex := hex.EncodeToString(want[:])
	haveFabricAPI := false
	for _, e := range entries {
		n := e.Name()
		path := filepath.Join(m.dir, n)
		if strings.HasPrefix(n, "annoying-client-") && strings.Contains(n, ".jar") && n != clientJarName {
			_ = os.Remove(path) // older client version
			continue
		}
		if strings.HasSuffix(n, ".jar") || strings.HasSuffix(n, ".jar.disabled") {
			if id, _, _, _ := readModInfo(path); id == "fabric-api" {
				if strings.HasSuffix(n, ".disabled") {
					_ = os.Rename(path, strings.TrimSuffix(path, ".disabled"))
				}
				haveFabricAPI = true
			}
		}
	}
	clientPath := filepath.Join(m.dir, clientJarName)
	_ = os.Remove(clientPath + ".disabled")
	if sha1File(clientPath) != wantHex {
		if err := writeFileAtomic(clientPath, clientJar); err != nil {
			m.mu.Unlock()
			return err
		}
	}
	m.mu.Unlock()
	if !haveFabricAPI {
		_, err := m.Install(ctx, fabricAPIProject)
		return err
	}
	return nil
}

// latestVersion picks the newest release (or, failing that, any) build for this game version.
func latestVersion(ctx context.Context, projectID string) (*modrinthVersion, error) {
	q := url.Values{
		"loaders":       {`["fabric"]`},
		"game_versions": {`["` + minecraftVersion + `"]`},
	}
	var versions []modrinthVersion
	if err := getJSON(ctx, modrinthAPI+"/project/"+url.PathEscape(projectID)+"/version?"+q.Encode(), &versions); err != nil {
		return nil, err
	}
	if len(versions) == 0 {
		return nil, fmt.Errorf("no Fabric %s version is available yet", minecraftVersion)
	}
	for i := range versions {
		if versions[i].VersionType == "release" {
			return &versions[i], nil
		}
	}
	return &versions[0], nil
}

type modrinthProject struct {
	ID      string `json:"id"`
	Title   string `json:"title"`
	IconURL string `json:"icon_url"`
}

// installedProjects maps Modrinth project IDs to file names for jars we installed.
func (m *ModManager) installedProjects() map[string]string {
	out := map[string]string{}
	for file, t := range m.index.Files {
		if _, err := os.Stat(filepath.Join(m.dir, file)); err == nil {
			out[t.ProjectID] = file
		} else if _, err := os.Stat(filepath.Join(m.dir, file+".disabled")); err == nil {
			out[t.ProjectID] = file
		}
	}
	return out
}

// Install downloads a project's newest compatible version and its required dependencies.
// It returns the titles of everything that was installed.
func (m *ModManager) Install(ctx context.Context, projectID string) ([]string, error) {
	var installed []string
	visiting := map[string]bool{}
	var install func(pid, versionID string) error
	install = func(pid, versionID string) error {
		if visiting[pid] {
			return nil
		}
		visiting[pid] = true
		m.mu.Lock()
		_, already := m.installedProjects()[pid]
		m.mu.Unlock()
		if already && pid != projectID {
			return nil
		}
		var v *modrinthVersion
		var err error
		if versionID != "" {
			v = &modrinthVersion{}
			err = getJSON(ctx, modrinthAPI+"/version/"+url.PathEscape(versionID), v)
		} else {
			v, err = latestVersion(ctx, pid)
		}
		if err != nil {
			return err
		}
		var project modrinthProject
		_ = getJSON(ctx, modrinthAPI+"/project/"+url.PathEscape(v.ProjectID), &project)
		for _, d := range v.Dependencies {
			if d.DependencyType == "required" && d.ProjectID != "" {
				if err := install(d.ProjectID, d.VersionID); err != nil {
					return fmt.Errorf("required dependency of %s: %w", project.Title, err)
				}
			}
		}
		f := v.primaryFile()
		if f == nil {
			return fmt.Errorf("%s has no downloadable file", project.Title)
		}
		name := filepath.Base(f.Filename)
		if !strings.HasSuffix(name, ".jar") {
			return fmt.Errorf("%s is not a jar file", name)
		}
		dest := filepath.Join(m.dir, name)
		if err := downloadOne(ctx, DownloadJob{URL: f.URL, Path: dest, SHA1: f.Hashes.SHA1, SHA512: f.Hashes.SHA512, Size: f.Size}, func(int64) {}); err != nil {
			return err
		}
		m.mu.Lock()
		// Replace an older jar of the same project.
		if old, ok := m.installedProjects()[v.ProjectID]; ok && old != name {
			_ = os.Remove(filepath.Join(m.dir, old))
			_ = os.Remove(filepath.Join(m.dir, old+".disabled"))
			delete(m.index.Files, old)
		}
		m.track(name, v.ProjectID, v.ID, project.Title, project.IconURL)
		m.saveIndex()
		m.mu.Unlock()
		title := project.Title
		if title == "" {
			title = name
		}
		installed = append(installed, title+" "+v.VersionNumber)
		logf("installed mod %s (%s)", title, name)
		return nil
	}
	err := install(projectID, "")
	return installed, err
}

// Search queries Modrinth for Fabric mods compatible with this game version.
func (m *ModManager) Search(ctx context.Context, query, sortBy string, offset int) ([]SearchHit, int, error) {
	switch sortBy {
	case "downloads", "follows", "newest", "updated", "relevance":
	default:
		sortBy = "relevance"
	}
	facets := `[["categories:fabric"],["versions:` + minecraftVersion + `"],["project_type:mod"]]`
	q := url.Values{"query": {query}, "facets": {facets}, "index": {sortBy}, "limit": {"24"}, "offset": {fmt.Sprint(max(0, offset))}}
	var res struct {
		Hits      []SearchHit `json:"hits"`
		TotalHits int         `json:"total_hits"`
	}
	if err := getJSON(ctx, modrinthAPI+"/search?"+q.Encode(), &res); err != nil {
		return nil, 0, err
	}
	m.mu.Lock()
	installed := m.installedProjects()
	m.mu.Unlock()
	for i := range res.Hits {
		_, res.Hits[i].Installed = installed[res.Hits[i].ProjectID]
	}
	return res.Hits, res.TotalHits, nil
}

// Identify looks up untracked jars on Modrinth by hash, so their icons and updates work too.
func (m *ModManager) Identify(ctx context.Context) {
	mods, err := m.List()
	if err != nil {
		return
	}
	hashes := map[string]string{}
	for _, mod := range mods {
		if mod.ProjectID == "" {
			hashes[sha1File(filepath.Join(m.dir, mod.File))] = strings.TrimSuffix(mod.File, ".disabled")
		}
	}
	if len(hashes) == 0 {
		return
	}
	var list []string
	for h := range hashes {
		list = append(list, h)
	}
	var found map[string]modrinthVersion
	if err := postJSON(ctx, modrinthAPI+"/version_files", map[string]any{"hashes": list, "algorithm": "sha1"}, &found, nil); err != nil {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for h, v := range found {
		var project modrinthProject
		_ = getJSON(ctx, modrinthAPI+"/project/"+url.PathEscape(v.ProjectID), &project)
		m.track(hashes[h], v.ProjectID, v.ID, project.Title, project.IconURL)
	}
	m.saveIndex()
}

// CheckUpdates returns installed mods with the newer version number filled in where one exists.
func (m *ModManager) CheckUpdates(ctx context.Context) ([]InstalledMod, error) {
	m.Identify(ctx)
	mods, err := m.List()
	if err != nil {
		return nil, err
	}
	byHash := map[string]int{}
	var hashes []string
	for i, mod := range mods {
		if mod.ModID == clientModID {
			continue // updated with the launcher itself
		}
		h := sha1File(filepath.Join(m.dir, mod.File))
		byHash[h] = i
		hashes = append(hashes, h)
	}
	var latest map[string]modrinthVersion
	err = postJSON(ctx, modrinthAPI+"/version_files/update", map[string]any{
		"hashes": hashes, "algorithm": "sha1", "loaders": []string{"fabric"}, "game_versions": []string{minecraftVersion},
	}, &latest, nil)
	if err != nil {
		return mods, err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for h, v := range latest {
		i, ok := byHash[h]
		if !ok {
			continue
		}
		if f := v.primaryFile(); f != nil && !strings.EqualFold(f.Hashes.SHA1, h) {
			mods[i].Update = v.VersionNumber
		}
	}
	return mods, nil
}

// UpdateAll installs the newest version of every mod that has one.
func (m *ModManager) UpdateAll(ctx context.Context) ([]string, error) {
	mods, err := m.CheckUpdates(ctx)
	if err != nil {
		return nil, err
	}
	var done []string
	for _, mod := range mods {
		if mod.Update == "" || mod.ProjectID == "" {
			continue
		}
		names, err := m.Install(ctx, mod.ProjectID)
		if err != nil {
			return done, fmt.Errorf("%s: %w", mod.Name, err)
		}
		done = append(done, names...)
	}
	return done, nil
}

// HasProject reports whether a Modrinth project is installed (used by the performance page).
func (m *ModManager) HasProject(projectID string) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	_, ok := m.installedProjects()[projectID]
	return ok
}

// RemoveProject deletes a project's jar if we installed it.
func (m *ModManager) RemoveProject(projectID string) error {
	m.mu.Lock()
	file, ok := m.installedProjects()[projectID]
	m.mu.Unlock()
	if !ok {
		return nil
	}
	if _, err := os.Stat(filepath.Join(m.dir, file)); err == nil {
		return m.Remove(file)
	}
	return m.Remove(file + ".disabled")
}
