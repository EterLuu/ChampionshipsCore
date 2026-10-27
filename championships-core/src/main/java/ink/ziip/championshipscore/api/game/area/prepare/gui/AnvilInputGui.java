package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareKeys;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.gui.AnvilInputMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Prepare-domain validation over the Core-owned native input lifecycle. */
public final class AnvilInputGui {
    private AnvilInputGui() {}

    public static void openName(Player player, PrepareSessionManager manager, GameTypeEnum type) {
        var session = manager.getSession(player);
        String prompt = GuiConfig.text(MenuId.MAP_EDITOR_INPUT.item("name") + ".title");
        open(
                player,
                manager,
                session,
                "name",
                prompt,
                prompt,
                text -> validateName(manager, type, text),
                text -> manager.createAndEnter(player, type, text),
                player::sendMessage,
                null);
    }

    public static void openNumber(Player player, PrepareSession session, IntConsumer submit) {
        String prompt = GuiConfig.text(MenuId.MAP_EDITOR_INPUT.item("number") + ".title");
        open(
                player,
                session.getPlugin().getPrepareSessionManager(),
                session,
                "number",
                prompt,
                prompt,
                text -> {
                    try {
                        return Integer.parseInt(text) < 1
                                ? MessageConfig.MAP_EDITOR_INPUT_NUMBER_TOO_SMALL
                                : null;
                    } catch (NumberFormatException ignored) {
                        return MessageConfig.MAP_EDITOR_INPUT_INVALID_NUMBER;
                    }
                },
                text -> submit.accept(Integer.parseInt(text)),
                error -> CoreMessages.sendAdminError(player, error),
                null);
    }

    /** A blank signed integer accepts the caller's original default. */
    public static void openInteger(
            Player player,
            PrepareSession session,
            String prompt,
            int defaultValue,
            IntConsumer submit) {
        open(
                player,
                session.getPlugin().getPrepareSessionManager(),
                session,
                "number",
                prompt,
                prompt,
                text -> {
                    if (text.isBlank()) return null;
                    try {
                        Integer.parseInt(text);
                        return null;
                    } catch (NumberFormatException ignored) {
                        return MessageConfig.MAP_EDITOR_INPUT_INVALID_INTEGER;
                    }
                },
                text -> submit.accept(text.isBlank() ? defaultValue : Integer.parseInt(text)),
                error -> CoreMessages.sendAdminError(player, error),
                null);
    }

    public static void openEditorText(
            Player player,
            PrepareSession session,
            String prompt,
            String initialValue,
            Function<String, String> validate,
            Consumer<String> submit,
            Runnable cancel) {
        open(
                player,
                session.getPlugin().getPrepareSessionManager(),
                session,
                "name",
                prompt,
                initialValue,
                validate,
                submit,
                error -> CoreMessages.sendAdminError(player, error),
                cancel);
    }

    private static void open(
            Player player,
            PrepareSessionManager manager,
            PrepareSession session,
            String item,
            String title,
            String value,
            Function<String, String> validate,
            Consumer<String> submit,
            Consumer<String> error,
            Runnable cancel) {
        var configured =
                AnvilInputMenu.Prompt.configured(
                        MenuId.MAP_EDITOR_INPUT.item(item),
                        title,
                        value,
                        GuiConfig.component(MenuId.MAP_EDITOR_INPUT.item("hint") + ".title"));
        var input = configured.input();
        PrepareKeys.tagPrepare(input);
        manager.getPlugin()
                .getAnvilInputMenu()
                .open(
                        manager,
                        player,
                        new AnvilInputMenu.Prompt(title, value, input),
                        new AnvilInputMenu.Callbacks(
                                () ->
                                        player.hasPermission(ChampionshipPermissions.ADMIN)
                                                && manager.getPlugin().getPrepareSessionManager()
                                                        == manager
                                                && manager.getSession(player) == session,
                                validate,
                                submit,
                                error,
                                cancel,
                                null));
    }

    public static void close(PrepareSessionManager manager, Player player) {
        manager.getPlugin().getAnvilInputMenu().close(manager, player);
    }

    public static void closeAll(PrepareSessionManager manager) {
        manager.getPlugin().getAnvilInputMenu().closeOwner(manager);
    }

    private static @Nullable String validateName(
            @NotNull PrepareSessionManager manager,
            @NotNull GameTypeEnum gameType,
            @Nullable String name) {
        if (name == null || name.isBlank())
            return CoreMessages.formatAdminError(MessageConfig.MAP_EDITOR_INPUT_NAME_EMPTY);
        String trimmed = name.trim();
        if (trimmed.length() > 32)
            return CoreMessages.formatAdminError(MessageConfig.MAP_EDITOR_INPUT_NAME_TOO_LONG);
        if (trimmed.matches(".*[\\\\/:*?\"<>|].*"))
            return CoreMessages.formatAdminError(MessageConfig.MAP_EDITOR_INPUT_NAME_INVALID);
        BaseGameInstanceManager<?> mgr =
                manager.getPlugin().getGameManager().getAreaManager(gameType);
        if (mgr != null && mgr.getArea(trimmed) != null)
            return CoreMessages.formatAdminError(
                    MessageConfig.MAP_EDITOR_INPUT_NAME_ALREADY_EXISTS.replace("%name%", trimmed));
        return null;
    }
}
