package ink.ziip.championshipscore.api.game.craftingfeast.config;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastOrderSequence;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class CraftingFeastConfigTest {
    @Test
    void bundledTemplateIsAnEmptyUnpublishedDraft() throws Exception {
        try (var stream = getClass().getResourceAsStream("/craftingfeast/area.yml")) {
            assertNotNull(stream);
            var yaml =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));
            var config = allocate(CraftingFeastConfig.class);
            assertEquals(CraftingFeastConfig.LATEST_VERSION, yaml.getInt("dont-edit-this.version"));
            assertEquals(CraftingFeastConfig.LATEST_VERSION, config.getLatestVersion());
            assertEquals("", yaml.getString("world-name"));
            assertFalse(yaml.getBoolean("prepare.published"));
            assertTrue(yaml.getBoolean("prepare.dirty"));
            assertFalse(yaml.getBoolean("prepare.world-built"));
            assertEquals(5, yaml.getInt("vip-every"));
            assertEquals(80, yaml.getInt("arrival-start-percent"));
            assertEquals(150, yaml.getInt("arrival-peak-percent"));
            assertEquals(120, yaml.getInt("arrival-peak-seconds"));
            assertEquals(30, yaml.getInt("delivery-wait-seconds"));
            assertTrue(yaml.getStringList("seats").isEmpty());
            assertTrue(yaml.getStringList("private-seats").isEmpty());
            assertTrue(yaml.getStringList("queue").isEmpty());
            assertTrue(yaml.getStringList("delivery-slots").isEmpty());
            assertTrue(yaml.getStringList("delivery-route").isEmpty());
            assertTrue(yaml.getConfigurationSection("resources").getKeys(false).isEmpty());
            assertTrue(yaml.getConfigurationSection("supplies").getKeys(false).isEmpty());
            assertTrue(yaml.getConfigurationSection("workstations").getKeys(false).isEmpty());
            assertTrue(yaml.getConfigurationSection("requests").getKeys(false).isEmpty());
            assertNull(config.getAreaPos1());
            assertNull(config.getAreaPos2());
            assertFalse(config.isPrepareReady());
            assertThrows(IllegalArgumentException.class, config::validate);
        }
    }

    @Test
    void parsesMenuCluesGuestProfilesAndRejectsMalformedRequests() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(
                """
                requests:
                  bread:
                    name: 面包
                    material: BREAD
                    amount: 1
                    points: 40
                    profession: farmer
                    biome: plains
                  cookie:
                    name: 曲奇
                    material: COOKIE
                    amount: 4
                    points: 45
                    profession: farmer
                    biome: jungle
                  clue_009:
                    name: 喝下就能解除中毒的饮品
                    answers: [MILK_BUCKET, HONEY_BOTTLE]
                    amount: 1
                    points: 100
                vip-guests:
                  royal_chef:
                    name: 御宴主厨
                    requests:
                      royal_chef_cake:
                        name: 庆典蛋糕
                        material: CAKE
                        amount: 1
                        points: 190
                        profession: farmer
                        biome: plains
                special-guests:
                  fox:
                    name: 森林旅狐
                    entity: FOX
                    bonus-percent: 30
                    requests:
                      fox_cooked_rabbit:
                        name: 烤兔肉
                        material: COOKED_RABBIT
                        amount: 1
                        points: 120
                """);
        var config = allocate(CraftingFeastConfig.class);
        set(config, "requests", yaml.getConfigurationSection("requests"));
        set(config, "vipGuests", yaml.getConfigurationSection("vip-guests"));
        set(config, "specialGuests", yaml.getConfigurationSection("special-guests"));
        set(config, "vipBonusPercent", 50);

        assertEquals(3, config.menu().size());
        assertEquals(1, config.vipGuestList().size());
        assertEquals(
                Set.of("FOX"),
                config.specialGuestList().stream()
                        .map(g -> g.entity())
                        .collect(Collectors.toSet()));
        assertEquals(
                config.allRequests().size(),
                config.allRequests().stream().map(r -> r.id()).distinct().count());
        var cookie =
                config.menu().stream()
                        .filter(r -> r.id().equals("cookie"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(Material.COOKIE, cookie.material());
        assertEquals(4, cookie.amount());
        var drink = config.menu().stream().filter(r -> r.clue()).findFirst().orElseThrow();
        assertEquals("喝下就能解除中毒的饮品", drink.name());
        assertNull(drink.material());
        assertEquals(Set.of(Material.MILK_BUCKET, Material.HONEY_BOTTLE), drink.answers());

        var sequence = new FeastOrderSequence(config.menu(), 410, 0);
        var tables = new HashSet<String>();
        var deliveries = new HashSet<String>();
        for (int i = 0; i < 3; i++) {
            tables.add(sequence.guest(i).request().id());
            deliveries.add(sequence.delivery(i).id());
        }
        assertEquals(Set.of("bread", "cookie", "clue_009"), tables);
        assertEquals(tables, deliveries);

        yaml.set("requests.clue_009.answers", List.of("MILK_BUCKET", "NOT_AN_ITEM"));
        assertThrows(IllegalArgumentException.class, config::menu);
        yaml.set("requests.clue_009.answers", List.of("MILK_BUCKET"));
        yaml.set("requests.clue_009.material", "MILK_BUCKET");
        assertThrows(IllegalArgumentException.class, config::menu);
        yaml.set("requests.clue_009.material", null);
        yaml.set("requests.bread.amount", 0);
        assertThrows(IllegalArgumentException.class, config::menu);
    }
}
