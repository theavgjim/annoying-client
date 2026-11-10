package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestMavenPath(t *testing.T) {
	cases := map[string]string{
		"net.fabricmc:fabric-loader:0.20.1":     "net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar",
		"org.lwjgl:lwjgl:3.3.3:natives-windows": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
		"com.example:thing:1.0@zip":             "com/example/thing/1.0/thing-1.0.zip",
	}
	for in, want := range cases {
		got, err := mavenPath(in)
		if err != nil || got != want {
			t.Errorf("mavenPath(%q) = %q, %v; want %q", in, got, err, want)
		}
	}
	if _, err := mavenPath("broken"); err == nil {
		t.Error("expected error for invalid coordinate")
	}
}

func TestRules(t *testing.T) {
	var rules []Rule
	_ = json.Unmarshal([]byte(`[{"action":"allow","os":{"name":"`+mojangOSName()+`"}}]`), &rules)
	if !rulesAllow(rules, nil) {
		t.Error("rule for this OS should allow")
	}
	_ = json.Unmarshal([]byte(`[{"action":"allow"},{"action":"disallow","os":{"name":"`+mojangOSName()+`"}}]`), &rules)
	if rulesAllow(rules, nil) {
		t.Error("disallow for this OS should win")
	}
	_ = json.Unmarshal([]byte(`[{"action":"allow","features":{"is_demo_user":true}}]`), &rules)
	if rulesAllow(rules, map[string]bool{}) || !rulesAllow(rules, map[string]bool{"is_demo_user": true}) {
		t.Error("feature rules not evaluated correctly")
	}
}

func TestMergeVersionsPrefersChildLibraries(t *testing.T) {
	parent := &VersionJSON{ID: "1.21.11", MainClass: "net.minecraft.client.main.Main",
		Libraries: []Library{{Name: "org.ow2.asm:asm:9.0"}, {Name: "com.mojang:brigadier:1.3.10"}}}
	child := &VersionJSON{ID: "fabric", MainClass: "net.fabricmc.loader.impl.launch.knot.KnotClient",
		Libraries: []Library{{Name: "org.ow2.asm:asm:9.10.1"}}}
	m := mergeVersions(parent, child)
	if m.MainClass != child.MainClass || m.ID != "fabric" {
		t.Fatalf("main class/id not taken from child: %+v", m)
	}
	if len(m.Libraries) != 2 || m.Libraries[0].Name != "org.ow2.asm:asm:9.10.1" {
		t.Fatalf("libraries not merged correctly: %+v", m.Libraries)
	}
}

func TestBuildArgs(t *testing.T) {
	dir := t.TempDir()
	p := newPaths(dir)
	g := &Game{paths: p}
	v := &VersionJSON{ID: "fabric-test", MainClass: "Main"}
	v.AssetIndex.ID = "29"
	v.Arguments.Game = []json.RawMessage{
		json.RawMessage(`"--username"`), json.RawMessage(`"${auth_player_name}"`),
		json.RawMessage(`{"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width","${resolution_width}"]}`),
		json.RawMessage(`{"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"}`),
	}
	v.Arguments.JVM = []json.RawMessage{json.RawMessage(`"-cp"`), json.RawMessage(`"${classpath}"`)}
	s := defaultSettings()
	s.Width = 1600
	args := g.buildArgs(&prepared{version: v, classpath: []string{"a.jar", "b.jar"}}, LaunchAccount{Name: "Steve", AccessToken: "tok-0123456789abcdef"}, s)
	joined := strings.Join(args, " ")
	for _, want := range []string{"-Xmx", "-cp a.jar" + classpathSeparator() + "b.jar", "Main --username Steve --width 1600"} {
		if !strings.Contains(joined, want) {
			t.Errorf("args missing %q: %s", want, joined)
		}
	}
	if strings.Contains(joined, "--demo") {
		t.Error("demo flag must not be passed")
	}
	if strings.Contains(redactArgs(args, "tok-0123456789abcdef"), "tok-0123456789abcdef") {
		t.Error("token not redacted")
	}
}

func TestApplyOptionsKeepsOtherLines(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "options.txt")
	_ = os.WriteFile(path, []byte("version:4000\nfov:0.5\nrenderDistance:32\nkey_key.jump:key.keyboard.space\n"), 0o644)
	if err := applyOptions(dir, map[string]string{"renderDistance": "8", "maxFps": "260"}, 1); err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(path)
	got := string(data)
	for _, want := range []string{"version:4000\n", "fov:0.5\n", "renderDistance:8\n", "key_key.jump:key.keyboard.space\n", "maxFps:260\n"} {
		if !strings.Contains(got, want) {
			t.Errorf("options.txt missing %q:\n%s", want, got)
		}
	}
	if strings.Contains(got, "renderDistance:32") {
		t.Error("old value kept")
	}
	// New file gets the data version first.
	dir2 := t.TempDir()
	_ = applyOptions(dir2, map[string]string{"ao": "true"}, 4671)
	data, _ = os.ReadFile(filepath.Join(dir2, "options.txt"))
	if !strings.HasPrefix(string(data), "version:4671\n") {
		t.Errorf("new options.txt should start with the data version: %q", data)
	}
}

func TestSettingsNormalize(t *testing.T) {
	s := Settings{MemoryMB: 1, GC: "nope", Width: 5, LaunchMode: "weird", DownloadThreads: 999}.normalized()
	if s.MemoryMB < 1024 || s.GC != "g1" || s.Width != 1280 || s.LaunchMode != "official" || s.DownloadThreads != 16 {
		t.Errorf("not normalized: %+v", s)
	}
}

func TestModSafePath(t *testing.T) {
	m := &ModManager{dir: t.TempDir()}
	for _, bad := range []string{"../evil.jar", "a/b.jar", ".hidden", "", `..\x.jar`} {
		if _, err := m.safePath(bad); err == nil && (runtime.GOOS == "windows" || !strings.Contains(bad, `\`)) {
			t.Errorf("safePath accepted %q", bad)
		}
	}
	if _, err := m.safePath("sodium.jar"); err != nil {
		t.Error(err)
	}
}
