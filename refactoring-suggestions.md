# ChampionshipsCore — optimization and refactoring suggestions

Reviewed on 2026-11-10/11 against `main` at `b7bd711`. Nothing in the repository was changed.

## Scope and limits

- Method: repository-wide measurement (line counts, method lengths, pattern counts) plus targeted
  reads of the hotspots those measurements pointed at.
- Size: about 197k lines of Java in 9 Maven modules; 128k of it is `championships-core` main code.
- Not reviewed in depth: the GUI menu classes, `championships-auth-bridge`, `championships-auth-proxy`,
  the Redis module, `WorkerListener`, and most individual game areas beyond their metrics.
- No security review was done.
- The project was not built and the test suite was not run. The repo's own check scripts were run
  (see section 8).
- Runtime cost claims are from reading the code, not from profiling.

Unless a path starts with a module name, it is relative to
`championships-core/src/main/java/ink/ziip/championshipscore/`.

## Summary

| # | Suggestion | Payoff | Effort | Risk |
| --- | --- | --- | --- | --- |
| 1 | Route game events centrally instead of per-instance listeners | High | Large | Medium |
| 2 | Replace parallel per-game switches with one interface and registry | High | Medium | Low |
| 3 | Split the god classes by composition | High | Large | Medium |
| 4 | Converge the two message systems | Medium | Large | Medium |
| 5 | Stop tests bypassing constructors | Medium | Small, then ongoing | Low |
| 6 | Use the shared Bingo scoring engine for local rounds | Medium | Medium | Medium |
| 7 | Make DAO failures distinguishable from "not found" | Medium | Medium | Low |
| 8 | Close build and CI gaps | Medium | Small | Low |
| 9 | Replace inline fully-qualified names with imports | Low | Small | Low |

---

## 1. Every arena instance registers its own full listener set

**Evidence**

- `api/game/instance/BaseGameInstance.java:132-133` creates and registers a `GameInstanceHandler`
  (10 `@EventHandler` methods) in the constructor of every instance.
- All 18 area constructors also register their game handler, for example
  `api/game/laserbox/runtime/LaserBoxArea.java:138`. Those handlers carry 6 to 30 `@EventHandler`
  methods each (`BingoHandler` 30, `BuildMartHandler` 29, `FrostbiteHandler` 22).
- `api/BaseListener.java` `register()` wraps each handler in a lambda that first calls
  `SpectatorGameEventGate.suppress`, which reflectively invokes every entity-returning getter on
  the event (`api/game/spectate/SpectatorGameEventGate.java`).
- Handlers then filter with a preamble such as `if (area.notAreaPlayer(player)) return;`. That
  line appears 13 times in `ParkourTagHandler`, 12 in `BattleBoxHandler`, 11 in `SkyWarsHandler`.

**Effect**

Every Bukkit event is delivered to every loaded arena copy, running or idle. Work per event grows
with the number of instances times the number of handlers, and each delivery pays the reflective
gate before the handler's own filter rejects it.

**Suggestion**

Player move events already have the right design: `api/game/manager/GameManagerHandler.java:244-263`
resolves the player's area once and calls `routePlayerMove*` on that one instance. Extend the same
routing to the other player and entity events, and let the per-game handler become a plain method
target rather than a registered listener. This also removes the `notAreaPlayer` preambles.

A cheaper intermediate step is to register the game handler only between start and reset.

**Constraint to keep:** the spectator gate must still run before any game logic, as the spectator
contract requires.

---

## 2. Per-game dispatch is hand-written in parallel switches

**Evidence**

- `api/schedule/ScheduleManager.java` has six switches over `GameTypeEnum` at lines 304, 348, 407,
  474, 619 and 780. They dispatch to per-game schedule managers with inconsistent method names
  (`startGame`, `startBattleBox`, `startParkourTag`).
- `supportsFormalEvent` (`ScheduleManager.java:304-325`) lists all 17 enum values as `true` with a
  `default -> false` that can never be reached. The method is dead logic.
- `api/game/manager/GameManager.java:95-176` declares 17 typed manager fields and then puts the
  same 17 objects into an `EnumMap`.
- `api/vote/VoteMenu.java` has 22 game `case` labels; `presentation/sidebar/CoreSidebarManager.java`
  has 9 `instanceof <Game>Area` checks.
- `api/game/model/GameTypeEnum.java:58-78` overrides `toString()` to return a localized name read
  from mutable static config. Anything that stringifies the enum (logs, keys, concatenation) gets
  display text, and gets `null` before configuration loads.

