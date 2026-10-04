package ink.ziip.championshipscore.platform.bukkit.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class ChampionshipTabTextTest {
    @Test
    void rendersTheSharedCoreAndWorkerFooterContract() {
        assertEquals(
                "§f队伍: §x§f§f§f§5§6§6金队 §f| 积分: 1235",
                ChampionshipTabText.teamFooter(
                        "&f队伍: %team% &f| 积分: %points%", "&#fff566金队", 1234.5D));
        assertEquals(
                "§f当前游戏: §b宾果时速",
                ChampionshipTabText.currentGameFooter("&f当前游戏: &b%game%", "宾果时速"));
        assertEquals(
                "§f队伍: §x§5§5§f§f§f§f青队",
                ChampionshipTabText.dailyTeamFooter("&f队伍: %team%", "&#55ffff青队"));
        assertEquals("§8[§x§f§f§f§5§6§6王牌竞速§8]§r ", ChampionshipTabText.gamePrefix("王牌竞速"));
    }

    @Test
    void colorsOnlyActivePlayersAndResetsEveryoneElse() {
        assertEquals("§x§f§f§f§5§6§6", ChampionshipTabText.playerNameColor("&#fff566", true));
        assertEquals("§f", ChampionshipTabText.playerNameColor("&#fff566", false));
        assertEquals("§f", ChampionshipTabText.playerNameColor(null, true));
    }

    @Test
    void sharesTheWholeTabIdentityWithJoinMessages() {
        assertEquals(
                "§8[§x§f§f§5§5§5§5红队§8]§r §x§f§f§5§5§5§5Player§r",
                ChampionshipTabText.playerIdentity("&#ff5555红队", "&#ff5555", true, "Player"));
        assertEquals(
                "§8[§a大厅§8]§r §fPlayer§r",
                ChampionshipTabText.playerIdentity("&a大厅", "&#ff5555", false, "Player"));
    }

    @Test
    void chatShowsPlayerNameBeforeTeamAndPreservesMessageStyle() {
        Component message =
                Component.text("hello", net.kyori.adventure.text.format.NamedTextColor.GREEN);
        Component chat =
                ChampionshipTabText.chatLine("&#ff5555红队", "&#ff5555", true, "Player", message);
        assertEquals(
                "Player <红队> » hello", PlainTextComponentSerializer.plainText().serialize(chat));
        assertEquals(message, chat.children().getLast());
        assertEquals(
                "Viewer <旁观> » hello",
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                ChampionshipTabText.chatLine(
                                        "旁观", null, false, "Viewer", message)));
    }

    @Test
    void dailyChatKeepsItsLabelBeforeThePlayerName() {
        Component message =
                Component.text("hello", net.kyori.adventure.text.format.NamedTextColor.GREEN);
        for (String label : java.util.List.of("红队", "大厅", "激光方盒")) {
            Component chat =
                    new PlayerPresentation(label, "&c", true, true).chatLine("Player", message);
            assertEquals(
                    "[" + label + "] Player » hello",
                    PlainTextComponentSerializer.plainText().serialize(chat));
            assertEquals(message, chat.children().getLast());
        }
    }
}
