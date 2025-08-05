package ink.ziip.championshipscore.api.object.dto;

public class PlayerScoreRequest {
    private String player;
    private String team;
    private int score;

    public PlayerScoreRequest() {}

    public PlayerScoreRequest(String player, String team, int score) {
        this.player = player;
        this.team = team;
        this.score = score;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}