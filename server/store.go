package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"
)

// Player is everything the server stores about one Minecraft account (keyed by UUID).
type Player struct {
	UUID      string            `json:"uuid"`
	Name      string            `json:"name"`
	Coins     int               `json:"coins"`
	Owned     []string          `json:"owned"`
	Equipped  map[string]string `json:"equipped"` // type -> cosmetic id
	Admin     bool              `json:"admin"`
	LastDaily time.Time         `json:"lastDaily"`
	FirstSeen time.Time         `json:"firstSeen"`
	LastSeen  time.Time         `json:"lastSeen"`
}

type session struct {
	UUID    string    `json:"uuid"`
	Expires time.Time `json:"expires"`
}

type Announcement struct {
	ID      int       `json:"id"`
	Message string    `json:"message"`
	By      string    `json:"by"`
	At      time.Time `json:"at"`
}

type dbFile struct {
	Players      map[string]*Player  `json:"players"`
	Sessions     map[string]*session `json:"sessions"`
	Announcement *Announcement       `json:"announcement"`
	AuditLog     []string            `json:"auditLog"`
}

// Store is a small JSON-file database. Every change is written atomically (temp file + rename).
type Store struct {
	mu   sync.Mutex
	path string
	db   dbFile
}

func openStore(path string) (*Store, error) {
	s := &Store{path: path}
	data, err := os.ReadFile(path)
	switch {
	case errors.Is(err, os.ErrNotExist):
	case err != nil:
		return nil, err
	default:
		if err := json.Unmarshal(data, &s.db); err != nil {
			return nil, errors.New("database file is damaged: " + err.Error())
		}
	}
	if s.db.Players == nil {
		s.db.Players = map[string]*Player{}
	}
	if s.db.Sessions == nil {
		s.db.Sessions = map[string]*session{}
	}
	return s, nil
}

// update runs f under the lock and saves when it returns nil.
func (s *Store) update(f func(db *dbFile) error) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := f(&s.db); err != nil {
		return err
	}
	return s.saveLocked()
}

func (s *Store) view(f func(db *dbFile)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	f(&s.db)
}

func (s *Store) saveLocked() error {
	now := time.Now()
	for k, v := range s.db.Sessions {
		if now.After(v.Expires) {
			delete(s.db.Sessions, k)
		}
	}
	if len(s.db.AuditLog) > 2000 {
		s.db.AuditLog = s.db.AuditLog[len(s.db.AuditLog)-2000:]
	}
	data, err := json.MarshalIndent(&s.db, "", " ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(s.path), 0o755); err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}

func randomHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func (db *dbFile) playerByName(name string) *Player {
	for _, p := range db.Players {
		if strings.EqualFold(p.Name, name) {
			return p
		}
	}
	return nil
}

func (p *Player) owns(id string) bool { return slices.Contains(p.Owned, id) }

func (p *Player) give(id string) {
	if !p.owns(id) {
		p.Owned = append(p.Owned, id)
	}
}

func (p *Player) take(id string) {
	p.Owned = slices.DeleteFunc(p.Owned, func(s string) bool { return s == id })
	for t, e := range p.Equipped {
		if e == id {
			delete(p.Equipped, t)
		}
	}
}

func (db *dbFile) audit(format string) {
	db.AuditLog = append(db.AuditLog, time.Now().UTC().Format(time.RFC3339)+" "+format)
}
