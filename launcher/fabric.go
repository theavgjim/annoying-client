package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

const (
	minecraftVersion = "1.21.11"
	loaderVersion    = "0.20.1"
	fabricVersionID  = "fabric-loader-" + loaderVersion + "-" + minecraftVersion
)

// fetchFabricProfile downloads the Fabric Loader launch profile, falling back to a cached copy offline.
func fetchFabricProfile(ctx context.Context, p Paths) (*VersionJSON, []byte, error) {
	path := filepath.Join(p.Versions, fabricVersionID, fabricVersionID+".json")
	url := fmt.Sprintf("https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json", minecraftVersion, loaderVersion)
	data, err := doRequest(ctx, "GET", url, nil, nil)
	if err != nil {
		cached, rerr := os.ReadFile(path)
		if rerr != nil {
			return nil, nil, fmt.Errorf("cannot reach Fabric's servers: %w", err)
		}
		data = cached
	} else if werr := writeFileAtomic(path, data); werr != nil {
		return nil, nil, werr
	}
	var v VersionJSON
	if err := json.Unmarshal(data, &v); err != nil {
		return nil, nil, fmt.Errorf("fabric profile is damaged: %w", err)
	}
	return &v, data, nil
}
