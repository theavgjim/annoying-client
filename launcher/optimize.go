package main

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// PerfMod is a performance mod the launcher can install with one click.
type PerfMod struct {
	ProjectID   string `json:"projectId"`
	Slug        string `json:"slug"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Default     bool   `json:"default"` // part of the recommended set
	Installed   bool   `json:"installed"`
	IconURL     string `json:"iconUrl"`
}

// performanceMods are well-known, widely used Fabric optimization mods (all on Modrinth for 1.21.11).
var performanceMods = []PerfMod{
	{ProjectID: "AANobbMI", Slug: "sodium", Name: "Sodium", Description: "Modern rendering engine; usually doubles or triples FPS", Default: true},
	{ProjectID: "gvQqBUqZ", Slug: "lithium", Name: "Lithium", Description: "Optimizes game logic, physics and chunk ticking", Default: true},
	{ProjectID: "uXXizFIs", Slug: "ferrite-core", Name: "FerriteCore", Description: "Cuts memory use substantially", Default: true},
	{ProjectID: "NNAgCjsB", Slug: "entityculling", Name: "Entity Culling", Description: "Skips rendering entities and block entities you cannot see", Default: true},
	{ProjectID: "5ZwdcRci", Slug: "immediatelyfast", Name: "ImmediatelyFast", Description: "Speeds up HUD, text and map rendering", Default: true},
}

// FPSPreset is a set of video settings written to options.txt.
type FPSPreset struct {
	ID          string            `json:"id"`
	Name        string            `json:"name"`
	Description string            `json:"description"`
	Options     map[string]string `json:"options"`
}

var fpsPresets = []FPSPreset{
	{ID: "max", Name: "Max FPS", Description: "Lowest settings for the highest frame rate. Best for low-end PCs and PvP.",
		Options: map[string]string{
			"graphicsPreset": `"custom"`, "renderDistance": "8", "simulationDistance": "6", "maxFps": "260",
			"enableVsync": "false", "renderClouds": `"false"`, "particles": "2", "entityShadows": "false",
			"ao": "false", "biomeBlendRadius": "0", "entityDistanceScaling": "0.75", "mipmapLevels": "0",
			"improvedTransparency": "false", "cutoutLeaves": "false", "chunkSectionFadeInTime": "0.0",
			"weatherRadius": "5", "inactivityFpsLimit": `"afk"`,
		}},
	{ID: "balanced", Name: "Balanced", Description: "Good looks with high FPS on most PCs. Recommended.",
		Options: map[string]string{
			"graphicsPreset": `"custom"`, "renderDistance": "12", "simulationDistance": "8", "maxFps": "260",
			"enableVsync": "false", "renderClouds": `"fast"`, "particles": "1", "entityShadows": "true",
			"ao": "true", "biomeBlendRadius": "2", "entityDistanceScaling": "1.0", "mipmapLevels": "2",
			"improvedTransparency": "false", "cutoutLeaves": "true", "chunkSectionFadeInTime": "0.5",
			"weatherRadius": "10", "inactivityFpsLimit": `"afk"`,
		}},
	{ID: "quality", Name: "Quality", Description: "Far view distance and full effects for strong PCs.",
		Options: map[string]string{
			"graphicsPreset": `"custom"`, "renderDistance": "20", "simulationDistance": "12", "maxFps": "260",
			"enableVsync": "false", "renderClouds": `"true"`, "particles": "0", "entityShadows": "true",
			"ao": "true", "biomeBlendRadius": "5", "entityDistanceScaling": "1.25", "mipmapLevels": "4",
			"improvedTransparency": "true", "cutoutLeaves": "true", "chunkSectionFadeInTime": "0.75",
			"weatherRadius": "10", "inactivityFpsLimit": `"afk"`,
		}},
}

func findPreset(id string) *FPSPreset {
	for i := range fpsPresets {
		if fpsPresets[i].ID == id {
			return &fpsPresets[i]
		}
	}
	return nil
}

// applyOptions merges values into options.txt, keeping every other line exactly as it was.
// When the file does not exist yet, the data version is written too so Minecraft does not run
// its old-format upgrade over the new values.
func applyOptions(instanceDir string, values map[string]string, dataVersion int) error {
	path := filepath.Join(instanceDir, "options.txt")
	var lines []string
	seen := map[string]bool{}
	if f, err := os.Open(path); err == nil {
		s := bufio.NewScanner(f)
		s.Buffer(make([]byte, 1024*1024), 1024*1024)
		for s.Scan() {
			line := s.Text()
			key, _, ok := strings.Cut(line, ":")
			if ok {
				if v, managed := values[key]; managed {
					line = key + ":" + v
					seen[key] = true
				}
			}
			lines = append(lines, line)
		}
		f.Close()
	} else if dataVersion > 0 {
		lines = append(lines, fmt.Sprintf("version:%d", dataVersion))
	}
	keys := make([]string, 0, len(values))
	for k := range values {
		if !seen[k] {
			keys = append(keys, k)
		}
	}
	sortStrings(keys)
	for _, k := range keys {
		lines = append(lines, k+":"+values[k])
	}
	return writeFileAtomic(path, []byte(strings.Join(lines, "\n")+"\n"))
}

func sortStrings(s []string) {
	for i := 1; i < len(s); i++ {
		for j := i; j > 0 && s[j] < s[j-1]; j-- {
			s[j], s[j-1] = s[j-1], s[j]
		}
	}
}

// jvmArgs builds the memory and garbage-collector flags for the game.
func jvmArgs(s Settings) []string {
	args := []string{fmt.Sprintf("-Xmx%dM", s.MemoryMB), fmt.Sprintf("-Xms%dM", min(s.MemoryMB, max(1024, s.MemoryMB/2)))}
	switch s.GC {
	case "zgc":
		// Generational ZGC (Java 21): very short pauses, needs a bit more memory.
		args = append(args, "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+AlwaysPreTouch", "-XX:+DisableExplicitGC")
	default:
		// Aikar's widely used G1 tuning: fewer and shorter garbage-collection stutters.
		args = append(args,
			"-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
			"-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
			"-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
			"-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
			"-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
			"-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem",
			"-XX:MaxTenuringThreshold=1")
	}
	for _, extra := range strings.Fields(s.ExtraJVMArgs) {
		args = append(args, extra)
	}
	return args
}
