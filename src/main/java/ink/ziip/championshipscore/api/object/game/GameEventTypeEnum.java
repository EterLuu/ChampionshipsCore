package ink.ziip.championshipscore.api.object.game;

public enum GameEventTypeEnum {
    ITEM_FOUND,
    CHASER_SELECTED,
    ROUND_START,
    PLAYER_TAGGED,
    ROUND_OVER,
    KILL,
    WOOL_WIN,
    PLAYER_FALL,
    FALL,
    BORDER_START,
    BORDER_END,
    COD_PASSED,
    DEATH,
    CHECKPOINT,
    PLAYER_MISTAKE,
    PLAYER_FINISH;

    @Override
    public String toString() {
        switch (this) {
            case ITEM_FOUND:
                return "Item_Found";
            case CHASER_SELECTED:
                return "Chaser_Selected";
            case ROUND_START:
                return "Round_Start";
            case PLAYER_TAGGED:
                return "Player_Tagged";
            case ROUND_OVER:
                return "Round_Over";
            case KILL:
                return "Kill";
            case WOOL_WIN:
                return "Wool_Win";
            case PLAYER_FALL:
                return "Player_Fall";
            case FALL:
                return "Fall";
            case BORDER_START:
                return "Border_Start";
            case BORDER_END:
                return "Border_End";
            case COD_PASSED:
                return "Cod_Passed";
            case DEATH:
                return "Death";
            case CHECKPOINT:
                return "Checkpoint";
            case PLAYER_MISTAKE:
                return "Player_Mistake";
            case PLAYER_FINISH:
                return "Player_Finish";
            default:
                return "Unknown";
        }
    }
}