**Suggestion**

- Introduce one `FormalSchedule` interface (`start`, `isEnabled`, `hasNextRound`, `stop`) and hold
  the implementations in an `EnumMap<GameTypeEnum, FormalSchedule>`. The six switches collapse to
  lookups. Dodgebolt and Sulfur Soccer, which are tracked by transition tasks today, become two
  more implementations.
- Delete `supportsFormalEvent` or reduce it to the registry lookup.
- Add an explicit `displayName()` on `GameTypeEnum` and stop overriding `toString()`.
- Prefer `areaManager(type)` over the 17 typed getters in new code.

---

## 3. God classes

**Evidence**

| Class | Lines | Methods | Notes |
| --- | --- | --- | --- |
| `championships-bingo-worker/src/main/java/ink/ziip/championshipscore/worker/WorkerMatchSession.java` | 3077 | ~150 | 59 fields; 47 `synchronized` methods on one monitor |
| `api/game/acerace/runtime/AceRaceArea.java` | 2319 | 143 | 34 fields |
| `api/game/manager/GameManager.java` | 2257 | 125 | `hotReload` (line 328) is 153 lines |
| `api/game/instance/BaseGameInstance.java` | 2199 | 183 | 35 fields; see breakdown below |
| `api/game/riptiderush/runtime/RiptideRushArea.java` | 1560 | 98 | 50 fields |

Across the game runtime, instance and manager classes, 8 methods exceed 100 lines and 36 exceed 60.

`BaseGameInstance` mixes at least seven responsibilities:

| Lines | Responsibility |
| --- | --- |
| 332-437 | Points and database write-back |
| 536-995 | Map load, save, publish, staging and rollback (file IO) |
| 997-1115 | Boss bars |
| 1130-1245 | Post-game settlement and routing |
| 1246-1445 | Rule introduction phase |
| 1465-1645 | Preparation and final countdown |
| 1727-2005 | Spectator delegation |

**Worst single method**

`api/game/parkourwarrior/runtime/ParkourWarriorTeamArea.java:188` `handlePlayerMove` is 211 lines
and 7 levels deep:

- It is `synchronized` on the area and runs on the per-move path for every player.
- It formats the player's display name before any region check, so the work is done on every move.
- It iterates all checkpoints twice.
- The pair "send to all spectators, then send the same text to the player" is written out five
  times.
- It checks `checkpointProgress != null` and then calls `checkpointProgress.put(...)` outside that
  check.
- It calls `ChampionshipsCore.getInstance()` although the class already holds `plugin`.

In `WorkerMatchSession`, the longest method is `releaseTestResources` (line 2464, 142 lines), which
is test-session cleanup living in the production session class.

**Suggestion**

- Split `BaseGameInstance` by composition, following the pattern already used for
  `CountdownBlockDisappearance` and `ParticipantCallbacks`. Extract the map file operations first:
  that block needs no game state and is the cleanest cut. Boss bars, the introduction phase and the
  final countdown are the next candidates.
- Split `handlePlayerMove` into "enter checkpoint", "advance sub-checkpoint" and "finish", add one
  helper that announces to spectators and the player, and build the display name only when a
  message is actually sent.
- Move `releaseTestResources` out of `WorkerMatchSession` into a collaborator owned by the test
  release path.

**Constraint to keep:** do not widen visibility only for tests, and keep spectator state owned by
`SpectatorManager`.

---

## 4. Two message systems side by side

**Evidence**

- `configuration/config/message/MessageConfig.java` is 3411 lines and declares 1066
  `public static String` fields, each populated by reflection one at a time
  (`configuration/config/BaseConfigurationFile.java:252-299`). The fields are not volatile and the
  set is not published atomically.
- The same class also serves `codeText(key, args...)`: 1193 keys under `code-text` in
  `message.yml`, read from an immutable map that is swapped atomically, used at 1280 call sites.
- 1112 of the 1193 keys are numbered after Java methods, for example
  `base-game-instance.load-map-1`. Renaming a method leaves the key misleading. The development
  guide asks for keys with a clear meaning.
- Many numbered keys are fragments: a short log label (`load-map-1`) reused with several message
  keys (`load-map-2` to `load-map-5`).
- There are 439 chained `.replace("%x%", ...)` calls in 79 files, against 24 uses of
  `MessageTemplate`. The guide requires single-pass substitution so that a player or map name
  containing a placeholder is not expanded again.
