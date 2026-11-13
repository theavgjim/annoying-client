package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"
)

// Backup keeps a copy of the database somewhere that survives restarts. Free hosts such as Render's
// free plan wipe the disk whenever the server sleeps or restarts, so without one, coins and cosmetics
// would reset.
type Backup interface {
	// Load returns the saved database, or nil if nothing has been saved yet.
	Load(ctx context.Context) ([]byte, error)
	Save(ctx context.Context, data []byte) error
	Name() string
}

const (
	gistDescription = "Annoying Client cosmetics database (do not edit)"
	gistFile        = "cosmetics.json"
)

// gistBackup stores the database as a secret GitHub gist owned by the token's account. Create the token
// at github.com/settings/tokens with only the "gist" scope and set it as GITHUB_TOKEN.
type gistBackup struct {
	token  string
	id     string // GIST_ID, or found by description on first use
	api    string
	client *http.Client
}

func newGistBackup(token, id string) *gistBackup {
	return &gistBackup{token: token, id: id, api: "https://api.github.com", client: &http.Client{Timeout: 30 * time.Second}}
}

func (g *gistBackup) Name() string {
	if g.id == "" {
		return "GitHub gist (created on first save)"
	}
	return "GitHub gist " + g.id
}

func (g *gistBackup) do(ctx context.Context, method, path string, body any, out any) error {
	var rd io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return err
		}
		rd = bytes.NewReader(b)
	}
	req, err := http.NewRequestWithContext(ctx, method, g.api+path, rd)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+g.token)
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("X-GitHub-Api-Version", "2022-11-28")
	req.Header.Set("User-Agent", "annoying-client-cosmetics-server")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := g.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode >= 300 {
		msg := strings.TrimSpace(string(data))
		if len(msg) > 200 {
			msg = msg[:200]
		}
		if resp.StatusCode == http.StatusUnauthorized {
			msg = "GITHUB_TOKEN is wrong or expired"
		}
		return fmt.Errorf("github %s %s: HTTP %d: %s", method, path, resp.StatusCode, msg)
	}
	if out != nil {
		return json.Unmarshal(data, out)
	}
	return nil
}

type gistFileInfo struct {
	Content   string `json:"content"`
	Truncated bool   `json:"truncated"`
	RawURL    string `json:"raw_url"`
}

type gistInfo struct {
	ID          string                  `json:"id"`
	Description string                  `json:"description"`
	Files       map[string]gistFileInfo `json:"files"`
}

// find looks for an existing database gist, so a restarted server finds its data without GIST_ID.
func (g *gistBackup) find(ctx context.Context) error {
	if g.id != "" {
		return nil
	}
	for page := 1; page <= 10; page++ {
		var list []gistInfo
		if err := g.do(ctx, "GET", fmt.Sprintf("/gists?per_page=100&page=%d", page), nil, &list); err != nil {
			return err
		}
		for _, gi := range list {
			if _, ok := gi.Files[gistFile]; ok && gi.Description == gistDescription {
				g.id = gi.ID
				return nil
			}
		}
		if len(list) < 100 {
			return nil
		}
	}
	return nil
}

func (g *gistBackup) Load(ctx context.Context) ([]byte, error) {
	if err := g.find(ctx); err != nil {
		return nil, err
	}
	if g.id == "" {
		return nil, nil
	}
	var gi gistInfo
	if err := g.do(ctx, "GET", "/gists/"+g.id, nil, &gi); err != nil {
		return nil, err
	}
	f, ok := gi.Files[gistFile]
	if !ok {
		return nil, nil
	}
	if !f.Truncated {
		return []byte(f.Content), nil
	}
	// Files over about 1 MB are cut off in the API response; fetch the whole file.
	req, err := http.NewRequestWithContext(ctx, "GET", f.RawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+g.token)
	resp, err := g.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("github raw file: HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 64<<20))
}

func (g *gistBackup) Save(ctx context.Context, data []byte) error {
	if err := g.find(ctx); err != nil {
		return err
	}
	files := map[string]any{gistFile: map[string]string{"content": string(data)}}
	if g.id == "" {
		var gi gistInfo
		if err := g.do(ctx, "POST", "/gists", map[string]any{"description": gistDescription, "public": false, "files": files}, &gi); err != nil {
			return err
		}
		if gi.ID == "" {
			return errors.New("github did not return a gist id")
		}
		g.id = gi.ID
		log.Printf("created backup gist %s", g.id)
		return nil
	}
	return g.do(ctx, "PATCH", "/gists/"+g.id, map[string]any{"files": files}, nil)
}

// ---- store integration -----------------------------------------------------------------------

// backupDelay batches changes: at most one upload per this interval (GitHub allows 500 writes/hour).
var backupDelay = 15 * time.Second

// backupBytes is the database without login sessions: a leaked backup must not let anyone sign in.
func (s *Store) backupBytesLocked() ([]byte, error) {
	copyDB := s.db
	copyDB.Sessions = nil
	return json.Marshal(&copyDB)
}

func (s *Store) markDirtyLocked() {
	s.version++
	if s.backup != nil {
		select {
		case s.dirty <- struct{}{}:
		default:
		}
	}
}

// RunBackups uploads changes in the background until ctx ends.
func (s *Store) RunBackups(ctx context.Context) {
	if s.backup == nil {
		return
	}
	for {
		select {
		case <-ctx.Done():
			return
		case <-s.dirty:
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(backupDelay):
		}
		if err := s.Flush(ctx); err != nil {
			log.Printf("backup failed, retrying in a minute: %v", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(time.Minute):
			}
			s.mu.Lock()
			s.markDirtyLocked()
			s.mu.Unlock()
		}
	}
}

// Flush uploads the database now if it changed since the last upload.
func (s *Store) Flush(ctx context.Context) error {
	if s.backup == nil {
		return nil
	}
	s.flushMu.Lock()
	defer s.flushMu.Unlock()
	s.mu.Lock()
	version := s.version
	if version == s.backedUp {
		s.mu.Unlock()
		return nil
	}
	data, err := s.backupBytesLocked()
	s.mu.Unlock()
	if err != nil {
		return err
	}
	if err := s.backup.Save(ctx, data); err != nil {
		return err
	}
	s.mu.Lock()
	s.backedUp = version
	s.mu.Unlock()
	return nil
}
