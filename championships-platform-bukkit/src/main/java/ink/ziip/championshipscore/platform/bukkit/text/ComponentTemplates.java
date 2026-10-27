package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.Style;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** One substitution pass preserves rich and translatable arguments, including literal tokens. */
public final class ComponentTemplates {
    private static final Pattern TOKEN = Pattern.compile("%[^%\\s]+%");

    private ComponentTemplates() {}

    public static Component render(String template, Map<String, Component> values) {
        return render(template, values, false);
    }

    /** Task state titles may deliberately recolour and strike through every rich name child. */
    public static Component renderStyled(String template, Map<String, Component> values) {
        return render(template, values, true);
    }

    private static Component render(
            String template, Map<String, Component> values, boolean overrideStyles) {
        return renderNode(LegacyText.component(template), Style.empty(), values, overrideStyles);
    }

    private static Component renderNode(
            Component template,
            Style inherited,
            Map<String, Component> values,
            boolean overrideStyles) {
        Style context = template.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        Component result = template.children(List.of());
        if (template instanceof TextComponent text) {
            var builder = Component.text().style(template.style());
            var matcher = TOKEN.matcher(text.content());
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() > end)
                    builder.append(Component.text(text.content().substring(end, matcher.start())));
                Component replacement = values.get(matcher.group());
                if (replacement == null) builder.append(Component.text(matcher.group()));
                else
                    builder.append(
                            overrideStyles
                                    ? styled(replacement, context)
                                    : replacement.applyFallbackStyle(context));
                end = matcher.end();
            }
            if (end < text.content().length())
                builder.append(Component.text(text.content().substring(end)));
            result = builder.build();
        }
        // Recurse over the original template only; inserted argument tokens are never expanded.
        for (Component child : template.children())
            result = result.append(renderNode(child, context, values, overrideStyles));
        return result;
    }

    private static Component styled(Component component, Style style) {
        var result =
                component
                        .style(component.style().merge(style, Style.Merge.Strategy.ALWAYS))
                        .children(
                                component.children().stream()
                                        .map(child -> styled(child, style))
                                        .toList());
        if (result instanceof TranslatableComponent translatable)
            result =
                    translatable.arguments(
                            translatable.arguments().stream()
                                    .map(
                                            argument ->
                                                    argument.value() instanceof Component child
                                                            ? TranslationArgument.component(
                                                                    styled(child, style))
                                                            : argument)
                                    .toList());
        return result;
    }
}
