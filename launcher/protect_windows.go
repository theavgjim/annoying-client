//go:build windows

package main

import (
	"errors"
	"syscall"
	"unsafe"
)

// Tokens are encrypted with DPAPI, so only the same Windows user on the same PC can read them.

type dataBlob struct {
	cbData uint32
	pbData *byte
}

var (
	crypt32           = syscall.NewLazyDLL("crypt32.dll")
	kernel32dll       = syscall.NewLazyDLL("kernel32.dll")
	procProtectData   = crypt32.NewProc("CryptProtectData")
	procUnprotectData = crypt32.NewProc("CryptUnprotectData")
	procLocalFree     = kernel32dll.NewProc("LocalFree")
)

func blob(b []byte) *dataBlob {
	if len(b) == 0 {
		return &dataBlob{}
	}
	return &dataBlob{cbData: uint32(len(b)), pbData: &b[0]}
}

func (b *dataBlob) bytes() []byte {
	out := make([]byte, b.cbData)
	copy(out, unsafe.Slice(b.pbData, b.cbData))
	return out
}

func protect(data []byte) ([]byte, error) {
	var out dataBlob
	r, _, err := procProtectData.Call(uintptr(unsafe.Pointer(blob(data))), 0, 0, 0, 0, 0x1 /* UI_FORBIDDEN */, uintptr(unsafe.Pointer(&out)))
	if r == 0 {
		return nil, err
	}
	defer procLocalFree.Call(uintptr(unsafe.Pointer(out.pbData)))
	return out.bytes(), nil
}

func unprotect(data []byte) ([]byte, error) {
	if len(data) == 0 {
		return nil, errors.New("empty")
	}
	var out dataBlob
	r, _, err := procUnprotectData.Call(uintptr(unsafe.Pointer(blob(data))), 0, 0, 0, 0, 0x1, uintptr(unsafe.Pointer(&out)))
	if r == 0 {
		return nil, err
	}
	defer procLocalFree.Call(uintptr(unsafe.Pointer(out.pbData)))
	return out.bytes(), nil
}
