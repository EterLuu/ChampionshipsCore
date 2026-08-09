package ink.ziip.championshipscore.api.object.game.battlebox;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.concurrent.ThreadLocalRandom;

public enum BBWeaponKitEnum {
    ARMOR, SPEED, HEAL, PULL;

    public static BBWeaponKitEnum getRandomEnum() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }

    @Override
    public String toString() {
        if (this == ARMOR)
            return MessageConfig.BATTLE_BOX_KITS_ARMOR;
        if (this == SPEED)
            return MessageConfig.BATTLE_BOX_KITS_SPEED;
        if (this == HEAL)
            return MessageConfig.BATTLE_BOX_KITS_HEAL;
        if (this == PULL)
            return MessageConfig.BATTLE_BOX_KITS_PULL;
        return "Unknown";
    }
}
