package ink.ziip.championshipscore.redis;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisInfrastructureConfigTest {
    @Test
    void keysAreNamespacedOnce() {
        RedisConnectionConfig config = new RedisConnectionConfig("redis://localhost:6379/0",
                "championships", "core-a", 1000, Duration.ofSeconds(2));
        assertEquals("championships:core:data-sync", config.key("core:data-sync"));
    }

    @Test
    void everyCoreInstanceGetsItsOwnFanoutGroups() {
        assertEquals("cc:data:core-a", RedisGroupNames.databaseSync("cc", "core-a"));
        assertEquals("cc:bingo:core-a", RedisGroupNames.bingoEvents("cc", "core-a"));
        assertNotEquals(RedisGroupNames.databaseSync("cc", "core-a"),
                RedisGroupNames.databaseSync("cc", "core-b"));
    }

    @Test
    void blankInstanceCannotAccidentallyCreateASharedGroup() {
        assertThrows(IllegalArgumentException.class, () -> RedisGroupNames.databaseSync("cc", " "));
    }
}
