package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

const (
	dailyCoins      = 50
	dailyInterval   = 20 * time.Hour
	starterCoins    = 100
	sessionLifetime = 24 * time.Hour
	maxLookup       = 100
)

var (
	nameRe = regexp.MustCompile(`^[A-Za-z0-9_]{1,16}$`)
	uuidRe = regexp.MustCompile(`^[0-9a-f]{32}$`)
)

type Server struct {
	store       *Store
	admins      map[string]bool // lower-case usernames that become admin on first verified sign-in
	verify      func(username, serverID string) (uuid, name string, err error)
	challengeMu sync.Mutex
	challenges  map[string]time.Time
	limiter     *rateLimiter
}

func (s *Server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/health", func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, map[string]bool{"ok": true}) })
	mux.HandleFunc("GET /v1/catalog", func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, catalog) })
	mux.HandleFunc("POST /v1/auth/start", s.authStart)
	mux.HandleFunc("POST /v1/auth/finish", s.authFinish)
	mux.HandleFunc("GET /v1/players", s.lookup)
	mux.HandleFunc("GET /v1/announcement", func(w http.ResponseWriter, r *http.Request) {
		var a *Announcement
		s.store.view(func(db *dbFile) { a = db.Announcement })
		writeJSON(w, 200, map[string]any{"announcement": a})
	})
	mux.HandleFunc("GET /v1/me", s.auth(s.me))
	mux.HandleFunc("POST /v1/daily", s.auth(s.daily))
	mux.HandleFunc("POST /v1/buy", s.auth(s.buy))
	mux.HandleFunc("POST /v1/equip", s.auth(s.equip))
	mux.HandleFunc("GET /v1/admin/players", s.admin(s.adminPlayers))
	mux.HandleFunc("POST /v1/admin/coins", s.admin(s.adminCoins))
	mux.HandleFunc("POST /v1/admin/cosmetic", s.admin(s.adminCosmetic))
	mux.HandleFunc("POST /v1/admin/role", s.admin(s.adminRole))
	mux.HandleFunc("POST /v1/admin/announce", s.admin(s.adminAnnounce))
	return s.limiter.wrap(mux)
}

// ---- helpers ------------------------------------------------------------------------------

type apiError struct {
	status int
	msg    string
}

func (e apiError) Error() string { return e.msg }

func bad(msg string) error       { return apiError{400, msg} }
func forbidden(msg string) error { return apiError{403, msg} }
func notFound(msg string) error  { return apiError{404, msg} }

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, err error) {
	var ae apiError
	if errors.As(err, &ae) {
		writeJSON(w, ae.status, map[string]string{"error": ae.msg})
		return
	}
	writeJSON(w, 500, map[string]string{"error": "server error"})
}

func readJSON(r *http.Request, v any) error {
	if err := json.NewDecoder(io.LimitReader(r.Body, 64*1024)).Decode(v); err != nil {
		return bad("invalid request body")
	}
	return nil
}

type authed func(w http.ResponseWriter, r *http.Request, p *Player, db *dbFile) (any, error)

// auth resolves the bearer session, runs h under the store lock and saves its changes.
func (s *Server) auth(h authed) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		var out any
		err := s.store.update(func(db *dbFile) error {
			sess := db.Sessions[token]
			if token == "" || sess == nil || time.Now().After(sess.Expires) {
				return apiError{401, "sign in again"}
			}
			p := db.Players[sess.UUID]
			if p == nil {
				return apiError{401, "sign in again"}
			}
			p.LastSeen = time.Now()
			var err error
			out, err = h(w, r, p, db)
			return err
		})
		if err != nil {
			writeErr(w, err)
			return
		}
		writeJSON(w, 200, out)
	}
}

func (s *Server) admin(h authed) http.HandlerFunc {
	return s.auth(func(w http.ResponseWriter, r *http.Request, p *Player, db *dbFile) (any, error) {
		if !p.Admin {
			return nil, forbidden("admins only")
		}
		return h(w, r, p, db)
	})
}

