#!/usr/bin/env bash
set -Eeuo pipefail

# Rotate the credentials that are owned by the Championships production login
# path.  This script changes files only.  It deliberately does not restart
# MCSManager-managed servers; restart all affected services in one maintenance
# window after --apply has completed.

umask 077

SCRIPT_NAME="$(basename "$0")"
ACTION="dry-run"
ENABLE_RCON=0
RECREATE_WEB=0

CORE_DIR="${CC_ROTATE_CORE_DIR:-/home/minecraft/minecraft/cc-core}"
BINGO_DIR="${CC_ROTATE_BINGO_DIR:-/home/minecraft/minecraft/cc-bingo}"
BUNGEE_DIR="${CC_ROTATE_BUNGEE_DIR:-/home/minecraft/minecraft/cc-bungeecord}"
WEB_DIR="${CC_ROTATE_WEB_DIR:-/home/minecraft/minecraft/cc-web}"
SECRETS_FILE="${CC_ROTATE_SECRETS_FILE:-/data/cc-production-secrets.env}"
BOT_SECRETS_FILE="${CC_ROTATE_BOT_SECRETS_FILE:-/data/cc-bot-secrets.env}"
BOT_ENV_FILE="${CC_ROTATE_BOT_ENV_FILE:-/data/cc-bot-harness.env}"
BACKUP_ROOT="${CC_ROTATE_BACKUP_ROOT:-/data/cc-production-secret-rotations}"

die() {
    printf '%s: %s\n' "$SCRIPT_NAME" "$*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Usage: scripts/rotate-production-secrets.sh [--dry-run|--apply] [--enable-rcon] [--recreate-web]

Rotate and write the production credentials used by cc-web, cc-core,
cc-bungeecord and cc-bingo.

  --dry-run       Validate paths and show the files that would change (default)
  --apply         Generate new credentials and atomically update the files
  --enable-rcon   Also enable Core RCON and generate a new Core RCON password
  --recreate-web  Recreate cc-web after writing .env so Docker reloads it
  -h, --help      Show this help

The script never restarts Minecraft services.  --recreate-web only recreates
the Docker Compose Web container; a plain `docker compose restart web` does not
reload .env.  After --apply, restart cc-bungeecord, cc-core and cc-bingo
through MCSManager in one maintenance window.  RCON is left disabled unless
--enable-rcon is supplied.

Override paths with CC_ROTATE_* environment variables.  The generated master
file defaults to /data/cc-production-secrets.env and is mode 0600.
EOF
}

while (($#)); do
    case "$1" in
        --dry-run) ACTION="dry-run" ;;
        --apply) ACTION="apply" ;;
        --enable-rcon) ENABLE_RCON=1 ;;
        --recreate-web) RECREATE_WEB=1 ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown option: $1 (use --help)" ;;
    esac
    shift
done

[[ "$(id -u)" == 0 ]] || die "run as root so all production configuration files can be updated"
command -v openssl >/dev/null 2>&1 || die "openssl is required"
command -v python3 >/dev/null 2>&1 || die "python3 is required"

WEB_ENV="$WEB_DIR/.env"
CORE_CONFIG="$CORE_DIR/plugins/ChampionshipsCore/config.yml"
CORE_BRIDGE_CONFIG="$CORE_DIR/plugins/ChampionshipsAuthBridge/config.yml"
CORE_AUTHME_CONFIG="$CORE_DIR/plugins/AuthMe/config.yml"
CORE_SERVER_PROPERTIES="$CORE_DIR/server.properties"
BINGO_AUTHME_CONFIG="$BINGO_DIR/plugins/AuthMe/config.yml"
BINGO_SERVER_PROPERTIES="$BINGO_DIR/server.properties"
BUNGEE_PROXY_CONFIG="$BUNGEE_DIR/plugins/ChampionshipsAuthProxy/config.yml"
BUNGEE_AUTHME_CONFIG="$BUNGEE_DIR/plugins/AuthMeBungee/config.yml"

