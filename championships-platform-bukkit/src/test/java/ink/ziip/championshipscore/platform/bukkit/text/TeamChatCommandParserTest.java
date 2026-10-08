package ink.ziip.championshipscore.platform.bukkit.text;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class TeamChatCommandParserTest {

    @Test
    void commandBodiesDistinguishAliasesEmptyTeamMessagesAndUnrelatedInput() {
        String[][] cases = {
            {"/teammsg hello team", "hello team"},
            {"/TM   hello  ", "hello"},
            {"/minecraft:teammsg hello", "hello"},
            {"/minecraft:tm hello", "hello"},
            {"/tm", ""},
            {"/msg Alice hello", null},
            {"hello", null}
        };
        for (String[] example : cases)
            assertEquals(example[1], TeamChatCommandParser.messageBody(example[0]), example[0]);
    }
}
