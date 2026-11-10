//go:build devtest

package main

import "os"

// Test builds only (go build -tags devtest): lets automated tests start the game without a
// Microsoft account by setting ANNOYING_TEST_PLAYER. Never compiled into release builds.
func devAccount() (LaunchAccount, bool) {
	name := os.Getenv("ANNOYING_TEST_PLAYER")
	if name == "" {
		return LaunchAccount{}, false
	}
	return LaunchAccount{Name: name, UUID: "00000000000040008000000000000000", AccessToken: "0", UserType: "legacy"}, true
}