TARGETS=(
    "$WEB_ENV"
    "$CORE_CONFIG"
    "$CORE_BRIDGE_CONFIG"
    "$CORE_AUTHME_CONFIG"
    "$BINGO_AUTHME_CONFIG"
    "$BUNGEE_PROXY_CONFIG"
    "$BUNGEE_AUTHME_CONFIG"
    "$CORE_SERVER_PROPERTIES"
    "$BINGO_SERVER_PROPERTIES"
)

for target in "${TARGETS[@]}"; do
    [[ -f "$target" ]] || die "required production file is missing: $target"
done

# An already enabled RCON endpoint must not silently keep its old password.
# --enable-rcon is only needed to turn Core RCON on; active endpoints are
# rotated automatically.
ROTATE_CORE_RCON=0
ROTATE_BINGO_RCON=0
if grep -Eq '^enable-rcon[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$CORE_SERVER_PROPERTIES"; then
    ROTATE_CORE_RCON=1
fi
if grep -Eq '^enable-rcon[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$BINGO_SERVER_PROPERTIES"; then
    ROTATE_BINGO_RCON=1
fi
if ((ENABLE_RCON)); then
    ROTATE_CORE_RCON=1
fi

CURRENT_TEST_SERVER_ID="${CC_ROTATE_TEST_LEASE_SERVER_ID:-}"
if [[ -z "$CURRENT_TEST_SERVER_ID" ]]; then
    CURRENT_TEST_SERVER_ID="$(awk -F= '$1 == "TEST_LEASE_SERVER_ID" { print $2; exit }' "$WEB_ENV" | sed -E "s/^[[:space:]]+//; s/[[:space:]]+$//; s/^['\"]//; s/['\"]$//" || true)"
fi
CURRENT_TEST_SERVER_ID="${CURRENT_TEST_SERVER_ID:-cc-core-prod-debug}"

if [[ "$ACTION" == "dry-run" ]]; then
    printf '%s\n' "Validated production secret targets (no files changed):"
    printf '  %s\n' \
        "$WEB_ENV" \
        "$CORE_CONFIG" \
        "$CORE_BRIDGE_CONFIG" \
        "$CORE_AUTHME_CONFIG" \
        "$BINGO_AUTHME_CONFIG" \
        "$BUNGEE_PROXY_CONFIG" \
        "$BUNGEE_AUTHME_CONFIG"
    if ((ROTATE_CORE_RCON || ROTATE_BINGO_RCON)); then
        printf '  RCON: rotate Core=%s Bingo=%s (Core is enabled only with --enable-rcon)\n' \
            "$ROTATE_CORE_RCON" "$ROTATE_BINGO_RCON"
    else
        printf '%s\n' "  RCON: unchanged (use --enable-rcon only when the control path is required)"
    fi
    printf '%s\n' "Use --apply to generate and write new credentials."
    exit 0
fi

