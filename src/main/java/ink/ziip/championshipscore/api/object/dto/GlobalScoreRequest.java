package ink.ziip.championshipscore.api.object.dto;

import java.util.List;

public class GlobalScoreRequest {
    private String team;
    private int totalScore;
    private List<PlayerScore> scores;

    public GlobalScoreRequest() {}

    public GlobalScoreRequest(String team, int totalScore, List<PlayerScore> scores) {
        this.team = team;
        this.totalScore = totalScore;
        this.scores = scores;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public List<PlayerScore> getScores() {
        return scores;
    }

    public void setScores(List<PlayerScore> scores) {
        this.scores = scores;
    }

    public static class PlayerScore {
        private String player;
        private int score;

        public PlayerScore() {}

        public PlayerScore(String player, int score) {
            this.player = player;
            this.score = score;
        }

        public String getPlayer() {
            return player;
        }

        public void setPlayer(String player) {
            this.player = player;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }
}