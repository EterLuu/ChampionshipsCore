package ink.ziip.championshipscore.api.object.dto;

public class VoteEventRequest {
    private String game;
    private int ticket;

    public VoteEventRequest() {}

    public VoteEventRequest(String game, int ticket) {
        this.game = game;
        this.ticket = ticket;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public int getTicket() {
        return ticket;
    }

    public void setTicket(int ticket) {
        this.ticket = ticket;
    }
}