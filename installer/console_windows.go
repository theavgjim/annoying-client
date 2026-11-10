//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

var (
	kernel32           = syscall.NewLazyDLL("kernel32.dll")
	procGetConsoleMode = kernel32.NewProc("GetConsoleMode")
	procSetConsoleMode = kernel32.NewProc("SetConsoleMode")
	procSetTitle       = kernel32.NewProc("SetConsoleTitleW")
)

// enableConsoleColors turns on ANSI escape sequences (Windows 10 and newer).
func enableConsoleColors() {
	const enableVirtualTerminalProcessing = 0x0004
	h, err := syscall.GetStdHandle(syscall.STD_OUTPUT_HANDLE)
	if err != nil {
		return
	}
	var mode uint32
	if r, _, _ := procGetConsoleMode.Call(uintptr(h), uintptr(unsafe.Pointer(&mode))); r == 0 {
		return
	}
	procSetConsoleMode.Call(uintptr(h), uintptr(mode|enableVirtualTerminalProcessing))
}

func setConsoleTitle(title string) {
	p, err := syscall.UTF16PtrFromString(title)
	if err == nil {
		procSetTitle.Call(uintptr(unsafe.Pointer(p)))
	}
}