- 14 fields are never referenced in production code:
  `GAME_TEAM_GAME_START_SUCCESSFUL`, `GAME_TEAM_GAME_START_FAILED`,
  `GAME_SINGLE_GAME_START_SUCCESSFUL`, `GAME_SINGLE_GAME_START_FAILED`,
  `LASER_BOX_GUN_SELECTION_DEFAULT`, `LASER_BOX_NO_AMMO`, `PARKOUR_TAG_KITS_WIND_CHARGE`,
  `PARKOUR_TAG_KITS_USE_WIND_CHARGE`, `PARKOUR_TAG_KITS_USE_WIND_CHARGE_FAILED`,
  `LASER_SHOWDOWN_KILL_PLAYER`, `LASER_SHOWDOWN_PLAYER_DEATH_BY_VOID`, `EVENT_IMPORT_NOT_READY`,
  `EVENT_IMPORT_RUNNING`, `EVENT_START_NO_EVENT`. Three of them are referenced only by tests.
- One field name is misspelled: `GAME_BOARD_RWO`.

**Suggestion**

- Stop adding static fields. Converge on the keyed immutable snapshot that `codeText` already uses.
- Give keys compile-checked constants (an enum or a generated constants class) so a missing key is
  a build error instead of a runtime `IllegalArgumentException`.
- Rename numbered keys by meaning as files are touched, one key per complete sentence.
- Replace `.replace` chains with `MessageTemplate` at the same time.
- Remove the 14 unreferenced fields after checking the running `message.yml` for overrides, as the
  guide's cleanup rule requires.

**Constraint to keep:** key renames are a configuration migration. Bump the file version and
migrate deployed overrides in the same delivery as the JAR.

---

## 5. Tests bypass constructors, and that leaks into production

**Evidence**

- 52 test files look up `sun.misc.Unsafe` themselves to allocate objects without running a
  constructor. Another 46 use the shared helper
  `championships-core/src/test/java/ink/ziip/championshipscore/support/TestObjects.java`.
- 83 test files build dynamic proxies by hand, and tests contain 181 `setAccessible` calls. No
  mocking library is on the classpath.
- Tests also assign global static config directly, for example
  `MessageConfig.EVENT_START_STARTED = "..."` in `EventStartCommandTest`.
- The leak into production: `api/schedule/ScheduleManager.java:88-110` null-checks
  `startSelections`, which is a `final` field with an initializer. It can only be null in an
  instance allocated through `Unsafe`.
- The root cause is that classes take the plugin and reach through it:
  `plugin.get...Manager()` appears at 1092 call sites (364 `getGameManager()`, 234
  `getTeamManager()`), and 15 files call `ChampionshipsCore.getInstance()` while already holding
  `plugin`.

**Suggestion**

- Now: replace the private `Unsafe` helpers in the 52 test files with `TestObjects.allocate`.
- Going forward: when a class is added or extracted (sections 2 and 3), give it a constructor that
  takes narrow interfaces instead of the plugin, so the test can construct it normally.
- Remove the dead null guards once the affected tests construct real objects.

---

## 6. Bingo scoring exists twice

**Evidence**

- `api/game/bingo/game/BingoRound.java:519` `awardPoints` implements claim rank, line bonuses and
  the co-op rule for local rounds.
- `championships-bingo-engine/src/main/java/ink/ziip/championshipscore/bingo/engine/BingoScoringEngine.java` `decide` (line 93) implements the same rules.
  It is used only by `WorkerMatchSession` and `api/game/bingo/execution/RemoteBingoMatch.java`.
- The local path is still live: `api/game/manager/GameManager.java:179` `startLocalBingo`.
- 13 `display.*` and `presentation.*` key literals are written out in both Core and Worker rather
  than defined once in `championships-common`, for example `"display.material"`,
  `"display.amount"`, `"presentation.tab.team-footer"`.

**Suggestion**

- Drive local rounds through `BingoScoringEngine` via an adapter from `ChampionshipTeam` to the
  engine's team snapshot, then delete the scoring code in `BingoRound`.
- Move the shared key literals into constants in `championships-common`.

**Open question:** whether the engine covers differential mode was not checked. Confirm that
before starting.

**Constraint to keep:** shared behaviour changes need both Core and Worker built, deployed and
restarted, and frozen manifests must stay readable without changing their hash.

---

## 7. DAO failures look like "not found"

**Evidence**

- `database/rank/RankDaoImpl.java:175-178` `getGameStatusOrder` returns `-1` on `SQLException`.
  `api/rank/RankManager.java:348` reads `-1` as "no row" and goes on to insert.
