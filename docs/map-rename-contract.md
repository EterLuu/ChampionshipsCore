# Map Rename Contract

`/cc map rename <game> <old> <new>` changes a map registration name. For every game except Build
Mart, it does not move the physical Bukkit world. Build Mart supports several independent map
regions in one physical world. A map that is the sole user of its default world
`buildmart_<registration>` owns that world: a rename changes both identities, for example `area`
and `buildmart_area` become `skyline` and `buildmart_skyline`. A shared or custom world remains
unchanged when one of its map registrations is renamed.

Build Mart drafts may bind any already loaded Bukkit world, including one used by another Build Mart
map. The administrator creates that world through normal world management before binding it in
prepare. Row-layout copies are placed east of the map's known infrastructure with a 384-block
clearance; administrators must still choose non-overlapping source/hub regions for separate maps.

The command is permitted only while the selected map has no active runtime instances, no prepare
session exists, and no formal event for the game is running. For Build Mart, the physical world is
moved only when the selected map is its sole user and its configured name is exactly
`buildmart_<old>`. This prevents a registration rename from moving a shared or unrelated world.

The operation is one logical transaction:

1. Detach the selected runtime registration and wait for queued DAILY and point writes.
2. Migrate every first-party database map identity in the same SQL transaction: `player_points.area`,
   `daily_match_results.map`, `daily_player_records.map`, `daily_map_player_stats.map`, and
   `daily_pkw_records.map` for Parkour Warrior.
3. Move the map YAML and update its `name` field. Map-owned generated assets move with the
   registration. Build Mart normally moves `material-manifests/<map>.yml` (rewriting its `map`
   field) and `schematics/<map>/` (including `base.schem` and `material-zones/`). Legacy Build
   Mart maps may have a YAML `name` distinct from the registration/file stem: `base.schem` follows
   the registration, while the generated material manifest and material-zone snapshots follow the
   YAML name. Both legacy locations are moved and normalized to the new registration. Battle Box,
   Parkour Tag, and TNT Run move `schematics/<map>/` (including `arena.schem`).
4. For a Build Mart map that solely owns `buildmart_<old>`, unload and move that world plus all named
   sidecar data: the Core map template (`ChampionshipsCore/maps/<world>`), WorldGuard state
   (`WorldGuard/worlds/<world>`), and FAWE history (`FastAsyncWorldEdit/history/<world>`). The map
   YAML's `world-name`, serialized location world prefixes, and `world_key` values, and the material
   manifest's `world`, are updated to the new dedicated world before the runtime map is recreated.
   A shared/custom Build Mart world is not moved and its references remain unchanged. The target world
   and every target sidecar path must be absent; the operation never overwrites existing state.
5. Update matching entries in `formal-events.<Game>.maps`, recreate the runtime map registration
   under the new name, then update live DAILY records, per-map statistics, Parkour Warrior records,
   leaderboards, and open map-list menus.

Conflicting historical DAILY rows are merged with the same semantics used during normal writes:
counts accumulate, maxima retain the greater value, and timed records retain the faster value. All
target asset paths must be absent; the command never overwrites assets belonging to a prior map.

Any later feature that persists a map registration name must be added to
`MapRecordRenameMigration`; any map-named file or directory must be added to `MapAssetRename` with a
rollback state. Global game data, shared Build Mart blueprints (`buildmart/blueprints`), and the
`buildmart-bak` backup directory are intentionally not map-owned rename targets. Except for the
dedicated Build Mart rule above, a world name is changed only by the separate world-management
operation; registration rename must not move a shared world folder or alter coordinates stored in
other map definitions.
