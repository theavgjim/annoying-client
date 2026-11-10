//go:build !windows

package main

// On other systems the account file is stored with owner-only permissions (0600).
func protect(data []byte) ([]byte, error)   { return data, nil }
func unprotect(data []byte) ([]byte, error) { return data, nil }
