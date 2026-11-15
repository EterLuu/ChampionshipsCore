package ink.ziip.championshipscore.api.game.model;

import ink.ziip.championshipscore.configuration.config.message.GameMessages;

public enum GameStageEnum {
    WAITING,
    LOADING,
    PREPARATION,
    COUNTDOWN,
    PROGRESS,
    STOPPING,
    END;

    @Override
    public String toString() {
        if (this == WAITING) return GameMessages.AREA_STATUS_WAITING;
        if (this == LOADING) return GameMessages.AREA_STATUS_LOADING;
        if (this == PREPARATION) return GameMessages.AREA_STATUS_PREPARATION;
        if (this == COUNTDOWN) return GameMessages.AREA_STATUS_COUNTDOWN;
        if (this == PROGRESS) return GameMessages.AREA_STATUS_PROGRESS;
        if (this == STOPPING) return GameMessages.AREA_STATUS_STOPPING;
        if (this == END) return GameMessages.AREA_STATUS_END;
        return "Unknown";
    }
}
