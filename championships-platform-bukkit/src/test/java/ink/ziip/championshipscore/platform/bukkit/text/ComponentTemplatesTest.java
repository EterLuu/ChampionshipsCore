package ink.ziip.championshipscore.platform.bukkit.text;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ComponentTemplatesTest {
    @Test
    void translatedArgumentsAndUnknownTokensSurviveExactlyOnePass() {
        var translation =
                Component.translatable(
                        "block.minecraft.stone", Component.text("literal %other% $\\"));
        var rendered =
                ComponentTemplates.render(
                        "before %task% / %unknown% / %other%",
                        Map.of("%task%", translation, "%other%", Component.text("after")));
        var text = PlainTextComponentSerializer.plainText().serialize(rendered);
        assertTrue(text.contains("%unknown%"));
        assertTrue(
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                (Component)
                                        findTranslation(rendered).arguments().getFirst().value())
                        .equals("literal %other% $\\"));
        assertTrue(hasTranslation(rendered));
        assertTrue(text.endsWith("/ after"));
    }

    @Test
    void templateStylesOverrideRichChildrenOnlyWhereExplicitlyConfigured() {
        var original =
                Component.translatable(
                                "block.minecraft.stone",
                                Component.text("arg", NamedTextColor.GREEN))
                        .color(NamedTextColor.YELLOW)
                        .append(Component.text("child", NamedTextColor.RED));
        var unchanged = ComponentTemplates.render("%task%", Map.of("%task%", original));
        assertEquals(original, unchanged.compact());
        var completed = ComponentTemplates.renderStyled("&7&m%task%", Map.of("%task%", original));
        assertStyled(completed);
        assertStyled(
                ComponentTemplates.renderStyled("&7&mdone %task%", Map.of("%task%", original)));
        assertTrue(hasTranslation(completed));
    }

    @Test
    void playerIdentityRetainsTeamColoursInsideGrayTemplateBrackets() {
        var identity =
                ComponentTemplates.render(
                        "&f%player% &7<%team%&7>",
                        Map.of(
                                "%player%",
                                Component.text("Alice"),
                                "%team%",
                                Component.text("Blue", NamedTextColor.BLUE)));
        assertEquals("§fAlice §7<§9Blue§7>", LegacyText.serialize(identity));
    }

    private static TranslatableComponent findTranslation(Component component) {
        if (component instanceof TranslatableComponent translation) return translation;
        for (Component child : component.children()) {
            var result = findTranslation(child);
            if (result != null) return result;
        }
        return null;
    }

    private static boolean hasTranslation(Component component) {
        return component instanceof TranslatableComponent
                || component.children().stream().anyMatch(ComponentTemplatesTest::hasTranslation);
    }

    private static void assertStyled(Component component) {
        assertStyled(component, Style.empty());
    }

    private static void assertStyled(Component component, Style inherited) {
        var style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TranslatableComponent
                || component instanceof net.kyori.adventure.text.TextComponent text
                        && !text.content().isEmpty()) {
            assertEquals(NamedTextColor.GRAY, style.color());
            assertEquals(TextDecoration.State.TRUE, style.decoration(TextDecoration.STRIKETHROUGH));
        }
        component.children().forEach(child -> assertStyled(child, style));
        if (component instanceof TranslatableComponent translation)
            translation
                    .arguments()
                    .forEach(
                            argument -> {
                                if (argument.value() instanceof Component child)
                                    assertStyled(child, style);
                            });
    }
}
