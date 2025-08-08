package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
public class GlobalScoreRequest {
    private String team;
    private int total_score;
    private List<PlayerScore> scores;

    public GlobalScoreRequest() {
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