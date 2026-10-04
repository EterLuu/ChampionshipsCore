package ink.ziip.championshipscore.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.junit.jupiter.api.Test;

class DailyMessageTest {
    @Test
    void resolvesConfiguredPrefixAndNestedParticipantOrSpectatorMessages() {
        String oldPrefix = MessageConfig.DAILY_PREFIX;
        String oldTemplate = MessageConfig.DAILY_PREFIXED;
        try {
            MessageConfig.DAILY_PREFIX = "&a[大厅] ";
            MessageConfig.DAILY_PREFIXED = "%prefix%%message%";
            assertEquals("&a[大厅] 已加入", CoreMessages.dailyMessage("已加入"));
            assertEquals("&a[大厅] 正在旁观", CoreMessages.dailyMessage("正在旁观"));
            MessageConfig.DAILY_PREFIXED = "%message%";
            assertEquals("&a[大厅] 已退出", CoreMessages.dailyMessage("%prefix%已退出"));
            MessageConfig.DAILY_PREFIXED = "自定义 %message%";
            assertEquals("自定义 已退出", CoreMessages.dailyMessage("已退出"));
        } finally {
            MessageConfig.DAILY_PREFIX = oldPrefix;
            MessageConfig.DAILY_PREFIXED = oldTemplate;
        }
    }
}
