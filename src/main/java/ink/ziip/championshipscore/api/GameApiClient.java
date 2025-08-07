package ink.ziip.championshipscore.api;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.dto.*;
import com.google.gson.Gson;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class GameApiClient extends BaseManager {
    private final Gson gson;
    private final String baseUrl;

    public GameApiClient(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        this.gson = new Gson();
        this.baseUrl = CCConfig.LIVE_API;
    }

    @Override
    public void load() {
        plugin.getLogger().info("GameApiClient loaded");
    }

    @Override
    public void unload() {
        plugin.getLogger().info("GameApiClient unloaded");
    }

    public void sendGameEvent(GameTypeEnum gameTypeEnum, Player player, ChampionshipTeam championshipTeam, String event, String lore) {
        if (!CCConfig.LIVE_API_ENABLED)
            return;

        GameEventRequest request = new GameEventRequest(player.getName(), championshipTeam.getName(), event, lore);
        sendGameEvent(gameTypeEnum.toString(), request);
    }

    private CompletableFuture<Boolean> sendGameEvent(String gameId, GameEventRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/" + gameId + "/event";
                String json = gson.toJson(request);
                return sendPostRequest(url, json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send game event", e);
                return false;
            }
        });
    }

    public void sendInGameScore(GameTypeEnum gameTypeEnum, Map<ChampionshipTeam, Double> scores) {
        if (!CCConfig.LIVE_API_ENABLED)
            return;

        List<PlayerScoreRequest> requests = new ArrayList<>();
        for (Map.Entry<ChampionshipTeam, Double> entry : scores.entrySet()) {
            requests.add(new PlayerScoreRequest(entry.getKey().getName(), entry.getKey().getName(), entry.getValue().intValue()));
        }
        sendInGameScoreUpdate(gameTypeEnum.toString(), requests);
    }

    private CompletableFuture<Boolean> sendInGameScoreUpdate(String gameId, List<PlayerScoreRequest> requests) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/" + gameId + "/score";
                String json = gson.toJson(requests);
                return sendPostRequest(url, json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send in-game score update", e);
                return false;
            }
        });
    }

    public void sendGlobalScore(Map<ChampionshipTeam, Double> teamPoints, Map<UUID, Double> playerPoints) {
        if (!CCConfig.LIVE_API_ENABLED)
            return;

        List<GlobalScoreRequest> requests = new ArrayList<>();
        for (Map.Entry<ChampionshipTeam, Double> entry : teamPoints.entrySet()) {
            ChampionshipTeam team = entry.getKey();
            double totalScore = entry.getValue();
            List<GlobalScoreRequest.PlayerScore> playerScores = new ArrayList<>();
            for (UUID uuid : team.getMembers()) {
                double playerScore = playerPoints.getOrDefault(uuid, 0.0);
                playerScores.add(new GlobalScoreRequest.PlayerScore(plugin.getPlayerManager().getPlayerName(uuid), (int) playerScore));
            }
            requests.add(new GlobalScoreRequest(team.getName(), (int) totalScore, playerScores));
        }
        sendGlobalScoreUpdate(requests);
    }

    private CompletableFuture<Boolean> sendGlobalScoreUpdate(List<GlobalScoreRequest> requests) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/game/score";
                String json = gson.toJson(requests);
                return sendPostRequest(url, json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send global score update", e);
                return false;
            }
        });
    }

    public void sendGlobalEvent(String status, GameTypeEnum gameTypeEnum, int round) {
        if (!CCConfig.LIVE_API_ENABLED)
            return;

        GlobalEventRequest.GameInfo gameInfo = new GlobalEventRequest.GameInfo(gameTypeEnum.toString(), round);
        GlobalEventRequest request = new GlobalEventRequest(status, gameInfo);
        sendGlobalEvent(request);
    }

    private CompletableFuture<Boolean> sendGlobalEvent(GlobalEventRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/game/event";
                String json = gson.toJson(request);
                return sendPostRequest(url, json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send global event", e);
                return false;
            }
        });
    }

    public void sendVoteEvent(Map<GameTypeEnum, Integer> votes) {
        if (!CCConfig.LIVE_API_ENABLED)
            return;

        List<VoteEventRequest> requests = new ArrayList<>();
        for (Map.Entry<GameTypeEnum, Integer> entry : votes.entrySet()) {
            GameTypeEnum gameType = entry.getKey();
            int voteCount = entry.getValue();
            requests.add(new VoteEventRequest(gameType.toString(), voteCount));
        }
        sendVoteEvent(requests);
    }

    private CompletableFuture<Boolean> sendVoteEvent(List<VoteEventRequest> requests) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/vote/event";
                String json = gson.toJson(requests);
                return sendPostRequest(url, json);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send vote event", e);
                return false;
            }
        });
    }

    private boolean sendPostRequest(String urlString, String jsonInputString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        connection.disconnect();

        return responseCode >= 200 && responseCode < 300;
    }

    public CompletableFuture<Boolean> getPlayerClientVerifyStatus(String playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = CCConfig.CLIENT_VERIFY_API + "/api/v1/race-safe/client/user/" + playerId + "/status";
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("Failed to verify player client status for playerId: " + playerId + ", response code: " + responseCode);
                    connection.disconnect();
                    return false;
                }

                String msg = "";
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    msg += line + "\n";
                }
                reader.close();

                PlayerClientVerifyResponse playerClientVerifyResponse = gson.fromJson(msg, PlayerClientVerifyResponse.class);
//                LocalDateTime checkTime = LocalDateTime.parse(playerClientVerifyResponse.getLast_check());
//                LocalDateTime nowTime = LocalDateTime.now(ZoneId.of("UTC"));
//                if (!checkTime.isAfter(nowTime.minusSeconds(3))) {
//                    return false;
//                }
                connection.disconnect();

                return playerClientVerifyResponse.isVerified();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to verify player client status", e);
                return false;
            }
        });
    }
}