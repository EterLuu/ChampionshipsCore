#!/usr/bin/env python3
"""Check that project documentation stays in sync with the code base.

Verifies, using only the Python standard library:

* every relative Markdown link resolves to an existing file;
* repo-relative file paths quoted in documentation exist;
* referenced Java class names still have a source file;
* dotted configuration keys quoted in documentation appear in code/resources;
* game display names in ``docs/02-games/01-games.md`` match ``message.yml``.

Run from the repository root:

    python3 scripts/check-docs.py
"""

from __future__ import annotations

import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")

SKIP_DIRS = {".git", "node_modules", "target", "versions", "libraries", ".paper"}

# Class-name suffixes that identify project types worth verifying.
TYPE_SUFFIXES = (
    "Manager", "Config", "Configuration", "Handler", "Command", "Enum", "Service",
    "Dao", "DaoImpl", "Gui", "Menu", "Area", "Instance", "Listener", "Scheduler",
    "Controller", "Resolver", "Registry", "Session", "Planner", "Definition",
    "Provider", "Loader", "Task", "Token", "Event", "Rule", "Arguments", "Selection",
    "Context", "Policy", "Snapshot", "Codec", "Transport", "Cache", "Store", "Factory",
    "Adapter", "Parser", "Writer", "Reader", "Tracker", "Steps", "Flow", "Plan",
    "Model", "Variant", "Engine", "Pool", "Gun", "Presentation", "Layout", "Page",
    "Format", "Builder", "Support",
)

# Identifiers that come from dependencies or other repositories.
EXTERNAL_IDENTIFIERS = {
    "AuthBridge", "AuthProxy", "ChampionshipsAuthBridge", "ChampionshipsAuthProxy",
    "ChampionshipsBingoWorker", "FastBoard", "OfflinePlayer", "PhaseTracker", "Context",
}

# Runtime/config-relative paths that are not repository files.
RUNTIME_PREFIXES = ("plugins/", "buildmart/", "sulfursoccer/", "riptiderush/", "test/")

FILE_EXTS = (".java", ".json", ".yml", ".yaml", ".md", ".properties", ".sh",
             ".py", ".jsonl", ".schem")


def walk_files(predicate):
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if predicate(name):
                yield os.path.join(base, name)


def markdown_files():
    return [p for p in walk_files(lambda n: n.endswith(".md"))
            if os.path.basename(p) != "AGENTS.md"]


def load_text(paths):
    for path in paths:
        try:
            yield path, open(path, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue


LINK_RE = re.compile(r"\]\(([^)\n]+)\)")
SPAN_RE = re.compile(r"`([^`]+)`")
KEY_RE = re.compile(r"^[a-z][a-z0-9-]*(?:\.[a-z0-9<>_*\-]+)+$")
CAMEL_RE = re.compile(r"`([A-Z][A-Za-z0-9]+)`")


def link_target(raw):
    parts = raw.strip().split()
    return parts[0] if parts else ""


def is_external(target):
    return target.startswith(("http://", "https://", "mailto:", "#", "ftp://", "tel:"))


def resolve(base_file, target):
    path = target.split("#", 1)[0]
    if not path:
        return None
    if path.startswith("/"):
        return os.path.normpath(path)
    return os.path.normpath(os.path.join(os.path.dirname(base_file), path))


def check_links():
    problems = []
    for path, text in load_text(markdown_files()):
        for match in LINK_RE.finditer(text):
            target = link_target(match.group(1))
            if not target or is_external(target):
                continue
            dest = resolve(path, target)
            if dest and not os.path.exists(dest):
                problems.append(
                    f"{os.path.relpath(path, ROOT)}: broken link -> {target}")
    return problems


def repo_path_of(span):
    if span.startswith(ROOT + os.sep):
        return span
    head = span.split("/", 1)[0]
    if span.startswith(RUNTIME_PREFIXES) or "<" in span or ">" in span:
        return None
    if os.path.isdir(os.path.join(ROOT, head)):
        return os.path.join(ROOT, span)
    return None


def check_paths():
    problems = []
    for path, text in load_text(markdown_files()):
        for span in SPAN_RE.findall(text):
            span = span.strip()
            if " " in span or not any(ext in span for ext in FILE_EXTS):
                continue
            if "/" not in span:
                continue
            candidate = repo_path_of(span)
            if candidate and not os.path.exists(candidate):
                problems.append(
                    f"{os.path.relpath(path, ROOT)}: missing path -> {span}")
    return problems


def check_identifiers(java_classes):
    problems = []
    for path, text in load_text(markdown_files()):
        for name in CAMEL_RE.findall(text):
            if name in java_classes or name in EXTERNAL_IDENTIFIERS:
                continue
            if not name.endswith(TYPE_SUFFIXES):
                continue
            problems.append(
                f"{os.path.relpath(path, ROOT)}: unknown type -> {name}")
    return problems


def check_config_keys(blob):
    problems = []
    for path, text in load_text(markdown_files()):
        for key in SPAN_RE.findall(text):
            key = key.strip()
            if not KEY_RE.match(key):
                continue
            if key.endswith((".toml", ".md", ".yml", ".yaml", ".json", ".java",
                             ".properties", ".sh", ".py")):
                continue
            last = key.split(".")[-1]
            if key in blob or last in blob:
                continue
            problems.append(
                f"{os.path.relpath(path, ROOT)}: unknown config key -> {key}")
    return problems


def parse_game_names(message_yml):
    pattern = re.compile(
        r"^  (parkourtag|battlebox|tntrun|lasershowdown|skywars|tgttos|"
        r"dragoneggcarnival|parkourwarrior|hotycodydusky|bingo|buildmart|dodgebolt|"
        r"riptiderush|frostbite|laserbox|sulfursoccer|acerace): (.+)$",
        re.M,
    )
    return {m.group(1): m.group(2).strip() for m in pattern.finditer(message_yml)}


def check_game_names():
    games_doc = os.path.join(DOCS, "02-games", "01-games.md")
    message_yml = os.path.join(
        ROOT, "championships-core", "src", "main", "resources", "message.yml")
    if not os.path.exists(games_doc) or not os.path.exists(message_yml):
        return []
    names = parse_game_names(open(message_yml, encoding="utf-8").read())
    row = re.compile(r"\| \[(.+?)\]\(#.*?\) \| `(.+?)` \| `(.+?)` \|")
    problems = []
    for line in open(games_doc, encoding="utf-8"):
        match = row.match(line)
        if not match:
            continue
        display, _token, config = match.groups()
        expected = names.get(config.lower())
        if expected is not None and expected != display:
            problems.append(
                f"docs/02-games/01-games.md: {config} display name '{display}' "
                f"!= message.yml '{expected}'")
    return problems


def main():
    java_classes = {os.path.basename(p)[:-5] for p in walk_files(lambda n: n.endswith(".java"))}
    blob = "\n".join(text for _, text in load_text(
        walk_files(lambda n: n.endswith((".java", ".yml", ".yaml", ".json", ".properties")))))

    problems = []
    problems += check_links()
    problems += check_paths()
    problems += check_identifiers(java_classes)
    problems += check_config_keys(blob)
    problems += check_game_names()

    if problems:
        print(f"Documentation check failed ({len(problems)} problem(s)):")
        for problem in problems:
            print("  -", problem)
        return 1
    print(f"Documentation check passed ({len(markdown_files())} files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
