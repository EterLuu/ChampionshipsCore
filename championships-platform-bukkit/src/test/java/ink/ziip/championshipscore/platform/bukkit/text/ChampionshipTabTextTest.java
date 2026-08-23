package ink.ziip.championshipscore.platform.bukkit.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChampionshipTabTextTest {
    @Test
    void rendersTheSharedCoreAndWorkerFooterContract() {
        assertEquals("§f队伍: §x§f§f§f§5§6§6金队 §f| 积分: 1235",
                ChampionshipTabText.teamFooter("&#fff566金队", 1234.5D));
        assertEquals("§f当前游戏: §b宾果时速",
                ChampionshipTabText.currentGameFooter("宾果时速"));
        assertEquals("§f队伍: §x§5§5§f§f§f§f青队",
                ChampionshipTabText.dailyTeamFooter("&#55ffff青队"));
        assertEquals("§8[§x§f§f§f§5§6§6王牌竞速§8]§r ",
                ChampionshipTabText.gamePrefix("王牌竞速"));
    }

    @Test
    void colorsOnlyActivePlayersAndResetsEveryoneElse() {
        assertEquals("§x§f§f§f§5§6§6",
                ChampionshipTabText.playerNameColor("&#fff566", true));
        assertEquals("§f", ChampionshipTabText.playerNameColor("&#fff566", false));
        assertEquals("§f", ChampionshipTabText.playerNameColor(null, true));
    }

    @Test
    void sharesTheWholeTabIdentityWithChatAndJoinMessages() {
        assertEquals("§8[§x§f§f§5§5§5§5红队§8]§r §x§f§f§5§5§5§5Player§r",
                ChampionshipTabText.playerIdentity("&#ff5555红队", "&#ff5555", true, "Player"));
        assertEquals("§8[§a大厅§8]§r §fPlayer§r",
                ChampionshipTabText.playerIdentity("&a大厅", "&#ff5555", false, "Player"));
    }
}