func profileView(p *Player) map[string]any {
	next := p.LastDaily.Add(dailyInterval)
	return map[string]any{
		"uuid": p.UUID, "name": p.Name, "coins": p.Coins, "owned": p.Owned, "equipped": p.Equipped,
		"admin": p.Admin, "dailyReady": time.Now().After(next), "nextDaily": next,
	}
}

// ---- sign-in (Mojang session verification, like a Minecraft server) -----------------------

func (s *Server) authStart(w http.ResponseWriter, r *http.Request) {
	id := randomHex(20)
	s.challengeMu.Lock()
	now := time.Now()
	for k, t := range s.challenges {
		if now.Sub(t) > 2*time.Minute {
			delete(s.challenges, k)
		}
	}
	s.challenges[id] = now
	s.challengeMu.Unlock()
	writeJSON(w, 200, map[string]string{"serverId": id})
}

func (s *Server) authFinish(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Username string `json:"username"`
		ServerID string `json:"serverId"`
	}
	if err := readJSON(r, &in); err != nil {
		writeErr(w, err)
		return
	}
	s.challengeMu.Lock()
	issued, ok := s.challenges[in.ServerID]
	delete(s.challenges, in.ServerID)
	s.challengeMu.Unlock()
	if !ok || time.Since(issued) > 2*time.Minute || !nameRe.MatchString(in.Username) {
		writeErr(w, bad("sign-in expired; try again"))
		return
	}
	uuid, name, err := s.verify(in.Username, in.ServerID)
	if err != nil {
		writeErr(w, forbidden("Minecraft could not confirm your account: "+err.Error()))
		return
	}
	token := randomHex(32)
	var view map[string]any
	err = s.store.update(func(db *dbFile) error {
		p := db.Players[uuid]
		if p == nil {
			p = &Player{UUID: uuid, Coins: starterCoins, Equipped: map[string]string{}, FirstSeen: time.Now()}
			p.give("cape_annoying")
			db.Players[uuid] = p
		}
		p.Name = name
		p.LastSeen = time.Now()
		if s.admins[strings.ToLower(name)] && !p.Admin {
			p.Admin = true
			p.give("cape_admin")
			db.audit(fmt.Sprintf("%s (%s) became admin from the ADMINS list", name, uuid))
		}
		db.Sessions[token] = &session{UUID: uuid, Expires: time.Now().Add(sessionLifetime)}
		view = profileView(p)
		return nil
	})
	if err != nil {
		writeErr(w, err)
		return
	}
	writeJSON(w, 200, map[string]any{"token": token, "profile": view})
}

// mojangVerify asks Mojang whether this player really joined with this server id.
func mojangVerify(username, serverID string) (string, string, error) {
	q := url.Values{"username": {username}, "serverId": {serverID}}
	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Get("https://sessionserver.mojang.com/session/minecraft/hasJoined?" + q.Encode())
	if err != nil {
		return "", "", errors.New("Mojang is unreachable")
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", "", errors.New("not verified")
	}
	var prof struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&prof); err != nil || !uuidRe.MatchString(prof.ID) {
		return "", "", errors.New("unexpected reply from Mojang")
	}
	return prof.ID, prof.Name, nil
}

// ---- player endpoints ---------------------------------------------------------------------

func (s *Server) me(w http.ResponseWriter, r *http.Request, p *Player, db *dbFile) (any, error) {
	return profileView(p), nil
}

func (s *Server) daily(w http.ResponseWriter, r *http.Request, p *Player, db *dbFile) (any, error) {
	if time.Since(p.LastDaily) < dailyInterval {
		return nil, bad("your daily coins are not ready yet")
	}
	p.LastDaily = time.Now()
	p.Coins += dailyCoins
	return profileView(p), nil
}

