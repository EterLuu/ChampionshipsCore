#!/usr/bin/env bash
set -euo pipefail

CC_FORMAT_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
CC_FORMAT_VERSION=1.33.0
CC_FORMAT_SHA256=697707af07c7753f29cba415c6a76b7882702ff464f807da98b28069b8751910
CC_FORMAT_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/championships-format"
mkdir -p "$CC_FORMAT_CACHE"
CC_FORMAT_JAR="$CC_FORMAT_CACHE/google-java-format-${CC_FORMAT_VERSION}.jar"
verify_formatter() {
    printf '%s  %s\n' "$CC_FORMAT_SHA256" "$1" | sha256sum --check --status
}
if [[ ! -f "$CC_FORMAT_JAR" ]] || ! verify_formatter "$CC_FORMAT_JAR"; then
    CC_FORMAT_DOWNLOAD="$(mktemp "$CC_FORMAT_CACHE/download.XXXXXXXX")"
    trap 'rm -f "$CC_FORMAT_DOWNLOAD"' EXIT
    curl --fail --location --max-time 60 \
        "https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/${CC_FORMAT_VERSION}/google-java-format-${CC_FORMAT_VERSION}-all-deps.jar" \
        --output "$CC_FORMAT_DOWNLOAD"
    verify_formatter "$CC_FORMAT_DOWNLOAD" || { echo "Formatter checksum mismatch" >&2; exit 1; }
    mv "$CC_FORMAT_DOWNLOAD" "$CC_FORMAT_JAR"
fi

case "${1:-}" in
    "") CC_FORMAT_ACTION=(--replace) ;;
    --check) CC_FORMAT_ACTION=(--dry-run --set-exit-if-changed) ;;
    *) echo 'Usage: scripts/format-java.sh [--check]' >&2; exit 2 ;;
esac

cd "$CC_FORMAT_ROOT"
git ls-files -z --cached --others --exclude-standard -- 'championships-*/src/main/java/*.java' 'championships-*/src/test/java/*.java' \
    | python3 -c 'import os, sys; sys.stdout.buffer.write(b"\0".join(p for p in sys.stdin.buffer.read().split(b"\0") if p and os.path.isfile(p) and (b"/src/main/java/" in p or b"/src/test/java/" in p)) + b"\0")' \
    | xargs -0 -r -n 100 java -jar "$CC_FORMAT_JAR" --aosp "${CC_FORMAT_ACTION[@]}"
