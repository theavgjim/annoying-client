package main

import (
	"context"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/hex"
	"errors"
	"fmt"
	"hash"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// DownloadJob is one file to fetch and verify.
type DownloadJob struct {
	URL    string
	Path   string
	SHA1   string // hex; verified when set
	SHA512 string // hex; verified when set (Modrinth)
	SHA256 string // hex; verified when set (launcher updates)
	Size   int64  // 0 when unknown
	Exec   bool   // mark executable (Java runtime)
}

// Progress is reported while a batch downloads.
type Progress struct {
	Stage      string  `json:"stage"`
	Done       int     `json:"done"`
	Total      int     `json:"total"`
	Bytes      int64   `json:"bytes"`
	TotalBytes int64   `json:"totalBytes"`
	Fraction   float64 `json:"fraction"`
}

// fileValid reports whether path already exists with the expected size and hash.
func fileValid(j DownloadJob) bool {
	st, err := os.Stat(j.Path)
	if err != nil || st.IsDir() {
		return false
	}
	if j.Size > 0 && st.Size() != j.Size {
		return false
	}
	if j.SHA1 == "" && j.SHA512 == "" && j.SHA256 == "" {
		return j.Size > 0 || st.Size() > 0
	}
	f, err := os.Open(j.Path)
	if err != nil {
		return false
	}
	defer f.Close()
	var h hash.Hash
	want := j.SHA1
	switch {
	case j.SHA1 != "":
		h = sha1.New()
	case j.SHA256 != "":
		h, want = sha256.New(), j.SHA256
	default:
		h, want = sha512.New(), j.SHA512
	}
	if _, err := io.Copy(h, f); err != nil {
		return false
	}
	return strings.EqualFold(hex.EncodeToString(h.Sum(nil)), want)
}

func downloadOne(ctx context.Context, j DownloadJob, onBytes func(int64)) error {
	if err := os.MkdirAll(filepath.Dir(j.Path), 0o755); err != nil {
		return err
	}
	var lastErr error
	for attempt := 0; attempt < 4; attempt++ {
		if attempt > 0 {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(time.Duration(attempt*attempt) * 500 * time.Millisecond):
			}
		}
		lastErr = func() error {
			req, err := http.NewRequestWithContext(ctx, http.MethodGet, j.URL, nil)
			if err != nil {
				return err
			}
			req.Header.Set("User-Agent", userAgent)
			client := &http.Client{Timeout: 10 * time.Minute}
			resp, err := client.Do(req)
			if err != nil {
				return err
			}
			defer resp.Body.Close()
			if resp.StatusCode != http.StatusOK {
				return fmt.Errorf("HTTP %d for %s", resp.StatusCode, j.URL)
			}
			tmp := j.Path + ".part"
			out, err := os.Create(tmp)
			if err != nil {
				return err
			}
			var h1, h256, h512 hash.Hash = sha1.New(), sha256.New(), sha512.New()
			w := io.MultiWriter(out, h1, h256, h512)
			buf := make([]byte, 64*1024)
			var written int64
			for {
				n, rerr := resp.Body.Read(buf)
				if n > 0 {
					if _, werr := w.Write(buf[:n]); werr != nil {
						out.Close()
						return werr
					}
					written += int64(n)
					onBytes(int64(n))
				}
				if rerr == io.EOF {
					break
				}
				if rerr != nil {
					out.Close()
					onBytes(-written)
					return rerr
				}
			}
			if err := out.Close(); err != nil {
				return err
			}
			if j.Size > 0 && written != j.Size {
				onBytes(-written)
				os.Remove(tmp)
				return fmt.Errorf("size mismatch for %s: got %d, want %d", j.URL, written, j.Size)
			}
			if j.SHA1 != "" && !strings.EqualFold(hex.EncodeToString(h1.Sum(nil)), j.SHA1) {
				onBytes(-written)
				os.Remove(tmp)
				return fmt.Errorf("checksum mismatch for %s", j.URL)
			}
			if j.SHA256 != "" && !strings.EqualFold(hex.EncodeToString(h256.Sum(nil)), j.SHA256) {
				onBytes(-written)
				os.Remove(tmp)
				return fmt.Errorf("checksum mismatch for %s", j.URL)
			}
			if j.SHA512 != "" && !strings.EqualFold(hex.EncodeToString(h512.Sum(nil)), j.SHA512) {
				onBytes(-written)
				os.Remove(tmp)
				return fmt.Errorf("checksum mismatch for %s", j.URL)
			}
			mode := os.FileMode(0o644)
			if j.Exec {
				mode = 0o755
			}
			_ = os.Chmod(tmp, mode)
			return os.Rename(tmp, j.Path)
		}()
		if lastErr == nil || errors.Is(lastErr, context.Canceled) {
			return lastErr
		}
	}
	return lastErr
}

// downloadAll fetches every job that is missing or damaged, using `threads` parallel workers.
func downloadAll(ctx context.Context, stage string, jobs []DownloadJob, threads int, report func(Progress)) error {
	var pending []DownloadJob
	var totalBytes int64
	for _, j := range jobs {
		if !fileValid(j) {
			pending = append(pending, j)
			totalBytes += j.Size
		}
	}
	if len(pending) == 0 {
		return nil
	}
	logf("%s: %d file(s) to download", stage, len(pending))
	var done, bytes atomic.Int64
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	stop := make(chan struct{})
	go func() {
		t := time.NewTicker(150 * time.Millisecond)
		defer t.Stop()
		for {
			select {
			case <-stop:
				return
			case <-t.C:
				p := Progress{Stage: stage, Done: int(done.Load()), Total: len(pending), Bytes: bytes.Load(), TotalBytes: totalBytes}
				if totalBytes > 0 {
					p.Fraction = float64(p.Bytes) / float64(totalBytes)
				} else {
					p.Fraction = float64(p.Done) / float64(p.Total)
				}
				report(p)
			}
		}
	}()
	defer close(stop)

	queue := make(chan DownloadJob)
	var wg sync.WaitGroup
	var firstErr error
	var errOnce sync.Once
	for i := 0; i < max(1, threads); i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := range queue {
				if err := downloadOne(ctx, j, func(n int64) { bytes.Add(n) }); err != nil {
					errOnce.Do(func() { firstErr = err; cancel() })
					return
				}
				done.Add(1)
			}
		}()
	}
	for _, j := range pending {
		select {
		case queue <- j:
		case <-ctx.Done():
		}
		if ctx.Err() != nil {
			break
		}
	}
	close(queue)
	wg.Wait()
	if firstErr != nil {
		return firstErr
	}
	report(Progress{Stage: stage, Done: len(pending), Total: len(pending), Bytes: totalBytes, TotalBytes: totalBytes, Fraction: 1})
	return ctx.Err()
}
