package ink.ziip.championshipscore.api.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Mutable only during preparation; owns the frozen 16-team by four-seat test roster. */
public final class TestRoster {
    public static final int TEAM_COUNT = 16;
    public static final int SEATS_PER_TEAM = 4;
    public static final int TOTAL_SEATS = TEAM_COUNT * SEATS_PER_TEAM;

    public enum OccupantType {
        BOT,
        DEVELOPER
    }

    public record SeatSnapshot(
            int teamNumber,
            int seat,
            String botUsername,
            OccupantType occupantType,
            UUID occupantUuid,
            String occupantName) {}

    private final List<List<Seat>> teams;
    private boolean frozen;

    private TestRoster(List<List<Seat>> teams) {
        this.teams = teams;
    }

    public static TestRoster fromBotUsernames(List<String> botUsernames) {
        Objects.requireNonNull(botUsernames, "botUsernames");
        if (botUsernames.size() != TOTAL_SEATS)
            throw new IllegalArgumentException("test roster must contain exactly 64 bot usernames");
        List<List<String>> grouped = new ArrayList<>();
        for (int offset = 0; offset < botUsernames.size(); offset += SEATS_PER_TEAM)
            grouped.add(List.copyOf(botUsernames.subList(offset, offset + SEATS_PER_TEAM)));
        return fromTeams(grouped);
    }

    public static TestRoster fromTeams(List<? extends List<String>> botUsernamesByTeam) {
        Objects.requireNonNull(botUsernamesByTeam, "botUsernamesByTeam");
        if (botUsernamesByTeam.size() != TEAM_COUNT)
            throw new IllegalArgumentException("test roster must contain exactly 16 teams");
        Set<String> names = new HashSet<>();
        List<List<Seat>> teams = new ArrayList<>();
        for (int teamIndex = 0; teamIndex < TEAM_COUNT; teamIndex++) {
            List<String> usernames = Objects.requireNonNull(botUsernamesByTeam.get(teamIndex));
            if (usernames.size() != SEATS_PER_TEAM)
                throw new IllegalArgumentException("every test team must contain exactly 4 seats");
            List<Seat> seats = new ArrayList<>();
            for (int seat = 0; seat < SEATS_PER_TEAM; seat++) {
                String username = normalizeUsername(usernames.get(seat));
                if (!names.add(username))
                    throw new IllegalArgumentException("duplicate bot username");
                seats.add(new Seat(teamIndex + 1, seat, username));
            }
            teams.add(List.copyOf(seats));
        }
        return new TestRoster(List.copyOf(teams));
    }

    public synchronized void bindBot(String botUsername, UUID observedUuid) {
        Objects.requireNonNull(observedUuid, "observedUuid");
        Seat seat = seatForBot(botUsername);
        if (seat.type != OccupantType.BOT) throw new IllegalStateException("bot seat was replaced");
        // A frozen roster may still receive the same Bot after a reconnect.  It must not be
        // possible to change a frozen seat or introduce a new UUID at that point.
        if (frozen) {
            if (!observedUuid.equals(seat.occupantUuid))
                throw new IllegalStateException("frozen bot seat UUID mismatch");
            return;
        }
        ensureUuidAvailable(observedUuid, seat);
        seat.occupantUuid = observedUuid;
        seat.occupantName = seat.botUsername;
    }

