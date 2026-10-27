package ink.ziip.championshipscore.api.game.laser.presentation;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class LaserCombatMessagesTest {
    private static MessageConfig config() throws Exception {
        var config = new MessageConfig(allocate(ChampionshipsCore.class));
        var defaults =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/message.yml").toFile());
        set(config, "bundledDefaults", defaults);
        config.loadFromConfiguration(defaults);
        return config;
    }

    @Test
    void combatParametersAreInsertedOnceAndReloadUsesCurrentLanguage() throws Exception {
        var config = config();
        var override = new YamlConfiguration();
        override.set("laserbox.hit", "%attacker% -> %victim% [%gun%]");
        override.set("laserbox.headshot", "HEAD %attacker% -> %victim% [%gun%]");
        override.set("laserbox.reload-complete", "Reload %gun%");
        override.set("laserbox.guns.rifle.name", "Rifle %gun% $\\");
        config.loadFromConfiguration(override);
        String expected = "A %victim% -> V %gun% [G %attacker% $\\]";
        assertEquals(
                expected,
                LaserCombatMessages.elimination(
                        LaserGun.HitRegion.BODY, "A %victim%", "V %gun%", "G %attacker% $\\"));
        assertEquals(
                "HEAD " + expected,
                LaserCombatMessages.elimination(
                        LaserGun.HitRegion.HEAD, "A %victim%", "V %gun%", "G %attacker% $\\"));
        var sent = new ArrayList<String>();
        Player player =
                proxy(
                        Player.class,
                        (p, m, a) -> {
                            assertEquals("sendActionBar", m.getName());
                            sent.add(LegacyText.plainText(LegacyText.serialize((Component) a[0])));
                            return null;
                        });
        LaserCombatMessages.showReloadComplete(player, LaserGun.RIFLE);
        override.set("laserbox.reload-complete", "Changed %gun%");
        config.loadFromConfiguration(override);
        LaserCombatMessages.showReloadComplete(player, LaserGun.RIFLE);
        assertEquals(List.of("Reload Rifle %gun% $\\", "Changed Rifle %gun% $\\"), sent);
    }

    @Test
    void missingLanguageInheritsBundledValuesWhileEmptyLanguageStaysEmpty() throws Exception {
        var config = config();
        var overrides = new YamlConfiguration();
        String defaultHit = LaserCombatMessages.elimination(LaserGun.HitRegion.BODY, "A", "B", "G");
        Component defaultHint = LaserCombatMessages.itemHint("strike");
        assertFalse(defaultHit.isBlank());
        assertEquals(NamedTextColor.YELLOW, defaultHint.color());
        overrides.set("laserbox.hit", "");
        overrides.set("laserbox.headshot", "");
        overrides.set("laserbox.item-strike", "");
        config.loadFromConfiguration(overrides);
        for (LaserGun.HitRegion region : List.of(LaserGun.HitRegion.BODY, LaserGun.HitRegion.HEAD))
            assertEquals("", LaserCombatMessages.elimination(region, "A", "B", "G"));
        assertEquals("", LegacyText.serialize(LaserCombatMessages.itemHint("strike")));
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(
                defaultHit,
                LaserCombatMessages.elimination(LaserGun.HitRegion.BODY, "A", "B", "G"));
        assertEquals(defaultHint, LaserCombatMessages.itemHint("strike"));
        overrides.set("laserbox.item-strike", "Uncoloured");
        config.loadFromConfiguration(overrides);
        assertNull(LaserCombatMessages.itemHint("strike").color());
    }

    @Test
    void respawnFeedbackPreservesTickRoundingAndTitleDuration() throws Exception {
        config();
        MessageConfig.LASER_BOX_RESPAWN_TITLE = "&eWait";
        MessageConfig.LASER_BOX_RESPAWN_SUBTITLE = "&a%seconds% / %seconds%";
        var sent = new ArrayList<List<Object>>();
        Player player =
                proxy(
                        Player.class,
                        (p, m, a) -> {
                            assertEquals("sendTitle", m.getName());
                            sent.add(List.of(a));
                            return null;
                        });
        for (int ticks : List.of(1, 20, 21, 80)) LaserCombatMessages.showRespawn(player, ticks);
        assertEquals(4, sent.size());
        for (int i = 0; i < sent.size(); i++) {
            int seconds = List.of(1, 1, 2, 4).get(i);
            assertEquals(
                    List.of(
                            LegacyText.translateColorCodes("&eWait"),
                            LegacyText.translateColorCodes("&a" + seconds + " / " + seconds),
                            0,
                            25,
                            0),
                    sent.get(i));
        }
    }
}
