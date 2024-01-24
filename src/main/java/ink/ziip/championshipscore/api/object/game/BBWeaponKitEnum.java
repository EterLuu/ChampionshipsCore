package ink.ziip.championshipscore.api.object.game;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.Random;

public enum BBWeaponKitEnum {
    PUNCH, KNOCK_BACK, JUMP, PULL;

    public static BBWeaponKitEnum getRandomEnum() {
        Random random = new Random();
        return values()[random.nextInt(values().length)];
    }

    @Override
    public String toString() {
        if (this == PUNCH)
            return MessageConfig.BATTLE_BOX_KITS_PUNCH;
        if (this == KNOCK_BACK)
            return MessageConfig.BATTLE_BOX_KITS_KNOCK_BACK;
        if (this == JUMP)
            return MessageConfig.BATTLE_BOX_KITS_JUMP;
        if (this == PULL)
            return MessageConfig.BATTLE_BOX_KITS_PULL;
        return "Unknown";
    }
}
