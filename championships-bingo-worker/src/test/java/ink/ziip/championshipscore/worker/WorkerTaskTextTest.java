package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.platform.bukkit.bingo.FrozenBingoTaskText;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class WorkerTaskTextTest {
    private static BingoTaskSpec task(String type, Map<String, String> attributes) {
        return new BingoTaskSpec(0, "identifier %name%", type, attributes);
    }

    @Test
    void frozenTextIsUsedWithoutLookingUpAnObjectiveOrReexpandingTokens() {
        var frozen =
                new FrozenBingoTaskText(
                        Component.translatable("item.minecraft.diamond")
                                .append(Component.text("%name%")),
                        List.of(Component.text("{0} %name%")));
        var task = task("unknown_future_type", frozen.attributes());
        assertEquals(frozen.name(), WorkerMenuService.displayName(task));
        assertEquals(frozen.lore(), WorkerTaskText.lore(task));
        assertEquals(frozen.attributes(), task.attributes());
    }

    @Test
    void corruptMarkedTextIsNeverSilentlyReconstructedOrPartiallyDisplayed() {
        var attributes =
                new HashMap<>(
                        new FrozenBingoTaskText(Component.text("ok"), List.of(Component.text("ok")))
                                .attributes());
        attributes.put("display.lore.0", "{");
        var task = task("event", attributes);
        assertThrows(IllegalArgumentException.class, () -> WorkerTaskText.name(task));
        assertThrows(IllegalArgumentException.class, () -> WorkerTaskText.lore(task));
    }

    @Test
    void historicalTextKeepsValidOptionalFieldsAndSkipsMalformedOrMissingLines() {
        var serializer = GsonComponentSerializer.gson();
        var attributes =
                Map.of(
                        "display.name",
                        serializer.serialize(Component.text("old name")),
                        "display.lore-size",
                        "2147483647",
                        "display.lore.1",
                        "{",
                        "display.lore.2",
                        serializer.serialize(Component.text("later")),
                        "display.lore.0",
                        serializer.serialize(Component.text("first")));
        var task = task("event", attributes);
        assertEquals(Component.text("old name"), WorkerTaskText.name(task));
        assertEquals(
                List.of(Component.text("first"), Component.text("later")),
                WorkerTaskText.lore(task));
        assertEquals(attributes, task.attributes());
    }

    @Test
    void historicalFallbackKeepsOriginalDecorationAndLiteralIdentifiers() {
        var serializer = LegacyComponentSerializer.legacySection();
        assertEquals(
                "§eidentifier %name%",
                serializer.serialize(
                        WorkerTaskText.name(task("event", Map.of("display.name", "{")))));
        assertEquals(
                "§djump × 7",
                serializer.serialize(
                        WorkerTaskText.name(
                                task("statistic", Map.of("statistic", "JUMP", "target", "7")))));
        assertEquals(
                "§dstatistic × 1",
                serializer.serialize(
                        WorkerTaskText.name(task("statistic", Map.of("count", "invalid")))));
        assertEquals(
                "§dkill_entity × 2 zombie",
                serializer.serialize(
                        WorkerTaskText.name(
                                task(
                                        "statistic",
                                        Map.of(
                                                "statistic",
                                                "KILL_ENTITY",
                                                "count",
                                                "2",
                                                "entity",
                                                "ZOMBIE")))));
    }
}
