#!/usr/bin/env bash
# Builds release/AnnoyingClient-Installer.exe (Windows x64) from the current mod build.
# Requirements: Go 1.24+, Python 3 with Pillow, and a finished `./gradlew build` in the parent directory.
set -euo pipefail
cd "$(dirname "$0")"
VERSION=1.2.2
JAR=../build/libs/annoying-client-${VERSION}+mc1.21.11.jar
FABRIC_API=fabric-api-0.141.6+1.21.11.jar
mkdir -p payload ../release
cp "$JAR" payload/
cp ../src/main/resources/assets/annoyingclient/icon.png payload/icon.png
if [ ! -f "payload/$FABRIC_API" ]; then
  URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.141.6+1.21.11/$FABRIC_API"
  curl -fsSL -o "payload/$FABRIC_API" "$URL"
  echo "$(curl -fsSL "$URL.sha1")  payload/$FABRIC_API" | sha1sum -c -
fi
curl -fsSL -o payload/fabric-loader-profile.json https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.20.1/profile/json
python3 -I tools/make_syso.py payload/icon-256.png rsrc_windows_amd64.syso "$VERSION"
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o ../release/AnnoyingClient-Installer.exe .
echo "Built ../release/AnnoyingClient-Installer.exe"
