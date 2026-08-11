#!/usr/bin/env bash

# Keep this shell as the container supervisor. Folia runs as its child so a reset request can be
# processed only after Java has exited and released the world's session lock.
set -u

marker_path="${BINGO_RESET_MARKER:-$PWD/.championships-bingo-reset}"

if [[ "${1:-}" == "--" ]]; then
    shift
fi
if [[ "$#" -eq 0 ]]; then
    echo "usage: $0 -- <java command and arguments>" >&2
    exit 64
fi

child_pid=""
stop_requested=0

forward_stop() {
    stop_requested=1
    if [[ -n "$child_pid" ]] && kill -0 "$child_pid" 2>/dev/null; then
        kill -TERM "$child_pid" 2>/dev/null || true
    fi
}

trap forward_stop TERM INT HUP

valid_basename() {
    local name="$1"
    [[ -n "$name" && "$name" != "." && "$name" != ".." && "$name" != */* ]]
}

process_reset_marker() {
    local -a fields=()
    mapfile -t fields < "$marker_path" || {
        echo "[BingoReset] Unable to read reset marker: $marker_path" >&2
        return 1
    }
    if [[ "${#fields[@]}" -ne 3 || "${fields[0]}" != "1" ]]; then
        echo "[BingoReset] Invalid reset marker format: $marker_path" >&2
        return 1
    fi

    local world_name="${fields[1]}"
    local retired_name="${fields[2]}"
    if ! valid_basename "$world_name" \
            || ! valid_basename "$retired_name" \
            || [[ "$retired_name" != "$world_name.cc-reset-"* ]]; then
        echo "[BingoReset] Unsafe world names in reset marker" >&2
        return 1
    fi

    local world_path="$PWD/$world_name"
    local retired_path="$PWD/$retired_name"
    if [[ -d "$world_path" && ! -L "$world_path" ]]; then
        if [[ -e "$retired_path" || -L "$retired_path" ]]; then
            echo "[BingoReset] Retired world target already exists: $retired_path" >&2
            return 1
        fi
        mv -- "$world_path" "$retired_path" || return 1
        echo "[BingoReset] Retired $world_name as $retired_name"
    elif [[ ! -e "$world_path" && ! -L "$world_path" \
            && -d "$retired_path" && ! -L "$retired_path" ]]; then
        echo "[BingoReset] Recovered completed world move to $retired_name"
    else
        echo "[BingoReset] World reset paths are not in a safe state" >&2
        return 1
    fi

    rm -- "$marker_path" || return 1
    return 0
}

while true; do
    if [[ -e "$marker_path" || -L "$marker_path" ]]; then
        if [[ ! -f "$marker_path" || -L "$marker_path" ]]; then
            echo "[BingoReset] Reset marker is not a regular file: $marker_path" >&2
            exit 74
        fi
        if ! process_reset_marker; then
            echo "[BingoReset] Refusing to start Folia after an unsafe reset handoff" >&2
            exit 74
        fi
    fi

    echo "[BingoReset] Starting Folia"
    "$@" <&0 &
    child_pid=$!

    child_status=0
    while true; do
        wait "$child_pid"
        child_status=$?
        if ! kill -0 "$child_pid" 2>/dev/null; then
            break
        fi
    done
    child_pid=""

    if [[ "$stop_requested" -eq 1 ]]; then
        exit "$child_status"
    fi
    if [[ ! -e "$marker_path" && ! -L "$marker_path" ]]; then
        exit "$child_status"
    fi
done
