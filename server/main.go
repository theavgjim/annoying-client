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
package main

import (
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

func main() {
	port := envOr("PORT", "8080")
	store, err := openStore(envOr("DATA", "data/cosmetics.json"))
	if err != nil {
		log.Fatalf("cannot open database: %v", err)
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
	log.Fatal(srv.ListenAndServe())
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
