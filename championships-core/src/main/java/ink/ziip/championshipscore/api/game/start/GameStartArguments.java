package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Shared command grammar for a manual game and an optional formal-event override. */
public record GameStartArguments(
        GameTypeEnum game, String map, List<String> teams, ArenaSelection arenas) {
    public GameStartArguments {
        teams = List.copyOf(teams);
    }

    public boolean allTeams() {
        return teams.isEmpty();
    }

    public static GameStartArguments parse(String[] raw, boolean event) {
        List<String> tokens = quotedTokens(raw);
        List<String> positional = new ArrayList<>();
        ArenaSelection selection = ArenaSelection.all();
        boolean arenaSpecified = false;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("--arena") || token.startsWith("--arena=")) {
                if (arenaSpecified)
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("game-start-arguments.parse-2"));
                arenaSpecified = true;
                String value;
                if (token.equals("--arena")) {
                    if (++i == tokens.size())
                        throw new IllegalArgumentException(
                                MessageConfig.codeText("game-start-arguments.parse-3"));
                    value = tokens.get(i);
                } else {
                    value = token.substring("--arena=".length());
                }
                selection = ArenaSelection.parse(value);
            } else if (token.startsWith("--")) {
                throw new IllegalArgumentException(
                        MessageConfig.codeText("game-start-arguments.parse-1", token));
            } else {
                positional.add(token);
            }
        }
        if (positional.isEmpty() || (!event && positional.size() < 2)) {
            throw new IllegalArgumentException(
                    MessageConfig.codeText("game-start-arguments.parse-4"));
        }
        GameTypeEnum game = GameTypeEnum.fromCommand(positional.getFirst());
        if (game == null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("game-start-arguments.parse-5", positional.getFirst()));
        String map =
                positional.size() < 2 || positional.get(1).equalsIgnoreCase("auto")
                        ? null
                        : positional.get(1);
        List<String> teams =
                new ArrayList<>(
                        positional.subList(Math.min(2, positional.size()), positional.size()));
        if (teams.size() == 1 && teams.getFirst().equalsIgnoreCase("all")) teams.clear();
        if (teams.stream().anyMatch(name -> name.equalsIgnoreCase("all"))) {
            throw new IllegalArgumentException(
                    MessageConfig.codeText("game-start-arguments.parse-6"));
        }
        if (new HashSet<>(teams.stream().map(name -> name.toLowerCase(Locale.ROOT)).toList()).size()
                != teams.size()) {
            throw new IllegalArgumentException(
                    MessageConfig.codeText("game-start-arguments.parse-7"));
        }
        return new GameStartArguments(game, map, teams, selection);
    }

    private static List<String> quotedTokens(String[] raw) {
        List<String> result = new ArrayList<>();
        StringBuilder quoted = null;
        char delimiter = 0;
        for (String token : raw) {
            if (quoted == null && (token.startsWith("\"") || token.startsWith("'"))) {
                delimiter = token.charAt(0);
                quoted = new StringBuilder();
                token = token.substring(1);
            } else if (quoted != null) {
                quoted.append(' ');
            }
            if (quoted == null) {
                result.add(token);
            } else if (token.endsWith(String.valueOf(delimiter))) {
                quoted.append(token, 0, token.length() - 1);
                if (quoted.isEmpty())
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("game-start-arguments.quoted-tokens-1"));
                result.add(quoted.toString());
                quoted = null;
            } else {
                quoted.append(token);
            }
        }
        if (quoted != null)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("game-start-arguments.quoted-tokens-2"));
        return result;
    }
}
