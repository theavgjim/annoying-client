//go:build !devtest

package main

// Release builds have no offline account: direct launches always use a signed-in Microsoft account.
func devAccount() (LaunchAccount, bool) { return LaunchAccount{}, false }
