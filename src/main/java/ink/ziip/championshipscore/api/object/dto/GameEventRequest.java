package ink.ziip.championshipscore.api.object.dto;

public class GameEventRequest {
    private String player;
    private String team;
    private String event;
    private String lore;

    public GameEventRequest() {}

    public GameEventRequest(String player, String team, String event, String lore) {
        this.player = player;
        this.team = team;
        this.event = event;
        this.lore = lore;
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

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getLore() {
        return lore;
    }

    public void setLore(String lore) {
        this.lore = lore;
    }
}