command -v flock >/dev/null 2>&1 || die "flock is required for concurrent-rotation protection"
LOCK_FILE="${CC_ROTATE_LOCK_FILE:-/data/cc-production-secret-rotation.lock}"
exec 9>"$LOCK_FILE"
flock -n 9 || die "another production secret rotation is already running"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/cc-secret-rotation.XXXXXX")"
cleanup() {
    rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

NEW_SECRETS="$WORK_DIR/secrets.env"
cat >"$NEW_SECRETS" <<EOF
CC_BRIDGE_CORE_KEY_ID=cc-core
CC_BRIDGE_CORE_HMAC_SECRET=$(openssl rand -hex 32)
CC_BRIDGE_PROXY_KEY_ID=cc-bungeecord-a
CC_BRIDGE_PROXY_HMAC_SECRET=$(openssl rand -hex 32)
CC_TEST_WEB_KEY_ID=cc-bot-harness
CC_TEST_WEB_HMAC_SECRET=$(openssl rand -hex 32)
TEST_LEASE_SERVER_ID=$CURRENT_TEST_SERVER_ID
TEST_LEASE_HMAC_SECRET=$(openssl rand -hex 32)
AUTHME_PROXY_SHARED_SECRET=$(openssl rand -hex 32)
EOF
if ((ROTATE_CORE_RCON)); then
    printf 'CC_CORE_RCON_PASSWORD=%s\n' "$(openssl rand -hex 32)" >>"$NEW_SECRETS"
fi
if ((ROTATE_BINGO_RCON)); then
    printf 'CC_BINGO_RCON_PASSWORD=%s\n' "$(openssl rand -hex 32)" >>"$NEW_SECRETS"
fi
chmod 600 "$NEW_SECRETS"

BACKUP_DIR="$BACKUP_ROOT/$TIMESTAMP"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

# The Python part validates and prepares every file before replacing any of
# them.  It also restores the originals if a later replacement fails.
ROTATE_WEB_ENV="$WEB_ENV" \
ROTATE_CORE_CONFIG="$CORE_CONFIG" \
ROTATE_CORE_BRIDGE_CONFIG="$CORE_BRIDGE_CONFIG" \
ROTATE_CORE_AUTHME_CONFIG="$CORE_AUTHME_CONFIG" \
ROTATE_BINGO_AUTHME_CONFIG="$BINGO_AUTHME_CONFIG" \
ROTATE_BUNGEE_PROXY_CONFIG="$BUNGEE_PROXY_CONFIG" \
ROTATE_BUNGEE_AUTHME_CONFIG="$BUNGEE_AUTHME_CONFIG" \
ROTATE_CORE_SERVER_PROPERTIES="$CORE_SERVER_PROPERTIES" \
ROTATE_BINGO_SERVER_PROPERTIES="$BINGO_SERVER_PROPERTIES" \
ROTATE_SECRETS_FILE="$SECRETS_FILE" \
ROTATE_BOT_SECRETS_FILE="$BOT_SECRETS_FILE" \
ROTATE_BOT_ENV_FILE="$BOT_ENV_FILE" \
ROTATE_BACKUP_DIR="$BACKUP_DIR" \
ROTATE_NEW_SECRETS="$NEW_SECRETS" \
ROTATE_CORE_RCON="$ROTATE_CORE_RCON" \
ROTATE_BINGO_RCON="$ROTATE_BINGO_RCON" \
python3 - <<'PY'
from __future__ import annotations

import json
import os
import re
import shutil
import stat
import tempfile
from pathlib import Path


def required(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise SystemExit(f"missing internal path: {name}")
    return value


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        values[key] = value
    return values


def load_secrets(path: Path) -> dict[str, str]:
    result = read_env(path)
    required_keys = (
        "CC_BRIDGE_CORE_KEY_ID",
        "CC_BRIDGE_CORE_HMAC_SECRET",
        "CC_BRIDGE_PROXY_KEY_ID",
        "CC_BRIDGE_PROXY_HMAC_SECRET",
        "CC_TEST_WEB_KEY_ID",
        "CC_TEST_WEB_HMAC_SECRET",
        "TEST_LEASE_SERVER_ID",
        "TEST_LEASE_HMAC_SECRET",
        "AUTHME_PROXY_SHARED_SECRET",
    )
    for key in required_keys:
        if not result.get(key):
            raise SystemExit(f"generated secret is missing {key}")
    for key, value in result.items():
        if key.endswith("SECRET") or key.endswith("PASSWORD"):
            if len(value.encode("utf-8")) < 32:
                raise SystemExit(f"generated secret is too short: {key}")
    web_env = Path(required("ROTATE_WEB_ENV"))
    web_values = read_env(web_env)
    bridge_master = web_values.get("BRIDGE_HMAC_SECRET", "")
    if len(bridge_master.encode("utf-8")) < 32 or bridge_master.startswith("replace-with-"):
        raise SystemExit("production Web BRIDGE_HMAC_SECRET is missing or too short; refusing to rotate")
    return result


def set_env_lines(text: str, updates: dict[str, str]) -> str:
    lines = text.splitlines(keepends=True)
    found: set[str] = set()
    output: list[str] = []
    for line in lines:
        stripped = line.lstrip()
        replaced = False
        if not stripped.startswith("#") and "=" in line:
            key = line.split("=", 1)[0].strip()
            if key in updates:
                ending = "\n" if line.endswith("\n") else ""
                output.append(f"{key}={updates[key]}{ending}")
                found.add(key)
                replaced = True
        if not replaced:
            output.append(line)
    if output and not output[-1].endswith("\n"):
        output[-1] += "\n"
    for key, value in updates.items():
        if key not in found:
            output.append(f"{key}={value}\n")
    return "".join(output)


def set_bridge_clients(text: str, secrets: dict[str, str]) -> str:
    lines = text.splitlines(keepends=True)
    index = next(
        (i for i, line in enumerate(lines) if line.startswith("BRIDGE_CLIENT_KEYS=")),
        None,
    )
    if index is None:
        raise SystemExit("production Web .env has no BRIDGE_CLIENT_KEYS")
    raw = lines[index].split("=", 1)[1].strip()
    quote = raw[0] if raw[:1] in "'\"" and raw[-1:] == raw[0] else ""
    if quote:
        raw = raw[1:-1]
    try:
        clients = json.loads(raw)
    except json.JSONDecodeError as error:
        raise SystemExit("production Web BRIDGE_CLIENT_KEYS is not valid JSON") from error
    if not isinstance(clients, list):
        raise SystemExit("production Web BRIDGE_CLIENT_KEYS must be a JSON array")

    replacements = {
        secrets["CC_BRIDGE_CORE_KEY_ID"]: (
            secrets["CC_BRIDGE_CORE_HMAC_SECRET"],
            "AUTHBRIDGE_WRITER",
        ),
        secrets["CC_BRIDGE_PROXY_KEY_ID"]: (
            secrets["CC_BRIDGE_PROXY_HMAC_SECRET"],
            "AUTH_PROXY_READER",
        ),
        secrets["CC_TEST_WEB_KEY_ID"]: (
            secrets["CC_TEST_WEB_HMAC_SECRET"],
            "TEST_HARNESS",
        ),
    }
    seen: set[str] = set()
    for item in clients:
        if not isinstance(item, dict):
            raise SystemExit("production Web BRIDGE_CLIENT_KEYS contains a non-object")
        key_id = item.get("keyId")
        if key_id in replacements:
            if key_id in seen:
                raise SystemExit(f"duplicate BRIDGE_CLIENT_KEYS keyId: {key_id}")
            secret, role = replacements[key_id]
            item["secret"] = secret
            item["role"] = role
            seen.add(key_id)
    for key_id, (secret, role) in replacements.items():
        if key_id not in seen:
            clients.append({"keyId": key_id, "secret": secret, "role": role})
    encoded = json.dumps(clients, separators=(",", ":"))
    ending = "\n" if lines[index].endswith("\n") else ""
    lines[index] = f"BRIDGE_CLIENT_KEYS={quote}{encoded}{quote}{ending}"
    return "".join(lines)


def replace_section_key(text: str, sections: tuple[str, ...], key: str, value: str) -> str:
    """Replace a scalar YAML key under an exact nested mapping path."""
    lines = text.splitlines(keepends=True)
    stack: list[tuple[int, str]] = []
    section_start: int | None = None
    section_indent = 0
    target_path = ".".join(sections)
    for index, line in enumerate(lines):
        match = re.match(r"^( *)([^#:\n][^:\n]*):(?:\s|$)", line)
        if not match:
            continue
        indent = len(match.group(1))
        name = match.group(2).strip().strip("'\"")
        while stack and indent <= stack[-1][0]:
            stack.pop()
        stack.append((indent, name))
        path = ".".join(name for _, name in stack)
        if path == target_path:
            section_start = index
            section_indent = indent
            continue
        if section_start is None:
            continue
        if indent <= section_indent and path != target_path:
            break
        # AuthMe uses four-space children while the Bukkit configs generally
        # use two; accept any direct descendant indentation without rewriting
        # nested mappings.
        if indent > section_indent and name == key:
            prefix = line[: len(line) - len(line.lstrip())]
            ending = "\n" if line.endswith("\n") else ""
            lines[index] = f"{prefix}{key}: {value}{ending}"
            return "".join(lines)
    raise SystemExit(f"missing YAML key {target_path}.{key}")


def replace_root_key(text: str, key: str, value: str) -> str:
    lines = text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        match = re.match(r"^( *)([^#:\n][^:\n]*):(?:\s|$)", line)
        if not match or len(match.group(1)) != 0 or match.group(2).strip() != key:
            continue
        ending = "\n" if line.endswith("\n") else ""
        lines[index] = f"{key}: {value}{ending}"
        return "".join(lines)
    raise SystemExit(f"missing root YAML key {key}")


def set_properties(text: str, updates: dict[str, str]) -> str:
    lines = text.splitlines(keepends=True)
    found: set[str] = set()
    for index, line in enumerate(lines):
        if line.lstrip().startswith("#") or "=" not in line:
            continue
        key = line.split("=", 1)[0]
        if key in updates:
            ending = "\n" if line.endswith("\n") else ""
            lines[index] = f"{key}={updates[key]}{ending}"
            found.add(key)
    for key, value in updates.items():
        if key not in found:
            lines.append(f"{key}={value}\n")
    return "".join(lines)


def atomic_write(path: Path, data: bytes, mode: int) -> None:
    fd, name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        try:
            os.unlink(name)
        except FileNotFoundError:
            pass


paths = {key: Path(required(key)) for key in (
    "ROTATE_WEB_ENV",
    "ROTATE_CORE_CONFIG",
    "ROTATE_CORE_BRIDGE_CONFIG",
    "ROTATE_CORE_AUTHME_CONFIG",
    "ROTATE_BINGO_AUTHME_CONFIG",
    "ROTATE_BUNGEE_PROXY_CONFIG",
    "ROTATE_BUNGEE_AUTHME_CONFIG",
    "ROTATE_CORE_SERVER_PROPERTIES",
    "ROTATE_BINGO_SERVER_PROPERTIES",
    "ROTATE_SECRETS_FILE",
    "ROTATE_BOT_SECRETS_FILE",
    "ROTATE_BOT_ENV_FILE",
    "ROTATE_BACKUP_DIR",
    "ROTATE_NEW_SECRETS",
)}
secrets = load_secrets(paths["ROTATE_NEW_SECRETS"])
rotate_core_rcon = os.environ.get("ROTATE_CORE_RCON") == "1"
rotate_bingo_rcon = os.environ.get("ROTATE_BINGO_RCON") == "1"

contents = {path: path.read_text(encoding="utf-8") for path in paths.values() if path.is_file()}
if "BRIDGE_HMAC_SECRET=" not in contents[paths["ROTATE_WEB_ENV"]]:
    raise SystemExit("production Web .env has no BRIDGE_HMAC_SECRET; refusing to rotate")

web_text = set_bridge_clients(contents[paths["ROTATE_WEB_ENV"]], secrets)
web_text = set_env_lines(
    web_text,
    {
        "TEST_LEASE_SERVER_ID": secrets["TEST_LEASE_SERVER_ID"],
        "TEST_LEASE_HMAC_SECRET": secrets["TEST_LEASE_HMAC_SECRET"],
    },
)

core_text = contents[paths["ROTATE_CORE_CONFIG"]]
core_text = replace_section_key(core_text, ("test", "auth"), "server-id", secrets["TEST_LEASE_SERVER_ID"])
core_text = replace_section_key(core_text, ("test", "auth"), "hmac-secret", secrets["TEST_LEASE_HMAC_SECRET"])
core_text = replace_section_key(core_text, ("test", "identity"), "key-id", secrets["CC_TEST_WEB_KEY_ID"])
core_text = replace_section_key(core_text, ("test", "identity"), "hmac-secret", secrets["CC_TEST_WEB_HMAC_SECRET"])
core_text = replace_section_key(core_text, ("leaderboard-sync",), "key-id", secrets["CC_BRIDGE_CORE_KEY_ID"])
core_text = replace_section_key(core_text, ("leaderboard-sync",), "hmac-secret", secrets["CC_BRIDGE_CORE_HMAC_SECRET"])

core_bridge_text = contents[paths["ROTATE_CORE_BRIDGE_CONFIG"]]
core_bridge_text = replace_section_key(core_bridge_text, ("api",), "key-id", secrets["CC_BRIDGE_CORE_KEY_ID"])
core_bridge_text = replace_section_key(core_bridge_text, ("api",), "hmac-secret", secrets["CC_BRIDGE_CORE_HMAC_SECRET"])

bungee_proxy_text = contents[paths["ROTATE_BUNGEE_PROXY_CONFIG"]]
bungee_proxy_text = replace_section_key(bungee_proxy_text, ("api",), "key-id", secrets["CC_BRIDGE_PROXY_KEY_ID"])
bungee_proxy_text = replace_section_key(bungee_proxy_text, ("api",), "hmac-secret", secrets["CC_BRIDGE_PROXY_HMAC_SECRET"])

core_authme_text = replace_section_key(contents[paths["ROTATE_CORE_AUTHME_CONFIG"]], ("Hooks",), "proxySharedSecret", secrets["AUTHME_PROXY_SHARED_SECRET"])
bingo_authme_text = replace_section_key(contents[paths["ROTATE_BINGO_AUTHME_CONFIG"]], ("Hooks",), "proxySharedSecret", secrets["AUTHME_PROXY_SHARED_SECRET"])
bungee_authme_text = replace_root_key(contents[paths["ROTATE_BUNGEE_AUTHME_CONFIG"]], "proxySharedSecret", secrets["AUTHME_PROXY_SHARED_SECRET"])

core_properties = contents[paths["ROTATE_CORE_SERVER_PROPERTIES"]]
bingo_properties = contents[paths["ROTATE_BINGO_SERVER_PROPERTIES"]]
if rotate_core_rcon:
    password = secrets.get("CC_CORE_RCON_PASSWORD")
    if not password:
        raise SystemExit("--enable-rcon requested without a generated Core RCON password")
    core_properties = set_properties(core_properties, {"enable-rcon": "true", "rcon.password": password})
if rotate_bingo_rcon:
    password = secrets.get("CC_BINGO_RCON_PASSWORD")
    if not password:
        raise SystemExit("active Bingo RCON has no generated password")
    bingo_properties = set_properties(bingo_properties, {"rcon.password": password})

bot_secrets_text = set_env_lines(
    contents.get(paths["ROTATE_BOT_SECRETS_FILE"], ""),
    {
        "CC_TEST_WEB_KEY_ID": secrets["CC_TEST_WEB_KEY_ID"],
        "CC_TEST_WEB_HMAC_SECRET": secrets["CC_TEST_WEB_HMAC_SECRET"],
        "TEST_LEASE_SERVER_ID": secrets["TEST_LEASE_SERVER_ID"],
        "TEST_LEASE_HMAC_SECRET": secrets["TEST_LEASE_HMAC_SECRET"],
    },
)
if rotate_core_rcon:
    bot_secrets_text = set_env_lines(bot_secrets_text, {"CC_TEST_CORE_RCON_PASSWORD": secrets["CC_CORE_RCON_PASSWORD"]})
bot_env_text = set_env_lines(
    contents.get(paths["ROTATE_BOT_ENV_FILE"], ""),
    {
        "CC_TEST_WEB_KEY_ID": secrets["CC_TEST_WEB_KEY_ID"],
        "CC_TEST_WEB_HMAC_SECRET": secrets["CC_TEST_WEB_HMAC_SECRET"],
    },
)

outputs: dict[Path, str] = {
    paths["ROTATE_WEB_ENV"]: web_text,
    paths["ROTATE_CORE_CONFIG"]: core_text,
    paths["ROTATE_CORE_BRIDGE_CONFIG"]: core_bridge_text,
    paths["ROTATE_CORE_AUTHME_CONFIG"]: core_authme_text,
    paths["ROTATE_BINGO_AUTHME_CONFIG"]: bingo_authme_text,
    paths["ROTATE_BUNGEE_PROXY_CONFIG"]: bungee_proxy_text,
    paths["ROTATE_BUNGEE_AUTHME_CONFIG"]: bungee_authme_text,
    paths["ROTATE_SECRETS_FILE"]: paths["ROTATE_NEW_SECRETS"].read_text(encoding="utf-8"),
    paths["ROTATE_BOT_SECRETS_FILE"]: bot_secrets_text,
    paths["ROTATE_BOT_ENV_FILE"]: bot_env_text,
}
if rotate_core_rcon:
    outputs[paths["ROTATE_CORE_SERVER_PROPERTIES"]] = core_properties
if rotate_bingo_rcon:
    outputs[paths["ROTATE_BINGO_SERVER_PROPERTIES"]] = bingo_properties

for path, data in outputs.items():
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and not path.is_file():
        raise SystemExit(f"target is not a regular file: {path}")

backup = paths["ROTATE_BACKUP_DIR"]
for path in outputs:
    if path.exists():
        destination = backup / (str(path).lstrip("/").replace("/", "__"))
        shutil.copy2(path, destination)
        os.chmod(destination, stat.S_IRUSR | stat.S_IWUSR)

originals = {path: path.read_bytes() for path in outputs if path.exists()}
modes = {path: stat.S_IMODE(path.stat().st_mode) for path in outputs if path.exists()}
secret_paths = {
    paths["ROTATE_WEB_ENV"],
    paths["ROTATE_SECRETS_FILE"],
    paths["ROTATE_BOT_SECRETS_FILE"],
    paths["ROTATE_BOT_ENV_FILE"],
}
try:
    for path, data in outputs.items():
        mode = 0o600 if path in secret_paths else modes.get(path, 0o644)
        atomic_write(path, data.encode("utf-8"), mode)
except Exception:
    for path, data in originals.items():
        atomic_write(path, data, modes[path])
    raise

print("rotated and wrote production credentials")
print("changed files:")
for path in outputs:
    print(f"  {path}")
print(f"secure rollback backup: {backup}")
if rotate_core_rcon or rotate_bingo_rcon:
    print(
        "RCON passwords rotated (Core=%s, Bingo=%s); values are in the 0600 secret files"
        % (rotate_core_rcon, rotate_bingo_rcon)
    )
else:
    print("RCON was left unchanged and disabled")
PY

if ((RECREATE_WEB)); then
    command -v docker >/dev/null 2>&1 || die "docker is required for --recreate-web"
    [[ -f "$WEB_DIR/compose.yml" ]] || die "cc-web compose.yml is missing: $WEB_DIR/compose.yml"
    (cd "$WEB_DIR" && docker compose up -d --no-deps --force-recreate web)
    for _ in $(seq 1 60); do
        web_status="$(docker inspect --format '{{.State.Health.Status}}' cc-web 2>/dev/null || true)"
        [[ "$web_status" == healthy ]] && break
        sleep 1
    done
    [[ "${web_status:-}" == healthy ]] || die "cc-web did not become healthy after recreation"
    printf '%s\n' "cc-web was recreated and is healthy"
fi

if ((RECREATE_WEB)); then
    printf '%s\n' "Rotation finished. Restart cc-bungeecord, cc-core and cc-bingo through MCSManager before using the new credentials."
else
    printf '%s\n' "Rotation finished. Recreate cc-web with Docker Compose, then restart cc-bungeecord, cc-core and cc-bingo through MCSManager."
fi
printf '%s\n' "The script did not restart or stop Minecraft services."
