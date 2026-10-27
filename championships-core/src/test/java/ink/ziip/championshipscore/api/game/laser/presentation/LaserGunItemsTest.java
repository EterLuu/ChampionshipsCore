package ink.ziip.championshipscore.api.game.laser.presentation;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class LaserGunItemsTest {
    @Test
    void weaponTextInheritsBundledLanguageAndPreservesExplicitEmptyOverrides() throws Exception {
        var bundled =
                YamlConfiguration.loadConfiguration(
                        Path.of("src/main/resources/message.yml").toFile());
        var config = new MessageConfig(allocate(ChampionshipsCore.class));
        config.loadFromConfiguration(bundled);
        set(config, "bundledDefaults", bundled);
        var overrides = new YamlConfiguration();
        overrides.set("laserbox.guns.sniper.name", "");
        overrides.set("laserbox.guns.rifle.name", "&aCustom %ammo% $\\");
        config.loadFromConfiguration(overrides);
        assertEquals("", LaserGunItems.gunName(LaserGun.SNIPER));
        assertEquals(
                "Custom %ammo% $\\", LegacyText.plainText(LaserGunItems.gunName(LaserGun.RIFLE)));
        assertEquals(
                LegacyText.plainText(bundled.getString("laserbox.guns.smg.name")),
                LegacyText.plainText(LaserGunItems.gunName(LaserGun.SMG)));
        overrides.set("laserbox.guns.sniper.name", null);
        config.loadFromConfiguration(overrides);
        assertEquals(
                LegacyText.plainText(bundled.getString("laserbox.guns.sniper.name")),
                LegacyText.plainText(LaserGunItems.gunName(LaserGun.SNIPER)));
        for (LaserGun gun : LaserGun.values()) {
            var values = LaserGunItems.presentation(gun);
            assertFalse(values.get("description").isBlank());
            assertFalse(values.get("controls").isBlank());
            assertTrue(values.get("magazine-summary").contains(Integer.toString(gun.magazine())));
            assertFalse(values.get("magazine-summary").contains("%seconds%"));
            assertFalse(values.get("fire-rate-summary").contains("%rate%"));
        }
    }

    @Test
    void combatHudDoesNotInterpretTokensInsideConfiguredWeaponNames() {
        MessageConfig.LASER_BOX_GUN_RIFLE_NAME = "Rifle %ammo% $\\";
        MessageConfig.LASER_BOX_AMMO = "%gun% [%ammo%/%magazine%]";
        MessageConfig.LASER_BOX_RELOAD = "%gun% [%seconds%]";
        List<String> sent = new ArrayList<>();
        Player player =
                proxy(
                        Player.class,
                        (p, m, a) -> {
                            if (!m.getName().equals("sendActionBar"))
                                throw new AssertionError(m.getName());
                            sent.add(LegacyText.plainText(LegacyText.serialize((Component) a[0])));
                            return null;
                        });
        LaserGunItems.showCombatStatus(player, LaserGun.RIFLE, 7, 0, 0);
        LaserGunItems.showCombatStatus(player, LaserGun.RIFLE, 7, 30, 0);
        LaserGunItems.showCombatStatus(player, null, 0, 0, 0);
        assertEquals(List.of("Rifle %ammo% $\\ [7/12]", "Rifle %ammo% $\\ [1.5]"), sent);
    }
}
