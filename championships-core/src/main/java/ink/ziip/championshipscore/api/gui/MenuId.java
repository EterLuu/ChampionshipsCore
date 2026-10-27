package ink.ziip.championshipscore.api.gui;

import org.jetbrains.annotations.NotNull;

/**
 * Single source of truth for every GUI screen defined under gui.yml.
 *
 * <p>Each constant owns the absolute configuration prefix of its menu. Derived paths such as {@code
 * <menu>.items.<item>} are built through helper methods so callers never concatenate raw strings by
 * hand.
 */
public enum MenuId {
    // Spectator
    SPECTATOR_VENUE_SELECTOR("spectator.menus.venue-selector", true),
    SPECTATOR_SUB_ARENA_SELECTOR("spectator.menus.sub-arena-selector", true),
    SPECTATOR_BUILD_MART_SELECTOR("spectator.menus.build-mart-selector", true),
    SPECTATOR_PLAYER_TELEPORT("spectator.menus.player-teleport-selector", true),

    // Team management
    TEAMS_OVERVIEW("teams.menus.overview", true),
    TEAMS_MEMBERS("teams.menus.members", true),

    // Free-play lobby
    DAILY_LOBBY("daily.menus.lobby-screen"),
    DAILY_BINGO_VOTE("daily.menus.bingo-vote"),
    DAILY_BINGO_VOTE_MODE("daily.menus.bingo-vote.screens.mode", true, "daily.menus.bingo-vote"),
    DAILY_BINGO_VOTE_DIFFICULTY(
            "daily.menus.bingo-vote.screens.difficulty", true, "daily.menus.bingo-vote"),
    DAILY_BINGO_VOTE_LINES("daily.menus.bingo-vote.screens.lines", true, "daily.menus.bingo-vote"),
    DAILY_BINGO_VOTE_GENESIS(
            "daily.menus.bingo-vote.screens.genesis", true, "daily.menus.bingo-vote"),
    DAILY_GAME_SELECTION("daily.menus.game-selection-screen", true),
    DAILY_LEADERBOARD("daily.menus.leaderboard-screen"),
    DAILY_PARTY("daily.menus.party-screen", true),
    DAILY_STATISTICS("daily.menus.statistics-screen"),
    DAILY_STATISTICS_OVERVIEW(
            "daily.menus.statistics-screen.screens.overview",
            true,
            "daily.menus.statistics-screen"),
    DAILY_STATISTICS_DETAIL(
            "daily.menus.statistics-screen.screens.detail", true, "daily.menus.statistics-screen"),
    DAILY_LEADERBOARD_OVERVIEW(
            "daily.menus.leaderboard-screen.screens.overview",
            true,
            "daily.menus.leaderboard-screen"),
    DAILY_LEADERBOARD_DETAIL(
            "daily.menus.leaderboard-screen.screens.detail",
            true,
            "daily.menus.leaderboard-screen"),

    // Championship voting
    VOTING_BALLOT("voting.menus.ballot", true),

    // Shared laser games
    LASER_GUN_SELECTION("laser.menus.gun-selection", true),

    // Map editor
    MAP_EDITOR_PREPARE_TOOLBAR("map-editor.menus.prepare-toolbar"),
    MAP_EDITOR_INPUT("map-editor.menus.input"),
    MAP_EDITOR_AREA_LIST("map-editor.menus.area-list", true),
    MAP_EDITOR_COUNTDOWN_BLOCKS("map-editor.menus.countdown-blocks"),
    MAP_EDITOR_LIST_EDITOR("map-editor.menus.list-editor"),
    MAP_EDITOR_LIST_MANAGE(
            "map-editor.menus.list-editor.screens.manage", false, "map-editor.menus.list-editor"),
    MAP_EDITOR_LIST_ENTRIES(
            "map-editor.menus.list-editor.screens.entries", true, "map-editor.menus.list-editor"),
    MAP_EDITOR_LIST_ENTRY_EDIT(
            "map-editor.menus.list-editor.screens.edit", false, "map-editor.menus.list-editor"),
    MAP_EDITOR_STEP_LIST("map-editor.menus.step-list", true),

