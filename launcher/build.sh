#!/usr/bin/env bash
# Builds release/AnnoyingClient.exe (Windows x64 launcher) from the current mod build.
# Requirements: Go 1.24+, Python 3 with Pillow, and a finished `./gradlew build` in the parent directory.
set -euo pipefail
cd "$(dirname "$0")"
cp ../build/libs/annoying-client-1.2.1+mc1.21.11.jar embedded/annoying-client.jar
go vet ./... && go test ./...
python3 -I tools/make_syso.py web/icon.png rsrc_windows_amd64.syso 1.2.1 "Annoying Client" AnnoyingClient.exe AnnoyingClient.Launcher
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -H windowsgui" -o ../release/AnnoyingClient.exe .
echo "Built ../release/AnnoyingClient.exe"
# Test build for automated end-to-end runs (adds the ANNOYING_TEST_PLAYER offline test account; never ship it):
#   go build -tags devtest -o /tmp/annoying-launcher-test .
