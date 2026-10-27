package ink.ziip.championshipscore.support;

import ink.ziip.championshipscore.api.game.bingo.util.MessageService;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Real locale loading with an isolated data directory; no server or production configuration. */
public final class BingoMessages {
    private BingoMessages() {}

    public static MessageService open(Path directory, String locale) {
        var plugin =
                (Plugin)
                        Proxy.newProxyInstance(
                                Plugin.class.getClassLoader(),
                                new Class<?>[] {Plugin.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getDataFolder" -> directory.toFile();
                                            case "getLogger" -> Logger.getAnonymousLogger();
                                            case "getResource" ->
                                                    BingoMessages.class.getResourceAsStream(
                                                            "/" + args[0]);
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        return new MessageService(plugin, "", locale);
    }
}
