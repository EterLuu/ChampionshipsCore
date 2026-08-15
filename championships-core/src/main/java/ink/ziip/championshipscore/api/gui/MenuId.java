package ink.ziip.championshipscore.api.gui;

import org.jetbrains.annotations.NotNull;

/**
 * Single source of truth for every GUI screen defined under gui.yml.
 *
 * <p>Each constant owns the absolute configuration prefix of its menu. Derived paths such as
 * {@code <menu>.items.<item>} or {@code <menu>.copy.<key>} are built through helper methods so
 * callers never concatenate raw strings by hand.</p>
 */
public enum MenuId {
    // Spectator
    SPECTATOR_VENUE_SELECTOR("spectator.menus.venue-selector"),
    SPECTATOR_SUB_ARENA_SELECTOR("spectator.menus.sub-arena-selector"),
    SPECTATOR_BUILD_MART_SELECTOR("spectator.menus.build-mart-selector"),
    SPECTATOR_VISIBILITY("spectator.menus.visibility"),
    SPECTATOR_PLAYER_VISIBILITY("spectator.menus.player-visibility-selector"),
    SPECTATOR_TEAM_VISIBILITY("spectator.menus.team-visibility-selector"),
    SPECTATOR_TEAM_POSITION("spectator.menus.team-position-selector"),

    // Team management
    TEAMS_OVERVIEW("teams.menus.overview"),
    TEAMS_MEMBERS("teams.menus.members"),

    // Free-play lobby
    DAILY_LOBBY("daily.menus.lobby-screen"),
    DAILY_GAME_SELECTION("daily.menus.game-selection-screen"),
    DAILY_LEADERBOARD("daily.menus.leaderboard-screen"),
    DAILY_PARTY("daily.menus.party-screen"),
    DAILY_STATISTICS("daily.menus.statistics-screen"),

    // Championship voting
    VOTING_BALLOT("voting.menus.ballot"),

    // Map editor
    MAP_EDITOR_PREPARE_TOOLBAR("map-editor.menus.prepare-toolbar"),
    MAP_EDITOR_INPUT("map-editor.menus.input"),
    MAP_EDITOR_AREA_LIST("map-editor.menus.area-list"),
    MAP_EDITOR_COUNTDOWN_BLOCKS("map-editor.menus.countdown-blocks"),
    MAP_EDITOR_LIST_EDITOR("map-editor.menus.list-editor"),
    MAP_EDITOR_STEP_LIST("map-editor.menus.step-list"),

    // Map editor — game-specific sub-menus
    ACE_RACE_EQUIPMENT("map-editor.games.ace-race.menus.equipment"),
    ACE_RACE_RESPAWN_BINDING("map-editor.games.ace-race.menus.respawn-binding"),
    TGTTOS_AREA_TYPE("map-editor.games.tgttos.menus.area-type"),
    BUILD_MART_MATERIAL_ZONES("map-editor.games.build-mart.menus.material-zones"),

    // Team management — hardcoded sub-screens
    TEAMS_ADD_PLAYER("teams.menus.add-player"),
    TEAMS_COLOR_PICKER("teams.menus.color-picker"),
    TEAMS_QUICK_ASSIGN("teams.menus.quick-assign"),
    TEAMS_TARGET_TEAM("teams.menus.target-team"),
    TEAMS_KNOWN_PLAYER("teams.menus.known-player"),
    TEAMS_CONFIRM("teams.menus.confirm"),

    // Bingo (core side only; the worker renders from the match manifest, not gui.yml)
    BINGO_TEAMMATE_TELEPORT("games.bingo.menus.teammate-teleport"),
    BINGO_CARD("games.bingo.menus.card");

    private final String path;

    MenuId(@NotNull String path) {
        this.path = path;
    }

    /** Absolute configuration prefix, e.g. {@code spectator.menus.venue-selector}. */
    public @NotNull String path() {
        return path;
    }

    /** Path of a fixed/configured item, e.g. {@code <menu>.items.close}. */
    public @NotNull String item(@NotNull String item) {
        return path + ".items." + item;
    }

    /** Path of a localised text entry inside this menu's {@code copy} block. */
    public @NotNull String copy(@NotNull String key) {
        return path + ".copy." + key;
    }

    /** Path of a value under this menu's {@code layout} block. */
    public @NotNull String layout(@NotNull String key) {
        return path + ".layout." + key;
    }
}
