#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
mapfile -t sources < <(find core/src/main/java core/src/test/java -name '*.java' -print)
javac --release 8 -d "$classes" "${sources[@]}"
java -cp "$classes" uk.obskyra.diamonds.core.CoreTest
