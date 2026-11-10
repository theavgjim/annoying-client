package main

import (
	"encoding/json"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"sync"
)

// Settings are the user's launcher preferences, stored in <data>/launcher.json.
type Settings struct {
	MemoryMB        int    `json:"memoryMB"`
	GC              string `json:"gc"` // "g1" (Aikar's flags) or "zgc"
	ExtraJVMArgs    string `json:"extraJvmArgs"`
	JavaPath        string `json:"javaPath"` // empty: use the Java runtime the launcher manages
	Width           int    `json:"width"`
	Height          int    `json:"height"`
	Fullscreen      bool   `json:"fullscreen"`
	LaunchMode      string `json:"launchMode"` // "official" or "direct"
	ClientID        string `json:"clientId"`   // Azure application ID approved for Minecraft (direct mode)
	KeepOpen        bool   `json:"keepOpen"`
	FPSPreset       string `json:"fpsPreset"` // last applied preset
	DownloadThreads int    `json:"downloadThreads"`
}

func defaultSettings() Settings {
	return Settings{
		MemoryMB:        recommendedMemoryMB(),
		GC:              "g1",
		Width:           1280,
		Height:          720,
		LaunchMode:      "official",
		KeepOpen:        true,
		DownloadThreads: 16,
	}
}

type SettingsStore struct {
	mu   sync.Mutex
	path string
	s    Settings
}

func loadSettings(path string) *SettingsStore {
	st := &SettingsStore{path: path, s: defaultSettings()}
	data, err := os.ReadFile(path)
	if err == nil {
		if jsonErr := json.Unmarshal(data, &st.s); jsonErr != nil {
			logf("launcher.json is invalid (%v); using defaults", jsonErr)
			_ = os.Rename(path, path+".corrupt")
			st.s = defaultSettings()
		}
	} else if !errors.Is(err, fs.ErrNotExist) {
		logf("cannot read %s: %v", path, err)
	}
	st.s = st.s.normalized()
	return st
}

func (s Settings) normalized() Settings {
	d := defaultSettings()
	maxMem := totalMemoryMB() - 1024
	if maxMem < 1024 {
		maxMem = 1024
	}
	if s.MemoryMB < 1024 || s.MemoryMB > maxMem {
		s.MemoryMB = min(max(s.MemoryMB, 1024), maxMem)
	}
	if s.GC != "g1" && s.GC != "zgc" {
		s.GC = d.GC
	}
	if s.Width < 320 || s.Width > 7680 {
		s.Width = d.Width
	}
	if s.Height < 240 || s.Height > 4320 {
		s.Height = d.Height
	}
	if s.LaunchMode != "official" && s.LaunchMode != "direct" {
		s.LaunchMode = d.LaunchMode
	}
	if s.DownloadThreads < 1 || s.DownloadThreads > 64 {
		s.DownloadThreads = d.DownloadThreads
	}
	return s
}

func (st *SettingsStore) Get() Settings {
	st.mu.Lock()
	defer st.mu.Unlock()
	return st.s
}

func (st *SettingsStore) Update(f func(*Settings)) (Settings, error) {
	st.mu.Lock()
	defer st.mu.Unlock()
	next := st.s
	f(&next)
	next = next.normalized()
	data, _ := json.MarshalIndent(next, "", "  ")
	if err := writeFileAtomic(st.path, data); err != nil {
		return st.s, err
	}
	st.s = next
	return next, nil
}

func writeFileAtomic(path string, data []byte) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return nil
}
