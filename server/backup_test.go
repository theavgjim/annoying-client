package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

// fakeGists is a minimal GitHub gist API.
type fakeGists struct {
	mu     sync.Mutex
	gists  map[string]gistInfo
	writes int
	fail   bool
}

func (f *fakeGists) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if r.Header.Get("Authorization") != "Bearer test-token" {
		http.Error(w, `{"message":"Bad credentials"}`, 401)
		return
	}
	if f.fail {
		http.Error(w, "down", 503)
		return
	}
	switch {
	case r.Method == "GET" && r.URL.Path == "/gists":
		list := []gistInfo{{ID: "other", Description: "someone's notes", Files: map[string]gistFileInfo{"a.txt": {}}}}
		for _, g := range f.gists {
			list = append(list, g)
		}
		_ = json.NewEncoder(w).Encode(list)
	case r.Method == "POST" && r.URL.Path == "/gists":
		var in struct {
			Description string                       `json:"description"`
			Public      bool                         `json:"public"`
			Files       map[string]map[string]string `json:"files"`
		}
		_ = json.NewDecoder(r.Body).Decode(&in)
		if in.Public {
			http.Error(w, "test: backups must be secret gists", 400)
			return
		}
		g := gistInfo{ID: "g1", Description: in.Description, Files: map[string]gistFileInfo{}}
		for name, file := range in.Files {
			g.Files[name] = gistFileInfo{Content: file["content"]}
		}
		f.gists[g.ID] = g
		f.writes++
		_ = json.NewEncoder(w).Encode(g)
	case strings.HasPrefix(r.URL.Path, "/gists/"):
		g, ok := f.gists[strings.TrimPrefix(r.URL.Path, "/gists/")]
		if !ok {
			http.Error(w, "not found", 404)
			return
		}
		if r.Method == "PATCH" {
			var in struct {
				Files map[string]map[string]string `json:"files"`
			}
			_ = json.NewDecoder(r.Body).Decode(&in)
			for name, file := range in.Files {
				g.Files[name] = gistFileInfo{Content: file["content"]}
			}
			f.gists[g.ID] = g
			f.writes++
		}
		_ = json.NewEncoder(w).Encode(g)
	default:
		http.Error(w, "unexpected "+r.Method+" "+r.URL.Path, 400)
	}
}

func testBackup(api string) *gistBackup {
	g := newGistBackup("test-token", "")
	g.api = api
	return g
}

func TestGistBackupSurvivesRestart(t *testing.T) {
	fake := &fakeGists{gists: map[string]gistInfo{}}
	gh := httptest.NewServer(fake)
	defer gh.Close()

	// First server: empty disk, no gist yet.
	s1, err := openStoreWithBackup(filepath.Join(t.TempDir(), "db.json"), testBackup(gh.URL))
	if err != nil {
		t.Fatal(err)
	}
	if err := s1.update(func(db *dbFile) error {
		db.Players["abc"] = &Player{UUID: "abc", Name: "theavgjim_", Coins: 4321, Owned: []string{"cape_galaxy"}, Equipped: map[string]string{"cape": "cape_galaxy"}}
		db.Sessions["secret-token"] = &session{UUID: "abc", Expires: time.Now().Add(time.Hour)}
		return nil
	}); err != nil {
		t.Fatal(err)
	}
	if err := s1.Flush(context.Background()); err != nil {
		t.Fatal(err)
	}
	stored := fake.gists["g1"].Files[gistFile].Content
	if !strings.Contains(stored, "4321") {
		t.Fatalf("backup is missing the player: %s", stored)
	}
	if strings.Contains(stored, "secret-token") {
		t.Fatal("login sessions must not be written to the backup")
	}
	// Nothing changed: no extra upload.
	writes := fake.writes
	if err := s1.Flush(context.Background()); err != nil || fake.writes != writes {
		t.Fatalf("unchanged database was uploaded again (err %v)", err)
	}

	// Second server: a new, empty disk (the free host wiped it). It finds the gist by itself.
	s2, err := openStoreWithBackup(filepath.Join(t.TempDir(), "db.json"), testBackup(gh.URL))
	if err != nil {
		t.Fatal(err)
	}
	var p *Player
	s2.view(func(db *dbFile) { p = db.Players["abc"] })
	if p == nil || p.Coins != 4321 || p.Equipped["cape"] != "cape_galaxy" {
		t.Fatalf("player not restored: %+v", p)
	}
	if err := s2.update(func(db *dbFile) error { db.Players["abc"].Coins = 5000; return nil }); err != nil {
		t.Fatal(err)
	}
	if err := s2.Flush(context.Background()); err != nil {
		t.Fatal(err)
	}
	if len(fake.gists) != 1 || !strings.Contains(fake.gists["g1"].Files[gistFile].Content, "5000") {
		t.Fatalf("expected the same gist to be updated, have %d gists", len(fake.gists))
	}
}

func TestBackgroundBackupAfterChange(t *testing.T) {
	fake := &fakeGists{gists: map[string]gistInfo{}}
	gh := httptest.NewServer(fake)
	defer gh.Close()
	old := backupDelay
	backupDelay = 10 * time.Millisecond
	defer func() { backupDelay = old }()

	s, err := openStoreWithBackup(filepath.Join(t.TempDir(), "db.json"), testBackup(gh.URL))
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go s.RunBackups(ctx)
	_ = s.update(func(db *dbFile) error { db.Players["x"] = &Player{UUID: "x", Name: "Alex", Coins: 77}; return nil })
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		fake.mu.Lock()
		done := strings.Contains(fake.gists["g1"].Files[gistFile].Content, "Alex")
		fake.mu.Unlock()
		if done {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("change was not backed up in the background")
}

func TestUnreadableBackupStopsStartup(t *testing.T) {
	fake := &fakeGists{gists: map[string]gistInfo{}, fail: true}
	gh := httptest.NewServer(fake)
	defer gh.Close()
	if _, err := openStoreWithBackup(filepath.Join(t.TempDir(), "db.json"), testBackup(gh.URL)); err == nil {
		t.Fatal("starting with an empty database while the backup can't be read would erase it on the next save")
	}
	bad := newGistBackup("wrong", "")
	bad.api = gh.URL
	if _, err := openStoreWithBackup(filepath.Join(t.TempDir(), "db.json"), bad); err == nil || !strings.Contains(err.Error(), "GITHUB_TOKEN") {
		t.Fatalf("a wrong token should explain itself, got %v", err)
	}
}
