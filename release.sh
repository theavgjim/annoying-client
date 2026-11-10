#!/usr/bin/env bash
# Builds a full Annoying Client release and publishes it to the launcher's auto-updater.
#
#   ./release.sh 1.2.0 "What changed, in one or two sentences."
#
# 1. Sets the new version everywhere (mod, launcher, installer, website).
# 2. Builds the mod jar, AnnoyingClient.exe and AnnoyingClient-Installer.exe (runs the unit tests).
# 3. Writes release/AnnoyingClient-<version>-Windows.zip and SHA256SUMS.txt.
# 4. Copies the zip and the exe into docs/download/ and writes docs/update.json.
# After committing and pushing to main, every running launcher sees "Update available" within 6 hours
# (or right away via Settings > Check for updates).
set -euo pipefail
cd "$(dirname "$0")"

NEW="${1:?usage: ./release.sh <version> \"<release notes>\"}"
NOTES="${2:?usage: ./release.sh <version> \"<release notes>\"}"
OLD="$(sed -n 's/^mod_version=//p' gradle.properties)"
REPO_RAW="https://raw.githubusercontent.com/theavgjim/annoying-client/main"

if [ "$OLD" != "$NEW" ]; then
  echo "Version $OLD -> $NEW"
  python3 -I - "$OLD" "$NEW" <<'PY'
import sys
old, new = sys.argv[1], sys.argv[2]
edits = {
    "gradle.properties": [f"mod_version={old}"],
    "launcher/main.go": [f'var launcherVersion = "{old}"'],
    "launcher/mods.go": [f"annoying-client-{old}+"],
    "launcher/build.sh": [f"annoying-client-{old}+", f"rsrc_windows_amd64.syso {old} "],
    "installer/main.go": [f'clientVersion    = "{old}"', f"annoying-client-{old}+"],
    "installer/build.sh": [f"VERSION={old}"],
    "docs/index.html": [f"<b>v{old}</b>", f"AnnoyingClient-{old}-Windows.zip", f"annoying-client-{old}+", f"Annoying Client {old} &middot;"],
}
for path, needles in edits.items():
    s = open(path).read()
    for n in needles:
        if n not in s:
            sys.exit(f"{path}: expected to find {n!r}")
        s = s.replace(n, n.replace(old, new))
    open(path, "w").write(s)
PY
fi

JAR="annoying-client-${NEW}+mc1.21.11.jar"
./gradlew --no-daemon -q build -x downloadAssets
mkdir -p release
rm -f release/*.zip release/annoying-client-*.jar
cp "build/libs/$JAR" release/
launcher/build.sh
installer/build.sh

cat > release/README.txt <<EOF
ANNOYING CLIENT ${NEW} (Minecraft 1.21.11)

1. Extract this zip (right-click > Extract All).
2. Double-click AnnoyingClient.exe.
   If Windows says "Windows protected your PC": click "More info" > "Run anyway".
3. Optional: "+ New profile" > "1.21.11 optimized" installs 50 performance and quality mods.
4. Press PLAY. The first start downloads Minecraft, Java and the client.
5. In game: Right Shift opens the ClickGUI (Mod Configs, Wardrobe, Themes and Client tabs).

The launcher updates itself: when a new version is out it shows "Update available" on the Play page.

Other files:
- AnnoyingClient-Installer.exe : only adds Annoying Client to the official Minecraft Launcher.
- ${JAR} : the mod itself, for manual Fabric installs (needs Fabric API).
EOF
sed -i 's/$/\r/' release/README.txt   # Windows line endings for Notepad

ZIP="AnnoyingClient-${NEW}-Windows.zip"
(cd release && zip -q -9 "$ZIP" AnnoyingClient.exe AnnoyingClient-Installer.exe "$JAR" README.txt \
  && sha256sum "$ZIP" AnnoyingClient.exe AnnoyingClient-Installer.exe "$JAR" > SHA256SUMS.txt)

mkdir -p docs/download
cp "release/$ZIP" docs/download/AnnoyingClient-Windows.zip
cp release/AnnoyingClient.exe docs/download/AnnoyingClient.exe

SIZE_MB="$(python3 -c "import os,sys;print(f'{os.path.getsize(sys.argv[1])/1048576:.1f} MB')" "release/$ZIP")"
python3 -I - "$SIZE_MB" <<'PY'
import re, sys
p = "docs/index.html"
s = open(p).read()
s, n = re.subn(r'(-Windows\.zip</b></span><span>)[0-9.]+ MB(</span>)', lambda m: m.group(1) + sys.argv[1] + m.group(2), s)
if n != 1:
    sys.exit("docs/index.html: download size not found")
open(p, "w").write(s)
PY

python3 -I - "$NEW" "$NOTES" "$REPO_RAW/docs/download/AnnoyingClient.exe" release/AnnoyingClient.exe <<'PY'
import hashlib, json, os, sys, datetime
version, notes, url, exe = sys.argv[1:5]
data = open(exe, "rb").read()
manifest = {
    "version": version,
    "notes": notes,
    "published": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "windows": {"url": url, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)},
}
open("docs/update.json", "w").write(json.dumps(manifest, indent=2) + "\n")
PY

echo
echo "Release $NEW is ready:"
cat release/SHA256SUMS.txt
echo
echo "Now commit and push to main:  git add -A && git commit -m \"Release $NEW\" && git push"
