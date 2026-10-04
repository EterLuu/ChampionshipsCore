#!/usr/bin/env bash
set -euo pipefail

CC_FORMAT_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
CC_FORMAT_VERSION=1.33.0
CC_FORMAT_JAR="/tmp/google-java-format-${CC_FORMAT_VERSION}.jar"
if [[ ! -f "$CC_FORMAT_JAR" ]]; then
    curl --fail --location --max-time 60 \
        "https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/${CC_FORMAT_VERSION}/google-java-format-${CC_FORMAT_VERSION}-all-deps.jar" \
        --output "$CC_FORMAT_JAR"
fi

case "${1:-}" in
    "") CC_FORMAT_ACTION=(--replace) ;;
    --check) CC_FORMAT_ACTION=(--dry-run --set-exit-if-changed) ;;
    *) echo 'Usage: tools/format-java.sh [--check]' >&2; exit 2 ;;
esac

cd "$CC_FORMAT_ROOT"
rg --files -0 -g '*.java' -g '!**/target/**' championships-* \
    | python3 -c 'import sys; sys.stdout.buffer.write(b"\0".join(p for p in sys.stdin.buffer.read().split(b"\0") if b"/src/main/java/" in p or b"/src/test/java/" in p) + b"\0")' \
    | xargs -0 -r -n 100 java -jar "$CC_FORMAT_JAR" --aosp "${CC_FORMAT_ACTION[@]}"
