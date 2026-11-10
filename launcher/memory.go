package main

// recommendedMemoryMB picks a heap size: enough for a modded client, never more than half the PC's RAM.
func recommendedMemoryMB() int {
	total := totalMemoryMB()
	switch {
	case total >= 32*1024:
		return 8192
	case total >= 16*1024:
		return 6144
	case total >= 12*1024:
		return 4096
	case total >= 8*1024:
		return 3072
	default:
		return max(1024, total/2)
	}
}
