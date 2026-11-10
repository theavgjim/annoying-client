package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const userAgent = "AnnoyingClient-Launcher/" + launcherVersion

var httpClient = &http.Client{Timeout: 60 * time.Second}

// HTTPError carries the status and body of a failed request.
type HTTPError struct {
	Status int
	URL    string
	Body   string
}

func (e *HTTPError) Error() string {
	body := e.Body
	if len(body) > 300 {
		body = body[:300] + "..."
	}
	return fmt.Sprintf("%s returned HTTP %d: %s", e.URL, e.Status, body)
}

func doRequest(ctx context.Context, method, url string, body io.Reader, headers map[string]string) ([]byte, error) {
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(time.Duration(attempt) * time.Second):
			}
		}
		var reader io.Reader
		if body != nil {
			if seeker, ok := body.(io.Seeker); ok {
				_, _ = seeker.Seek(0, io.SeekStart)
			}
			reader = body
		}
		req, err := http.NewRequestWithContext(ctx, method, url, reader)
		if err != nil {
			return nil, err
		}
		req.Header.Set("User-Agent", userAgent)
		for k, v := range headers {
			req.Header.Set(k, v)
		}
		resp, err := httpClient.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		data, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			lastErr = err
			continue
		}
		if resp.StatusCode >= 500 || resp.StatusCode == 429 {
			lastErr = &HTTPError{resp.StatusCode, url, string(data)}
			continue
		}
		if resp.StatusCode >= 400 {
			return data, &HTTPError{resp.StatusCode, url, string(data)}
		}
		return data, nil
	}
	return nil, lastErr
}

func getJSON(ctx context.Context, url string, out any) error {
	data, err := doRequest(ctx, http.MethodGet, url, nil, map[string]string{"Accept": "application/json"})
	if err != nil {
		return err
	}
	return json.Unmarshal(data, out)
}

func postJSON(ctx context.Context, url string, in, out any, headers map[string]string) error {
	payload, err := json.Marshal(in)
	if err != nil {
		return err
	}
	h := map[string]string{"Content-Type": "application/json", "Accept": "application/json"}
	for k, v := range headers {
		h[k] = v
	}
	data, err := doRequest(ctx, http.MethodPost, url, bytes.NewReader(payload), h)
	if err != nil {
		return err
	}
	if out == nil {
		return nil
	}
	return json.Unmarshal(data, out)
}
