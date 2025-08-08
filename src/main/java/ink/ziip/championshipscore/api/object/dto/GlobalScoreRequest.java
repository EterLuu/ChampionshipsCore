package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class GlobalScoreRequest {
    private String team;
    private int total_score;
    private List<PlayerScore> scores;

    public GlobalScoreRequest() {
    }

    public GlobalScoreRequest(String team, int total_score, List<PlayerScore> scores) {
        this.team = team;
        this.total_score = total_score;
        this.scores = scores;
    }

    @Setter
    @Getter
    public static class PlayerScore {
        private String player;
        private int score;

        public PlayerScore() {
        }

        public PlayerScore(String player, int score) {
            this.player = player;
            this.score = score;
        }
    }
}