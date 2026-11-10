//go:build !devauth

package main

// Release builds always verify players with Mojang.
func insecureDevAuth() bool { return false }

func devVerify(username, serverID string) (string, string, error) {
	return mojangVerify(username, serverID)
}
