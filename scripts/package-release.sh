#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?usage: package-release.sh <version>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
JAR="$ROOT/nyx-loader/target/nyx-loader-$VERSION.jar"
STAGE="$DIST/stage"

[ -f "$JAR" ] || { echo "missing $JAR (run mvn package first)" >&2; exit 1; }

rm -rf "$DIST"
mkdir -p "$STAGE/versions/Nyx-1.0" "$STAGE/libraries/dev/nyx/nyx-loader/$VERSION"

SHA1="$(sha1sum "$JAR" | cut -d' ' -f1)"
SIZE="$(stat -c%s "$JAR")"

sed -e "s/@VERSION@/$VERSION/g" -e "s/@SHA1@/$SHA1/g" -e "s/@SIZE@/$SIZE/g" \
    "$ROOT/install/Nyx-1.0.json.template" > "$STAGE/versions/Nyx-1.0/Nyx-1.0.json"
cp "$JAR" "$STAGE/libraries/dev/nyx/nyx-loader/$VERSION/"

mkdir -p "$STAGE/mods-examples"
cp "$ROOT/examples/hello-nyx/target/hello-nyx-$VERSION.jar" "$STAGE/mods-examples/"
cp "$ROOT/examples/session-timer/target/session-timer-$VERSION.jar" "$STAGE/mods-examples/"
cp "$ROOT/examples/log-probe/target/log-probe-$VERSION.jar" "$STAGE/mods-examples/"

(cd "$STAGE" && zip -qr "../Nyx-$VERSION-install.zip" versions libraries mods-examples)

cp "$JAR" "$DIST/"
cp "$STAGE/versions/Nyx-1.0/Nyx-1.0.json" "$DIST/"
cp "$ROOT/examples/hello-nyx/target/hello-nyx-$VERSION.jar" "$DIST/"
cp "$ROOT/examples/session-timer/target/session-timer-$VERSION.jar" "$DIST/"
rm -rf "$STAGE"

ls -la "$DIST"
