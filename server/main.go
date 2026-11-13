// Annoying Client cosmetics server: stores coins, owned and equipped cosmetics and admin roles.
//
// Players sign in the way a Minecraft server checks them: the client asks Mojang to "join" a random
// server id with its access token, and this server asks Mojang (hasJoined) who joined. Nobody can
// claim another player's account, including an admin's.
//
// Configuration (environment variables):
//
//	PORT    listen port (default 8080)
//	DATA    database file (default ./data/cosmetics.json)
//	ADMINS  comma-separated Minecraft usernames that are owners (e.g. "theavgjim_")
//	GITHUB_TOKEN  GitHub token with only the "gist" scope: backs the database up to a secret gist, so it
//	        survives hosts that wipe the disk on restart (Render's free plan)
//	GIST_ID optional; the gist to use (found automatically by its description otherwise)
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"
)

func main() {
	port := envOr("PORT", "8080")
	var backup Backup
	if token := os.Getenv("GITHUB_TOKEN"); token != "" {
		gb := newGistBackup(token, os.Getenv("GIST_ID"))
		if api := os.Getenv("GITHUB_API_URL"); api != "" { // tests and GitHub Enterprise
			gb.api = strings.TrimRight(api, "/")
		}
		backup = gb
	} else {
		log.Printf("WARNING: GITHUB_TOKEN is not set; on hosts without a permanent disk, coins and cosmetics reset when the server restarts")
	}
	store, err := openStoreWithBackup(envOr("DATA", "data/cosmetics.json"), backup)
	if err != nil {
		log.Fatalf("cannot open database: %v", err)
	}
	if err := store.update(func(*dbFile) error { return nil }); err != nil {
		log.Printf("WARNING: cannot save the database (%v); sign-ins will fail until DATA points to a writable folder", err)
	}
	admins := map[string]bool{}
	for _, a := range strings.Split(envOr("ADMINS", "theavgjim_"), ",") {
		if a = strings.ToLower(strings.TrimSpace(a)); a != "" {
			admins[a] = true
		}
	}
	s := &Server{store: store, admins: admins, verify: mojangVerify, challenges: map[string]time.Time{}, limiter: newRateLimiter(240, 60)}
	if insecureDevAuth() {
		log.Printf("WARNING: development sign-in is enabled; anyone can claim any username. Never use this in production.")
		s.verify = devVerify
	}
	srv := &http.Server{Addr: ":" + port, Handler: s.routes(), ReadHeaderTimeout: 10 * time.Second, ReadTimeout: 20 * time.Second, WriteTimeout: 20 * time.Second}
	log.Printf("Annoying Client cosmetics server on :%s (owners: %s)", port, strings.Join(keys(admins), ", "))
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	go store.RunBackups(ctx)
	go func() {
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal(err)
		}
	}()
	<-ctx.Done()
	// Hosts stop sleeping servers with SIGTERM: finish requests, then upload the last changes.
	log.Printf("shutting down")
	shutdown, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdown)
	if err := store.Flush(shutdown); err != nil {
		log.Printf("final backup failed: %v", err)
	}
}

func envOr(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func keys(m map[string]bool) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	return out
}
