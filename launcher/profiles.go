package main

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
)

// A Profile is a separate game folder with its own mods, settings, worlds and screenshots.
// The original folder (<data>/instance) is the "default" profile; others live in <data>/profiles/<id>.
type Profile struct {
	ID       string    `json:"id"`
	Name     string    `json:"name"`
	Template string    `json:"template,omitempty"`
	Created  time.Time `json:"created"`
}

type ProfileStore struct {
	mu       sync.Mutex
	path     string
	data     string
	Active   string    `json:"active"`
	Profiles []Profile `json:"profiles"`
}

// PinnedMod is one exact Modrinth file in a profile template.
type PinnedMod struct {
	ProjectID     string `json:"projectId"`
	VersionID     string `json:"versionId"`
	Title         string `json:"title"`
	IconURL       string `json:"iconUrl"`
	VersionNumber string `json:"versionNumber"`
	Filename      string `json:"filename"`
	URL           string `json:"url"`
	SHA1          string `json:"sha1"`
	SHA512        string `json:"sha512"`
	Size          int64  `json:"size"`
}

// ProfileTemplate is a ready-made mod list offered when creating a profile.
type ProfileTemplate struct {
	ID          string      `json:"id"`
	Name        string      `json:"name"`
	Description string      `json:"description"`
	Mods        []PinnedMod `json:"mods"`
}

//go:embed embedded/profile-optimized.json
var optimizedTemplateJSON []byte

func profileTemplates() []ProfileTemplate {
	var t struct {
		Mods []PinnedMod `json:"mods"`
	}
	_ = json.Unmarshal(optimizedTemplateJSON, &t)
	return []ProfileTemplate{
		{ID: "empty", Name: "Annoying Client only", Description: "Just Annoying Client and Fabric API. Add mods yourself."},
		{ID: "optimized", Name: "1.21.11 optimized", Description: fmt.Sprintf("%d mods: Sodium, Iris, Lithium, Entity Culling, Xaero's World Map, Simple Voice Chat, Mod Menu and more.", len(t.Mods)), Mods: t.Mods},
	}
}

func findTemplate(id string) *ProfileTemplate {
	for _, t := range profileTemplates() {
		if t.ID == id {
			tt := t
			return &tt
		}
	}
	return nil
}

func loadProfiles(dataDir string) *ProfileStore {
	ps := &ProfileStore{path: filepath.Join(dataDir, "profiles.json"), data: dataDir}
	if b, err := os.ReadFile(ps.path); err == nil {
		if json.Unmarshal(b, ps) != nil {
			logf("profiles.json is invalid; starting with the default profile")
			_ = os.Rename(ps.path, ps.path+".corrupt")
			ps.Profiles = nil
		}
	}
	hasDefault := false
	for _, p := range ps.Profiles {
		if p.ID == "default" {
			hasDefault = true
		}
	}
	if !hasDefault {
		ps.Profiles = append([]Profile{{ID: "default", Name: "Annoying Client", Created: time.Now()}}, ps.Profiles...)
	}
	if ps.find(ps.Active) == nil {
		ps.Active = "default"
	}
	_ = ps.save()
	return ps
}

func (ps *ProfileStore) save() error {
	b, _ := json.MarshalIndent(ps, "", "  ")
	return writeFileAtomic(ps.path, b)
}

func (ps *ProfileStore) find(id string) *Profile {
	for i := range ps.Profiles {
		if ps.Profiles[i].ID == id {
			return &ps.Profiles[i]
		}
	}
	return nil
}

func (ps *ProfileStore) Dir(id string) string {
	if id == "default" {
		return filepath.Join(ps.data, "instance")
	}
	return filepath.Join(ps.data, "profiles", id)
}

func (ps *ProfileStore) List() ([]Profile, string) {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	return append([]Profile(nil), ps.Profiles...), ps.Active
}

func (ps *ProfileStore) ActiveProfile() Profile {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	return *ps.find(ps.Active)
}

var slugRe = regexp.MustCompile(`[^a-z0-9]+`)

func (ps *ProfileStore) Create(name, template string) (Profile, error) {
	name = strings.TrimSpace(name)
	if name == "" || len(name) > 40 {
		return Profile{}, errors.New("profile names must be 1 to 40 characters")
	}
	ps.mu.Lock()
	defer ps.mu.Unlock()
	for _, p := range ps.Profiles {
		if strings.EqualFold(p.Name, name) {
			return Profile{}, fmt.Errorf("a profile named %q already exists", name)
		}
	}
	base := strings.Trim(slugRe.ReplaceAllString(strings.ToLower(name), "-"), "-")
	if base == "" || base == "default" {
		base = "profile"
	}
	id := base
	for n := 2; ps.find(id) != nil; n++ {
		id = fmt.Sprintf("%s-%d", base, n)
	}
	p := Profile{ID: id, Name: name, Template: template, Created: time.Now()}
	if err := os.MkdirAll(filepath.Join(ps.Dir(id), "mods"), 0o755); err != nil {
		return Profile{}, err
	}
	ps.Profiles = append(ps.Profiles, p)
	return p, ps.save()
}

func (ps *ProfileStore) Select(id string) error {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	if ps.find(id) == nil {
		return errors.New("that profile does not exist")
	}
	ps.Active = id
	return ps.save()
}

func (ps *ProfileStore) Rename(id, name string) error {
	name = strings.TrimSpace(name)
	if name == "" || len(name) > 40 {
		return errors.New("profile names must be 1 to 40 characters")
	}
	ps.mu.Lock()
	defer ps.mu.Unlock()
	p := ps.find(id)
	if p == nil {
		return errors.New("that profile does not exist")
	}
	p.Name = name
	return ps.save()
}

// Delete removes a profile and its folder (worlds included). The default profile cannot be deleted.
func (ps *ProfileStore) Delete(id string) error {
	if id == "default" {
		return errors.New("the default profile cannot be deleted")
	}
	ps.mu.Lock()
	defer ps.mu.Unlock()
	idx := -1
	for i, p := range ps.Profiles {
		if p.ID == id {
			idx = i
		}
	}
	if idx < 0 {
		return errors.New("that profile does not exist")
	}
	if err := os.RemoveAll(ps.Dir(id)); err != nil {
		return err
	}
	ps.Profiles = append(ps.Profiles[:idx], ps.Profiles[idx+1:]...)
	if ps.Active == id {
		ps.Active = "default"
	}
	return ps.save()
}

// InstallPinned downloads exact mod files (checksums verified) into this mod folder and records them.
func (m *ModManager) InstallPinned(ctx context.Context, mods []PinnedMod, threads int, report func(Progress)) error {
	jobs := make([]DownloadJob, 0, len(mods))
	for _, pm := range mods {
		name := filepath.Base(pm.Filename)
		if !strings.HasSuffix(name, ".jar") {
			return fmt.Errorf("%s is not a jar file", name)
		}
		jobs = append(jobs, DownloadJob{URL: pm.URL, Path: filepath.Join(m.dir, name), SHA1: pm.SHA1, Size: pm.Size})
	}
	if err := downloadAll(ctx, "Downloading mods", jobs, threads, report); err != nil {
		return err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, pm := range mods {
		m.track(filepath.Base(pm.Filename), pm.ProjectID, pm.VersionID, pm.Title, pm.IconURL)
	}
	m.saveIndex()
	return nil
}
