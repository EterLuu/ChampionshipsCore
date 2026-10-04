#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
simulation_build=$(mktemp -d /tmp/riptide-dodge-sim.XXXXXX)
trap 'rm -rf "$simulation_build"' EXIT
javac -d "$simulation_build" \
  "$repo_root/championships-core/src/main/java/ink/ziip/championshipscore/api/game/riptiderush/mechanics/RiptideDodgeSchedule.java" \
  "$repo_root/tools/riptide-dodge-sim/DodgeSimulation.java"
java -Xms64m -Xmx512m -cp "$simulation_build" DodgeSimulation "$@"
