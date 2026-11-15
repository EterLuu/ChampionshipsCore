package ink.ziip.championshipscore.api.game.battlebox.model;

import ink.ziip.championshipscore.configuration.config.message.BattleBoxMessages;

import java.util.concurrent.ThreadLocalRandom;

public enum BBWeaponKitEnum {
    ARMOR,
    SPEED,
    HEAL,
    PULL;

    public static BBWeaponKitEnum getRandomEnum() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }

    @Override
    public String toString() {
        if (this == ARMOR) return BattleBoxMessages.BATTLE_BOX_KITS_ARMOR;
        if (this == SPEED) return BattleBoxMessages.BATTLE_BOX_KITS_SPEED;
        if (this == HEAL) return BattleBoxMessages.BATTLE_BOX_KITS_HEAL;
        if (this == PULL) return BattleBoxMessages.BATTLE_BOX_KITS_PULL;
        return "Unknown";
    }
}
