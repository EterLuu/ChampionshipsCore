package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.api.event.WebEventApiClient;
import ink.ziip.championshipscore.api.team.entry.TeamImportEntry;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class EventCommandSupport {
    private static final Map<String, String> TEAM_COLORS = createTeamColors();
    private EventCommandSupport() {}

    static @Nullable GameTypeEnum parse(@NotNull String raw) {
        return GameTypeEnum.fromCommand(raw);
    }

    static boolean canSchedule(@NotNull GameTypeEnum game) {
        return !FinaleGameRegistry.isRegistered(game)
                && ChampionshipsCore.getInstance().getScheduleManager().supportsFormalEvent(game);
    }

    static @NotNull List<String> enabledFormalGames() {
        List<String> names = new ArrayList<>();
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        EventStateStore.ActiveEvent active = new EventStateStore(plugin).load();
        if (active == null || active.archived()) return names;
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if (active.allows(game) && plugin.getGameManager().isGameEnabled(game) && canSchedule(game))
                names.add(game.commandName());
        }
        return names;
    }

    static @NotNull WebEventApiClient webClient() {
        return new WebEventApiClient(CCConfig.WEB_LEADERBOARD_SYNC_BASE_URL,
                CCConfig.WEB_LEADERBOARD_SYNC_KEY_ID, CCConfig.WEB_LEADERBOARD_SYNC_HMAC_SECRET,
                Boolean.TRUE.equals(CCConfig.WEB_LEADERBOARD_SYNC_ALLOW_INSECURE_PRIVATE_HTTP),
                CCConfig.WEB_LEADERBOARD_SYNC_CONNECT_TIMEOUT_SECONDS,
                CCConfig.WEB_LEADERBOARD_SYNC_REQUEST_TIMEOUT_SECONDS);
    }

    static @NotNull List<TeamImportEntry> validateImport(@NotNull EventTeamImport imported) {
        if (imported.event() == null || imported.event().slug() == null || imported.event().title() == null
                || !imported.event().slug().matches("[a-z0-9][a-z0-9-]{1,31}")
                || imported.event().title().isBlank())
            throw new IllegalArgumentException("赛事信息不完整");
        if (!"READY".equals(imported.event().lifecycleStatus()))
            throw new IllegalArgumentException("赛事尚未进入比赛就绪状态");
        if (imported.event().games() == null || imported.event().games().isEmpty()
                || imported.event().games().size() > 16)
            throw new IllegalArgumentException("赛事游戏列表为空");
        Set<GameTypeEnum> eventGames = new HashSet<>();
        for (EventTeamImport.Game configured : imported.event().games()) {
            GameTypeEnum game = configured == null || configured.key() == null
                    ? null : GameTypeEnum.fromCommand(configured.key());
            if (game == null || FinaleGameRegistry.isRegistered(game) || !eventGames.add(game))
                throw new IllegalArgumentException("赛事游戏无效或重复: " + (configured == null ? "null" : configured.key()));
            if (configured.variantKey() == null || !configured.variantKey().matches("[a-z0-9][a-z0-9-]{0,39}")
                    || configured.label() == null || configured.label().isBlank() || configured.label().length() > 80)
                throw new IllegalArgumentException("赛事游戏变体无效: " + configured.key());
        }
        if (imported.event().roundMultipliers() == null
                || imported.event().roundMultipliers().size() < eventGames.size())
            throw new IllegalArgumentException("轮次倍率数量不能少于赛事游戏数量");
        for (Double multiplier : imported.event().roundMultipliers()) {
            if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 0D || multiplier > 100D)
                throw new IllegalArgumentException("赛事轮次倍率无效");
        }
        if (imported.teams().isEmpty() || imported.teams().size() > TEAM_COLORS.size())
            throw new IllegalArgumentException("启用队伍数量必须在 1 到 16 之间");
        Set<String> names = new HashSet<>();
        Set<String> colors = new HashSet<>();
        Set<UUID> uuids = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        List<TeamImportEntry> teams = new ArrayList<>();
        for (EventTeamImport.Team team : imported.teams()) {
            String name = team.name().trim();
            String color = team.colorName().toLowerCase(Locale.ROOT);
            if (name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("队名无效: " + name);
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("队名重复: " + name);
            if (!colors.add(color)) throw new IllegalArgumentException("队伍颜色重复: " + color);
            String expectedHex = TEAM_COLORS.get(color);
            if (expectedHex == null || !expectedHex.equalsIgnoreCase(team.colorHex()))
                throw new IllegalArgumentException("队伍颜色不是固定羊毛色: " + color);
            if (team.members().isEmpty() || team.members().size() > CCConfig.TEAM_MAX_MEMBERS)
                throw new IllegalArgumentException("队伍人数不符合服务器限制: " + name);
            List<TeamImportEntry.Member> members = new ArrayList<>();
            for (EventTeamImport.Member member : team.members()) {
                if (!member.username().matches("[A-Za-z0-9_]{3,16}"))
                    throw new IllegalArgumentException("玩家名无效: " + member.username());
                UUID uuid;
                try {
                    uuid = UUID.fromString(member.uuid());
                } catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException("玩家 UUID 无效: " + member.username());
                }
                if (!uuids.add(uuid) || !usernames.add(member.username().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("玩家在阵容中重复: " + member.username());
                members.add(new TeamImportEntry.Member(uuid, member.username()));
            }
            teams.add(new TeamImportEntry(name, color, expectedHex, List.copyOf(members)));
        }
        return List.copyOf(teams);
    }

    private static Map<String, String> createTeamColors() {
        Map<String, String> colors = new HashMap<>();
        colors.put("white", "#F9FFFE"); colors.put("orange", "#F9801D");
        colors.put("magenta", "#C74EBD"); colors.put("light_blue", "#3AB3DA");
        colors.put("yellow", "#FED83D"); colors.put("lime", "#80C71F");
        colors.put("pink", "#F38BAA"); colors.put("gray", "#474F52");
        colors.put("light_gray", "#9D9D97"); colors.put("cyan", "#169C9C");
        colors.put("purple", "#8932B8"); colors.put("blue", "#3C44AA");
        colors.put("brown", "#835432"); colors.put("green", "#5E7C16");
        colors.put("red", "#B02E26"); colors.put("black", "#1D1D21");
        return Map.copyOf(colors);
    }
}
