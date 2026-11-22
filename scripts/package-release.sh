#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?usage: package-release.sh <version>}"
MC="${2:-1.7.10}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
JAR="$ROOT/nyx-loader/target/nyx-loader-$VERSION.jar"
STAGE="$DIST/stage"
NAME="Nyx-$MC"

[ -f "$JAR" ] || { echo "missing $JAR (run mvn package first)" >&2; exit 1; }

rm -rf "$DIST"
mkdir -p "$STAGE/versions/$NAME/mods" "$STAGE/libraries/dev/nyx/nyx-loader/$VERSION" "$DIST/examples"

python3 "$ROOT/scripts/make_fcl_json.py" --mc "$MC" --nyx-version "$VERSION" --jar "$JAR" --forge \
    --out "$STAGE/versions/$NAME/$NAME.json"
python3 "$ROOT/scripts/make_fcl_json.py" --mc "$MC" --nyx-version "$VERSION" --jar "$JAR" \
    --out "$DIST/$NAME-fabric-only.json"
cp "$JAR" "$STAGE/libraries/dev/nyx/nyx-loader/$VERSION/"

(cd "$STAGE" && zip -qr "../Nyx-$VERSION-install.zip" versions libraries)

cp "$JAR" "$DIST/"
cp "$STAGE/versions/$NAME/$NAME.json" "$DIST/"
for example in hello-nyx session-timer log-probe fps-counter fabric-example; do
    cp "$ROOT/examples/$example/target/$example-$VERSION.jar" "$DIST/examples/"
done
rm -rf "$STAGE"

ls -la "$DIST" "$DIST/examples"