func (s *Server) buy(w http.ResponseWriter, r *http.Request, p *Player, db *dbFile) (any, error) {
	var in struct {
		ID string `json:"id"`
	}
	if err := readJSON(r, &in); err != nil {
		return nil, err
	}
	c := findCosmetic(in.ID)
	switch {
	case c == nil:
		return nil, notFound("that cosmetic does not exist")
	case p.owns(c.ID):
		return nil, bad("you already own this")
	case c.Price < 0:
		return nil, forbidden("this cosmetic can't be bought")
	case p.Coins < c.Price:
		return nil, bad(fmt.Sprintf("you need %d more coins", c.Price-p.Coins))
	}
	p.Coins -= c.Price
	p.give(c.ID)
	return profileView(p), nil
}

func (s *Server) equip(w http.ResponseWriter, r *http.Request, p *Player, db *dbFile) (any, error) {
	var in struct {
		Type string `json:"type"`
		ID   string `json:"id"` // empty unequips
	}
	if err := readJSON(r, &in); err != nil {
		return nil, err
	}
	if in.Type != "cape" && in.Type != "trail" {
		return nil, bad("unknown cosmetic type")
	}
	if in.ID == "" {
		delete(p.Equipped, in.Type)
		return profileView(p), nil
	}
	c := findCosmetic(in.ID)
	if c == nil || c.Type != in.Type {
		return nil, notFound("that cosmetic does not exist")
	}
	if !p.owns(c.ID) {
		return nil, forbidden("you don't own this yet")
	}
	if p.Equipped == nil {
		p.Equipped = map[string]string{}
	}
	p.Equipped[in.Type] = c.ID
	return profileView(p), nil
}

// lookup returns what other players are wearing (public; used to draw their cosmetics).
func (s *Server) lookup(w http.ResponseWriter, r *http.Request) {
	ids := strings.Split(r.URL.Query().Get("uuids"), ",")
	if len(ids) > maxLookup {
		ids = ids[:maxLookup]
	}
	out := map[string]map[string]string{}
	s.store.view(func(db *dbFile) {
		for _, id := range ids {
			id = strings.ToLower(strings.ReplaceAll(strings.TrimSpace(id), "-", ""))
			if p := db.Players[id]; p != nil && len(p.Equipped) > 0 {
				eq := map[string]string{}
				for k, v := range p.Equipped {
					eq[k] = v
				}
				out[id] = eq
			}
		}
	})
	writeJSON(w, 200, out)
}

// ---- admin endpoints ----------------------------------------------------------------------

func target(db *dbFile, name string) (*Player, error) {
	if !nameRe.MatchString(name) {
		return nil, bad("enter a valid Minecraft username")
	}
	p := db.playerByName(name)
	if p == nil {
		return nil, notFound(name + " has not signed in to Annoying Client yet")
	}
	return p, nil
}

func (s *Server) adminPlayers(w http.ResponseWriter, r *http.Request, admin *Player, db *dbFile) (any, error) {
	list := make([]map[string]any, 0, len(db.Players))
	for _, p := range db.Players {
		list = append(list, map[string]any{"name": p.Name, "coins": p.Coins, "owned": len(p.Owned), "admin": p.Admin, "lastSeen": p.LastSeen})
	}
	return list, nil
}

func (s *Server) adminCoins(w http.ResponseWriter, r *http.Request, admin *Player, db *dbFile) (any, error) {
	var in struct {
		Player string `json:"player"`
		Amount int    `json:"amount"` // negative removes
	}
	if err := readJSON(r, &in); err != nil {
		return nil, err
	}
	if in.Amount == 0 || in.Amount > 1_000_000 || in.Amount < -1_000_000 {
		return nil, bad("amount must be between -1,000,000 and 1,000,000 (not 0)")
	}
	p, err := target(db, in.Player)
	if err != nil {
		return nil, err
	}
	p.Coins = max(0, p.Coins+in.Amount)
	db.audit(fmt.Sprintf("%s changed %s's coins by %d (now %d)", admin.Name, p.Name, in.Amount, p.Coins))
	return map[string]any{"player": p.Name, "coins": p.Coins}, nil
}