    // Map editor — game-specific sub-menus
    ACE_RACE_EQUIPMENT("map-editor.games.ace-race.menus.equipment"),
    ACE_RACE_RESPAWN_BINDING("map-editor.games.ace-race.menus.respawn-binding", true),
    RIPTIDE_RUSH_EDITOR("map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_STOPPED(
            "map-editor.games.riptide-rush.menus.course-editor.stopped",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_SIDE(
            "map-editor.games.riptide-rush.menus.course-editor.side",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_CATEGORY(
            "map-editor.games.riptide-rush.menus.course-editor.category",
            true,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_SIDE_POOL(
            "map-editor.games.riptide-rush.menus.course-editor.side-pool",
            true,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_ENTRY(
            "map-editor.games.riptide-rush.menus.course-editor.entry",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_ADD(
            "map-editor.games.riptide-rush.menus.course-editor.add",
            true,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_CHOICE(
            "map-editor.games.riptide-rush.menus.course-editor.choice",
            true,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_WORKSHOP(
            "map-editor.games.riptide-rush.menus.course-editor.workshop",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_ABANDON(
            "map-editor.games.riptide-rush.menus.course-editor.abandon",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_DELETE(
            "map-editor.games.riptide-rush.menus.course-editor.delete",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_COURSE(
            "map-editor.games.riptide-rush.menus.course-editor.course",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_PREVIEW(
            "map-editor.games.riptide-rush.menus.course-editor.preview",
            true,
            "map-editor.games.riptide-rush.menus.course-editor"),
    RIPTIDE_PLACEMENT(
            "map-editor.games.riptide-rush.menus.course-editor.placement",
            false,
            "map-editor.games.riptide-rush.menus.course-editor"),
    TGTTOS_AREA_TYPE("map-editor.games.tgttos.menus.area-type", true),
    BUILD_MART_MATERIAL_ZONES("map-editor.games.build-mart.menus.material-zones", true),
    BUILD_MART_BLUEPRINTS("map-editor.games.build-mart.menus.blueprints", true),

    // Team management — sub-screens
    TEAMS_ADD_PLAYER("teams.menus.add-player", true),
    TEAMS_COLOR_PICKER("teams.menus.color-picker", true),
    TEAMS_QUICK_ASSIGN("teams.menus.quick-assign", true),
    TEAMS_TARGET_TEAM("teams.menus.target-team", true),
    TEAMS_KNOWN_PLAYER("teams.menus.known-player", true),
    TEAMS_CONFIRM("teams.menus.confirm"),
    TEAMS_INPUT("teams.menus.input"),

    // Bingo (core side only; the worker renders from the match manifest, not gui.yml)
    BINGO_TEAMMATE_TELEPORT("games.bingo.menus.teammate-teleport", true),
    BINGO_CARD("games.bingo.menus.card"),
    BINGO_CARD_X3("games.bingo.menus.card.screens.x3", false, "games.bingo.menus.card"),
    BINGO_CARD_X4("games.bingo.menus.card.screens.x4", false, "games.bingo.menus.card"),
    BINGO_CARD_X5("games.bingo.menus.card.screens.x5", false, "games.bingo.menus.card");

    private final String path;
    private final boolean paged;
    private final String itemFamily;

    MenuId(@NotNull String path) {
        this(path, false);
    }

    MenuId(@NotNull String path, boolean paged) {
        this(path, paged, path);
    }

    MenuId(String path, boolean paged, String itemFamily) {
        this.path = path;
        this.paged = paged;
        this.itemFamily = itemFamily;
    }

    public static boolean isPagedMenu(String path) {
        for (MenuId id : values()) if (id.path.equals(path)) return id.paged;
        return false;
    }

    /** These renderers route clicks through the visible placement of a partial page. */
    public static boolean supportsContentArrangements(String path) {
        return java.util.Set.of(
                        TEAMS_OVERVIEW.path,
                        TEAMS_MEMBERS.path,
                        TEAMS_ADD_PLAYER.path,
                        TEAMS_KNOWN_PLAYER.path,
                        TEAMS_QUICK_ASSIGN.path,
                        TEAMS_TARGET_TEAM.path,
                        TEAMS_COLOR_PICKER.path,
                        DAILY_STATISTICS_OVERVIEW.path,
                        DAILY_STATISTICS_DETAIL.path,
                        DAILY_LEADERBOARD_OVERVIEW.path,
                        DAILY_LEADERBOARD_DETAIL.path,
                        RIPTIDE_CHOICE.path,
                        BINGO_TEAMMATE_TELEPORT.path,
                        SPECTATOR_PLAYER_TELEPORT.path,
                        SPECTATOR_VENUE_SELECTOR.path,
                        SPECTATOR_SUB_ARENA_SELECTOR.path,
                        SPECTATOR_BUILD_MART_SELECTOR.path)
                .contains(path);
    }

    public static String itemFamilyFor(String path) {
        for (MenuId id : values()) if (id.path.equals(path)) return id.itemFamily;
        return path;
    }

    /** The member list renders Back only on page zero and Previous only on later pages. */
    public static boolean mutuallyExclusiveControls(String path, String first, String second) {
        var pair = java.util.Set.of(first, second);
        if (path.equals(TEAMS_MEMBERS.path))
            return pair.equals(java.util.Set.of("back", "previous"));
        if (path.equals(RIPTIDE_CATEGORY.path))
            return java.util.Set.of("quota", "stopped-child-weight").containsAll(pair)
                    || java.util.Set.of("minimum", "floor-timing").containsAll(pair);
        if (path.equals(RIPTIDE_ADD.path))
            return java.util.Set.of("new-rhythm", "new-dodge", "blank-building").containsAll(pair);
        if (path.equals(RIPTIDE_WORKSHOP.path))
            return java.util.Set.of(
                            "floor-building-help", "math-building-help", "pass-building-help")
                    .containsAll(pair);
        return false;
    }

    /** Absolute configuration prefix, e.g. {@code spectator.menus.venue-selector}. */
    public @NotNull String path() {
        return path;
    }

    /** Path of a fixed/configured item, e.g. {@code <menu>.items.close}. */
    public @NotNull String item(@NotNull String item) {
        return path + ".items." + item;
    }

    /** Path of a value under this menu's {@code layout} block. */
    public @NotNull String layout(@NotNull String key) {
        return path + ".layout." + key;
    }
}
