package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func newTestServer(t *testing.T) (*httptest.Server, *Server) {
	t.Helper()
	store, err := openStore(filepath.Join(t.TempDir(), "db.json"))
	if err != nil {
		t.Fatal(err)
	}
	s := &Server{store: store, admins: map[string]bool{"theavgjim_": true}, challenges: map[string]time.Time{}, limiter: newRateLimiter(100000, 100000)}
	// Fake Mojang: only names in this map are "verified".
	accounts := map[string]string{"theavgjim_": "0123456789abcdef0123456789abcdef", "Alex": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Steve": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
	s.verify = func(username, serverID string) (string, string, error) {
		if id, ok := accounts[username]; ok {
			return id, username, nil
		}
		return "", "", errors.New("not verified")
	}
	ts := httptest.NewServer(s.routes())
	t.Cleanup(ts.Close)
	return ts, s
}

func call(t *testing.T, ts *httptest.Server, method, path, token string, body any) (int, map[string]any) {
	t.Helper()
	var buf bytes.Buffer
	if body != nil {
		_ = json.NewEncoder(&buf).Encode(body)
	}
	req, _ := http.NewRequest(method, ts.URL+path, &buf)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	var out map[string]any
	_ = json.NewDecoder(resp.Body).Decode(&out)
	return resp.StatusCode, out
}

func login(t *testing.T, ts *httptest.Server, name string) (string, map[string]any) {
	t.Helper()
	_, start := call(t, ts, "POST", "/v1/auth/start", "", nil)
	code, out := call(t, ts, "POST", "/v1/auth/finish", "", map[string]string{"username": name, "serverId": start["serverId"].(string)})
	if code != 200 {
		t.Fatalf("login %s: %d %v", name, code, out)
	}
	return out["token"].(string), out["profile"].(map[string]any)
}

func TestSignInRequiresMojangVerification(t *testing.T) {
	ts, _ := newTestServer(t)
	_, start := call(t, ts, "POST", "/v1/auth/start", "", nil)
	code, _ := call(t, ts, "POST", "/v1/auth/finish", "", map[string]string{"username": "Impostor", "serverId": start["serverId"].(string)})
	if code != 403 {
		t.Fatalf("unverified sign-in should be refused, got %d", code)
	}
	// A server id can only be used once.
	_, start = call(t, ts, "POST", "/v1/auth/start", "", nil)
	sid := start["serverId"].(string)
	call(t, ts, "POST", "/v1/auth/finish", "", map[string]string{"username": "Alex", "serverId": sid})
	if code, _ := call(t, ts, "POST", "/v1/auth/finish", "", map[string]string{"username": "Alex", "serverId": sid}); code == 200 {
		t.Fatal("reusing a server id must fail")
	}
	if code, _ := call(t, ts, "GET", "/v1/me", "not-a-token", nil); code != 401 {
		t.Fatalf("bad token should be 401, got %d", code)
	}
}

func TestEconomyAndEquip(t *testing.T) {
	ts, _ := newTestServer(t)
	tok, prof := login(t, ts, "Alex")
	if prof["coins"].(float64) != starterCoins || prof["admin"].(bool) {
		t.Fatalf("new player should get starter coins and no admin: %v", prof)
	}
	if code, out := call(t, ts, "POST", "/v1/daily", tok, nil); code != 200 || out["coins"].(float64) != starterCoins+dailyCoins {
		t.Fatalf("daily: %d %v", code, out)
	}
	if code, _ := call(t, ts, "POST", "/v1/daily", tok, nil); code != 400 {
		t.Fatal("second daily claim must be refused")
	}
	if code, _ := call(t, ts, "POST", "/v1/buy", tok, map[string]string{"id": "cape_galaxy"}); code != 400 {
		t.Fatal("buying without enough coins must fail")
	}
	if code, out := call(t, ts, "POST", "/v1/buy", tok, map[string]string{"id": "cape_mono"}); code != 200 || out["coins"].(float64) != 50 {
		t.Fatalf("buy: %d %v", code, out)
	}
	if code, _ := call(t, ts, "POST", "/v1/buy", tok, map[string]string{"id": "cape_admin"}); code == 200 {
		t.Fatal("the staff cape must not be for sale")
	}
	if code, _ := call(t, ts, "POST", "/v1/equip", tok, map[string]string{"type": "cape", "id": "cape_crimson"}); code != 403 {
		t.Fatal("equipping an unowned cosmetic must fail")
	}
	if code, _ := call(t, ts, "POST", "/v1/equip", tok, map[string]string{"type": "cape", "id": "cape_mono"}); code != 200 {
		t.Fatal("equipping an owned cape failed")
	}
	resp, _ := http.Get(ts.URL + "/v1/players?uuids=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa,cccccccccccccccccccccccccccccccc")
	var look map[string]map[string]string
	_ = json.NewDecoder(resp.Body).Decode(&look)
	resp.Body.Close()
	if look["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]["cape"] != "cape_mono" || len(look) != 1 {
		t.Fatalf("lookup: %v", look)
	}
}

func TestAdmin(t *testing.T) {
	ts, _ := newTestServer(t)
	alexTok, _ := login(t, ts, "Alex")
	ownerTok, owner := login(t, ts, "theavgjim_")
	if !owner["admin"].(bool) {
		t.Fatal("theavgjim_ should become admin from the ADMINS list")
	}
	// Non-admins can't use admin endpoints.
	for _, path := range []string{"/v1/admin/coins", "/v1/admin/cosmetic", "/v1/admin/role", "/v1/admin/announce"} {
		if code, _ := call(t, ts, "POST", path, alexTok, map[string]any{"player": "Alex", "amount": 999, "id": "cape_galaxy", "give": true, "admin": true, "message": "hi"}); code != 403 {
			t.Fatalf("%s should be forbidden for non-admins, got %d", path, code)
		}
	}
	if code, out := call(t, ts, "POST", "/v1/admin/coins", ownerTok, map[string]any{"player": "alex", "amount": 500}); code != 200 || out["coins"].(float64) != starterCoins+500 {
		t.Fatalf("give coins: %d %v", code, out)
	}
	if code, _ := call(t, ts, "POST", "/v1/admin/coins", ownerTok, map[string]any{"player": "Nobody", "amount": 5}); code != 404 {
		t.Fatal("unknown player should be 404")
	}
	if code, _ := call(t, ts, "POST", "/v1/admin/cosmetic", ownerTok, map[string]any{"player": "Alex", "id": "cape_galaxy", "give": true}); code != 200 {
		t.Fatal("give cosmetic failed")
	}
	_, me := call(t, ts, "GET", "/v1/me", alexTok, nil)
	if !strings.Contains(strings.Join(toStrings(me["owned"]), ","), "cape_galaxy") {
		t.Fatalf("Alex should own the galaxy cape: %v", me["owned"])
	}
	// Promote Alex, who then can't demote the owner.
	if code, _ := call(t, ts, "POST", "/v1/admin/role", ownerTok, map[string]any{"player": "Alex", "admin": true}); code != 200 {
		t.Fatal("promote failed")
	}
	if code, _ := call(t, ts, "POST", "/v1/admin/role", alexTok, map[string]any{"player": "theavgjim_", "admin": false}); code != 403 {
		t.Fatal("an owner from ADMINS must not be removable")
	}
	if code, _ := call(t, ts, "POST", "/v1/admin/role", alexTok, map[string]any{"player": "Alex", "admin": false}); code != 400 {
		t.Fatal("admins can't remove their own role")
	}
	if code, _ := call(t, ts, "POST", "/v1/admin/announce", ownerTok, map[string]any{"message": "Welcome to Annoying Client!"}); code != 200 {
		t.Fatal("announce failed")
	}
	_, ann := call(t, ts, "GET", "/v1/announcement", "", nil)
	if ann["announcement"].(map[string]any)["message"] != "Welcome to Annoying Client!" {
		t.Fatalf("announcement: %v", ann)
	}
}

func TestPersistence(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "db.json")
	st, _ := openStore(path)
	_ = st.update(func(db *dbFile) error {
		db.Players["x"] = &Player{UUID: "x", Name: "X", Coins: 42}
		return nil
	})
	st2, err := openStore(path)
	if err != nil || st2.db.Players["x"].Coins != 42 {
		t.Fatalf("data not persisted: %v", err)
	}
}

func toStrings(v any) []string {
	var out []string
	if list, ok := v.([]any); ok {
		for _, x := range list {
			out = append(out, x.(string))
		}
	}
	return out
}