func (s *Server) adminCosmetic(w http.ResponseWriter, r *http.Request, admin *Player, db *dbFile) (any, error) {
	var in struct {
		Player string `json:"player"`
		ID     string `json:"id"`
		Give   bool   `json:"give"`
	}
	if err := readJSON(r, &in); err != nil {
		return nil, err
	}
	c := findCosmetic(in.ID)
	if c == nil {
		return nil, notFound("that cosmetic does not exist")
	}
	p, err := target(db, in.Player)
	if err != nil {
		return nil, err
	}
	if in.Give {
		p.give(c.ID)
	} else {
		p.take(c.ID)
	}
	db.audit(fmt.Sprintf("%s %s %s %s", admin.Name, map[bool]string{true: "gave", false: "removed"}[in.Give], c.ID, p.Name))
	return map[string]any{"player": p.Name, "owned": p.Owned}, nil
}

func (s *Server) adminRole(w http.ResponseWriter, r *http.Request, admin *Player, db *dbFile) (any, error) {
	var in struct {
		Player string `json:"player"`
		Admin  bool   `json:"admin"`
	}
	if err := readJSON(r, &in); err != nil {
		return nil, err
	}
	p, err := target(db, in.Player)
	if err != nil {
		return nil, err
	}
	if !in.Admin && s.admins[strings.ToLower(p.Name)] {
		return nil, forbidden(p.Name + " is an owner from the server's ADMINS list and can't be removed here")
	}
	if !in.Admin && p.UUID == admin.UUID {
		return nil, bad("you can't remove your own admin role")
	}
	p.Admin = in.Admin
	if in.Admin {
		p.give("cape_admin")
	}
	db.audit(fmt.Sprintf("%s set admin=%v for %s", admin.Name, in.Admin, p.Name))
	return map[string]any{"player": p.Name, "admin": p.Admin}, nil
}

func (s *Server) adminAnnounce(w http.ResponseWriter, r *http.Request, admin *Player, db *dbFile) (any, error) {
	var in struct {
		Message string `json:"message"`
	}
	if err := readJSON(r, &in); err != nil {
		return nil, err
	}
	msg := strings.TrimSpace(in.Message)
	if msg == "" || len(msg) > 200 {
		return nil, bad("announcements must be 1 to 200 characters")
	}
	id := 1
	if db.Announcement != nil {
		id = db.Announcement.ID + 1
	}
	db.Announcement = &Announcement{ID: id, Message: msg, By: admin.Name, At: time.Now()}
	db.audit(fmt.Sprintf("%s announced: %s", admin.Name, msg))
	return db.Announcement, nil
}

// ---- rate limiting ------------------------------------------------------------------------

type rateLimiter struct {
	mu      sync.Mutex
	buckets map[string]*bucket
	rate    float64 // tokens per second
	burst   float64
}

type bucket struct {
	tokens float64
	last   time.Time
}

func newRateLimiter(perMinute, burst int) *rateLimiter {
	return &rateLimiter{buckets: map[string]*bucket{}, rate: float64(perMinute) / 60, burst: float64(burst)}
}

func (l *rateLimiter) allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	b := l.buckets[key]
	if b == nil {
		b = &bucket{tokens: l.burst, last: now}
		l.buckets[key] = b
		if len(l.buckets) > 50000 {
			l.buckets = map[string]*bucket{key: b}
		}
	}
	b.tokens = min(l.burst, b.tokens+now.Sub(b.last).Seconds()*l.rate)
	b.last = now
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}

func (l *rateLimiter) wrap(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			ip = r.RemoteAddr
		}
		if !l.allow(ip) {
			writeJSON(w, 429, map[string]string{"error": "too many requests; slow down"})
			return
		}
		h.ServeHTTP(w, r)
	})
}
