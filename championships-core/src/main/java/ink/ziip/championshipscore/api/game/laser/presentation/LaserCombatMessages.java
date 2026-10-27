package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.entity.Player;

import java.util.Map;

/** Both laser games use the same configured combat feedback and one-pass interpolation. */
public final class LaserCombatMessages {
    private LaserCombatMessages() {}

    public static String elimination(
            LaserGun.HitRegion region, String attacker, String victim, String weapon) {
        return MessageTemplate.render(
                LaserGunItems.text(
                        region == LaserGun.HitRegion.HEAD
                                ? MessageConfig.LASER_BOX_HEADSHOT
                                : MessageConfig.LASER_BOX_HIT),
                Map.of("%attacker%", attacker, "%victim%", victim, "%gun%", weapon));
    }

    public static void showRespawn(Player player, int remainingTicks) {
        player.sendTitle(
                LegacyText.translateColorCodes(
                        LaserGunItems.text(MessageConfig.LASER_BOX_RESPAWN_TITLE)),
                LegacyText.translateColorCodes(
                        MessageTemplate.render(
                                LaserGunItems.text(MessageConfig.LASER_BOX_RESPAWN_SUBTITLE),
                                Map.of("%seconds%", Integer.toString((remainingTicks + 19) / 20)))),
                0,
                25,
                0);
    }

    public static void showReloadComplete(Player player, LaserGun gun) {
        player.sendActionBar(
                LegacyText.component(
                        MessageTemplate.render(
                                LaserGunItems.text(MessageConfig.LASER_BOX_RELOAD_COMPLETE),
                                Map.of("%gun%", LaserGunItems.gunName(gun)))));
    }

    public static Component itemHint(String type) {
        String message =
                switch (type) {
                    case "grenade" -> MessageConfig.LASER_BOX_ITEM_GRENADE;
                    case "strike" -> MessageConfig.LASER_BOX_ITEM_STRIKE;
                    case "reveal" -> MessageConfig.LASER_BOX_ITEM_REVEAL;
                    default -> MessageConfig.LASER_BOX_ITEM_SHIELD;
                };
        return LegacyText.component(LaserGunItems.text(message))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
