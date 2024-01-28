package ink.ziip.championshipscore.api.object.stage;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public enum GameStageEnum {
    WAITING, LOADING, PREPARATION, PROGRESS, STOPPING, END;

    @Override
    public String toString() {
        if (this == WAITING)
            return MessageConfig.AREA_STATUS_WAITING;
        if (this == LOADING)
            return MessageConfig.AREA_STATUS_LOADING;
        if (this == PREPARATION)
            return MessageConfig.AREA_STATUS_PREPARATION;
        if (this == PROGRESS)
            return MessageConfig.AREA_STATUS_PROGRESS;
        if (this == STOPPING)
            return MessageConfig.AREA_STATUS_STOPPING;
        if (this == END)
            return MessageConfig.AREA_STATUS_END;
        return "Unknown";
    }
}
