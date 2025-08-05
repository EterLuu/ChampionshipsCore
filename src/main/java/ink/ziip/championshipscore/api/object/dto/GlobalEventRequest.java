package ink.ziip.championshipscore.api.object.dto;

public class GlobalEventRequest {
    private String status;
    private GameInfo game;

    public GlobalEventRequest() {}

    public GlobalEventRequest(String status, GameInfo game) {
        this.status = status;
        this.game = game;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public GameInfo getGame() {
        return game;
    }

    public void setGame(GameInfo game) {
        this.game = game;
    }

    public static class GameInfo {
        private String name;
        private int round;

        public GameInfo() {}

        public GameInfo(String name, int round) {
            this.name = name;
            this.round = round;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getRound() {
            return round;
        }

        public void setRound(int round) {
            this.round = round;
        }
    }
}