- `addGameStatus` and `deleteGameStatus` (`RankDaoImpl.java:205`, `:223`) return `void` and only
  log, so `publishRankChange("game-order-added")` at `RankManager.java:356-357` fires after a
  failed write.
- `RankManager.resetGameOrder` (`:367-374`) runs 17 single deletes, each on its own connection,
  with no transaction, and then publishes success.
- The `database` package has 78 hand-written `prepareStatement` blocks and 63
  `catch (SQLException)`. About 18 of those return a default value; about 21 rethrow.

**Suggestion**

Add a small helper inside `database/` with three entry points — query with a row mapper, update,
and "run in one transaction" — that throws a single unchecked data-access exception. The manager
write queues then decide what a failure means. Convert `RankDaoImpl` first because its callers are
few.

**What is fine:** the threading. `RankManager.enqueueRankTask` keeps database IO off server
threads and preserves ordering. Do not add a new executor.

---

## 8. Build and CI gaps

**Evidence**

- `.github/workflows/maven.yml` runs `check-source-layout.py`, `check-spectator-contract.py` and
  `mvn clean package`. It does not run `scripts/format-java.sh --check`, Ruff, `check-docs.py` or
  the tests in `championships-test-harness`.
- The format check fails today on two files:
  `api/ChampionshipPermissions.java` and `command/game/GameMainCommand.java`.
- `scripts/format-java.sh` lists files with `rg`, which was not installed in this environment.
- Open-ended version ranges make builds non-reproducible: `mariadb-java-client [3.5.9,)` at
  `championships-core/pom.xml:93`, and `jackson-databind [2.21.7,)` in both auth modules.
- InventoryFramework (`IF` 0.12.1) is shaded and relocated into the Core JAR (1105 classes, about
  3 MB uncompressed) but is never imported.
- Both MySQL and MariaDB JDBC drivers are shaded. The MySQL driver with protobuf is about 12.8 MB
  uncompressed.
- PlaceholderAPI is 2.12.3 in Core and 2.12.2 in Worker. The Lombok version is written twice in
  `championships-core/pom.xml`.
- `.github/dependabot.yml` covers only Maven, quarterly. It does not cover the npm harness or
  GitHub Actions.

Checks that pass today: `check-source-layout.py`, `check-spectator-contract.py --strict`,
`check-docs.py`.

**Suggestion**

- Format the two files, then add the format check, Ruff, `check-docs.py` and the harness tests to
  CI.
- List files in `format-java.sh` with `git ls-files` so it has no extra tool dependency.
- Pin the two version ranges to exact versions.
- Remove the `IF` dependency, its shade include and its relocation.
- Decide whether both JDBC drivers are needed; if every deployment is MariaDB or the MariaDB driver
  is acceptable for MySQL, drop the MySQL driver and protobuf.
- Move PlaceholderAPI and Lombok versions into the parent `dependencyManagement`.
- Add npm and `github-actions` ecosystems to Dependabot.

---

## 9. Inline fully-qualified names

**Evidence**

About 595 lines across the scanned modules use names such as `java.util.Map` or
`org.bukkit.configuration.file.YamlConfiguration` inline instead of an import. The heaviest files
are `BingoRound.java` (38), `MessageConfig.java` (22), `WorkerListener.java` (22) and
`WorkerMatchSession.java` (21). `api/BaseListener.java` is a short file made hard to read by it.

**Suggestion**

Replace them with imports. google-java-format does not do this, so use an IDE inspection or a
one-off script, then run the formatter. It is a mechanical, behaviour-neutral change and is best
done as its own commit.

---

## Leave alone

These are already well structured; refactoring around them should preserve them.

- Central move routing in `GameManagerHandler`.
- The start-generation and `ParticipantCallbacks` guards on delayed game callbacks.
- `ConfiguredLocation` and `LocationConfig` as the only coordinate conversion path.
- The `database` package as the sole owner of SQL, and the managers' ordered write queues.
- The shared `GameStartArguments` / `GameStartService` path for `game start` and `event start`.
- The three repository check scripts.

## Suggested order

1. **Hours each:** build and CI gaps (8), the shared test helper (5), the dead switch (2), inline
   names (9).
2. **Contained refactors:** the schedule interface and registry (2), the DAO helper (7).
3. **Core structure:** central event routing (1), the `BaseGameInstance` split (3).
4. **Cross-server changes:** message convergence (4) and scoring unification (6). Both touch
   configuration or the Core/Worker contract, so they need a config migration or both servers
   verified.
