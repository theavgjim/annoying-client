//go:build devauth

package main

import (
	"crypto/md5"
	"encoding/hex"
	"os"
)

// Test builds only (go build -tags devauth) with ANNOYING_DEV_AUTH=1: trusts the username without
// asking Mojang, so automated tests can run offline. Never compiled into release builds.
func insecureDevAuth() bool { return os.Getenv("ANNOYING_DEV_AUTH") == "1" }

func devVerify(username, serverID string) (string, string, error) {
	sum := md5.Sum([]byte("OfflinePlayer:" + username))
	sum[6] = sum[6]&0x0f | 0x30
	sum[8] = sum[8]&0x3f | 0x80
	return hex.EncodeToString(sum[:]), username, nil
}