    public synchronized String replaceWithDeveloper(int teamNumber, UUID uuid, String name) {
        ensureMutable();
        Objects.requireNonNull(uuid, "uuid");
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("developer name must not be blank");
        List<Seat> team = team(teamNumber);
        ensureUuidAvailable(uuid, null);
        Seat target =
                team.stream()
                        .filter(seat -> seat.type == OccupantType.BOT)
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalStateException("team has no bot seat to replace"));
        target.type = OccupantType.DEVELOPER;
        target.occupantUuid = uuid;
        target.occupantName = name;
        return target.botUsername;
    }

    /** Removes the developer from a preparation seat and makes that seat available to its Bot. */
    public synchronized String removeDeveloper(int teamNumber, UUID uuid) {
        ensureMutable();
        Objects.requireNonNull(uuid, "uuid");
        for (Seat seat : team(teamNumber)) {
            if (seat.type != OccupantType.DEVELOPER || !uuid.equals(seat.occupantUuid)) continue;
            seat.type = OccupantType.BOT;
            seat.occupantUuid = null;
            seat.occupantName = null;
            return seat.botUsername;
        }
        throw new IllegalStateException("developer is not occupying team " + teamNumber);
    }

    public synchronized boolean isBotUsername(String username) {
        String normalized = normalizeUsername(username);
        return teams.stream()
                .flatMap(List::stream)
                .anyMatch(seat -> seat.botUsername.equals(normalized));
    }

    /** A preparation seat must not remain ready after its authenticated connection leaves. */
    public synchronized void unbindBot(String username, UUID observedUuid) {
        ensureMutable();
        Seat seat = seatForBot(username);
        if (seat.type != OccupantType.BOT || !Objects.equals(observedUuid, seat.occupantUuid))
            return;
        seat.occupantUuid = null;
        seat.occupantName = null;
    }

    public synchronized void freeze() {
        if (!isComplete())
            throw new IllegalStateException("cannot freeze an incomplete test roster");
        frozen = true;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    public synchronized boolean isComplete() {
        Set<UUID> members = new HashSet<>();
        for (List<Seat> team : teams)
            for (Seat seat : team)
                if (seat.occupantUuid == null || !members.add(seat.occupantUuid)) return false;
        return true;
    }

    public synchronized List<UUID> teamMembers(int teamNumber) {
        return team(teamNumber).stream()
                .map(seat -> seat.occupantUuid)
                .filter(Objects::nonNull)
                .toList();
    }

    public synchronized List<UUID> allMembers() {
        return teams.stream()
                .flatMap(List::stream)
                .map(seat -> seat.occupantUuid)
                .filter(Objects::nonNull)
                .toList();
    }

    public synchronized List<SeatSnapshot> snapshot() {
        return teams.stream().flatMap(List::stream).map(Seat::snapshot).toList();
    }

    private List<Seat> team(int teamNumber) {
        if (teamNumber < 1 || teamNumber > TEAM_COUNT)
            throw new IllegalArgumentException("team number must be between 1 and 16");
        return teams.get(teamNumber - 1);
    }

    private Seat seatForBot(String botUsername) {
        String normalized = normalizeUsername(botUsername);
        return teams.stream()
                .flatMap(List::stream)
                .filter(seat -> seat.botUsername.equals(normalized))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("unknown bot username: " + botUsername));
    }

    private void ensureUuidAvailable(UUID uuid, Seat ignored) {
        for (List<Seat> team : teams)
            for (Seat seat : team)
                if (seat != ignored && uuid.equals(seat.occupantUuid))
                    throw new IllegalStateException(
                            "UUID already occupies the test roster: " + uuid);
    }

    private void ensureMutable() {
        if (frozen) throw new IllegalStateException("test roster is frozen");
    }

    private static String normalizeUsername(String username) {
        Objects.requireNonNull(username, "username");
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,16}"))
            throw new IllegalArgumentException("invalid Minecraft username: " + username);
        return normalized;
    }

    private static final class Seat {
        private final int teamNumber;
        private final int seat;
        private final String botUsername;
        private OccupantType type = OccupantType.BOT;
        private UUID occupantUuid;
        private String occupantName;

        private Seat(int teamNumber, int seat, String botUsername) {
            this.teamNumber = teamNumber;
            this.seat = seat;
            this.botUsername = botUsername;
        }

        private SeatSnapshot snapshot() {
            return new SeatSnapshot(
                    teamNumber, seat, botUsername, type, occupantUuid, occupantName);
        }
    }
}
