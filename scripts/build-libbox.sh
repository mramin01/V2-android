#!/usr/bin/env bash
# Build the sing-box libbox AAR and drop it into app/libs/libbox.aar.
#
# WHY THIS EXISTS: libbox is NOT published on Maven Central. The only
# supported way to get it is to build it from the sing-box source tree with
# gomobile (this is exactly what SagerNet/sing-box-for-android does:
# `make lib_android` runs `go run ./cmd/internal/build_libbox -target android`).
# The resulting all-ABI AAR is ~93 MB (24 MB for a single ABI).
#
# REQUIREMENTS: Go (>= 1.23), Android SDK + NDK 28, gomobile (fork used by
# sing-box — the build script installs it). This takes several minutes and
# needs ~4 GB free. Do NOT run this in the plain GitHub Actions CI job;
# build the AAR once on a dev machine and commit/copy it to app/libs/.
#
# USAGE:
#   SING_BOX_VERSION=v1.11.10 ./scripts/build-libbox.sh
#   # then rebuild the app — Gradle picks up app/libs/libbox.aar
#   # automatically (see app/build.gradle.kts) and compiles in
#   # src/libbox/.../LibboxEngine.kt.
#
# After the first build with the AAR present, expect a fix-up pass in
# LibboxEngine.kt only: the libbox API surface moves between releases
# (e.g. the command-server API changed in 1.13). The UI, VpnService and
# config generation do not change.
set -euo pipefail

SING_BOX_VERSION="${SING_BOX_VERSION:-v1.11.10}"
ANDROID_API="${ANDROID_API:-24}"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point at the Android NDK}"

echo "Cloning sing-box $SING_BOX_VERSION ..."
git clone --depth 1 --branch "$SING_BOX_VERSION" https://github.com/SagerNet/sing-box.git "$WORKDIR/sing-box"

cd "$WORKDIR/sing-box"
echo "Installing gomobile + building libbox (android api $ANDROID_API) ..."
go run ./cmd/internal/build_libbox -target android -androidapi "$ANDROID_API"

AAR="$WORKDIR/sing-box/libbox.aar"
if [ ! -f "$AAR" ]; then
  echo "ERROR: libbox.aar was not produced. Check the gomobile output above." >&2
  exit 1
fi

mkdir -p "$(dirname "$0")/../app/libs"
cp "$AAR" "$(dirname "$0")/../app/libs/libbox.aar"
ls -lh "$(dirname "$0")/../app/libs/libbox.aar"
echo "Done. Rebuild the app; the libbox source set is now active."
