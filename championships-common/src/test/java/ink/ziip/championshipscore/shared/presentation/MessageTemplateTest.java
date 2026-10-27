package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class MessageTemplateTest {
    @Test
    void insertedTokensAreLiteralRegardlessOfReplacementOrder() {
        var values = new LinkedHashMap<String, String>();
        values.put("%player%", "{0} %score% $1\\name");
        values.put("%score%", "42");
        values.put("{0}", "ready");
        String template = "%player%: %score% ({0}) %unknown%";
        String expected = "{0} %score% $1\\name: 42 (ready) %unknown%";
        assertEquals(expected, MessageTemplate.render(template, values));
        assertEquals(expected, MessageTemplate.render(template, values.reversed()));
    }

    @Test
    void emptyValuesRepeatedTokensAndMissingArgumentsRemainDistinct() {
        assertEquals(
                " / Alice Alice / {999999999999999999999} / 50%",
                MessageTemplate.render(
                        "{0} / %player% %player% / {999999999999999999999} / 50%",
                        Map.of("{0}", "", "%player%", "Alice")));
    }
}
