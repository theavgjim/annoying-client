//go:build !windows

package main

import "fmt"

func showError(title, message string) { fmt.Println(title + ": " + message) }
