package ink.ziip.championshipscore.platform.bukkit.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeamChatCommandParserTest {
    @Test
    void acceptsVanillaNamesAndAliases() {
        assertEquals("hello team", TeamChatCommandParser.messageBody("/teammsg hello team"));
        assertEquals("hello", TeamChatCommandParser.messageBody("/TM   hello  "));
        assertEquals("hello", TeamChatCommandParser.messageBody("/minecraft:teammsg hello"));
        assertEquals("hello", TeamChatCommandParser.messageBody("/minecraft:tm hello"));
    }

    @Test
    void distinguishesMissingMessagesAndOtherCommands() {
        assertEquals("", TeamChatCommandParser.messageBody("/tm"));
        assertNull(TeamChatCommandParser.messageBody("/msg Alice hello"));
        assertNull(TeamChatCommandParser.messageBody("hello"));
    }
}
