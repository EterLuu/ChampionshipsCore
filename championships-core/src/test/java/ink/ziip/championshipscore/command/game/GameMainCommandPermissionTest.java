package ink.ziip.championshipscore.command.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class GameMainCommandPermissionTest {
    private List<String> messages;

    @BeforeEach
    void setUp() {
        messages = new ArrayList<>();
        MessageConfig.NO_PERMISSION = "no permission";
        MessageConfig.COMMAND_USAGE = "%usage%";
    }

    @Test
    void ordinarySenderCannotEnterGameControl() {
        CommandSender sender = senderWithPermissions();
        GameMainCommand command = new GameMainCommand();

        assertTrue(command.onCommand(sender, null, "cc", new String[] {"test"}));
        assertEquals(List.of(), command.onTabComplete(sender, null, "cc", new String[] {""}));
        assertEquals(List.of("no permission"), messages);
    }

    @Test
    void testPermissionCanReachOnlyTestChild() {
        CommandSender sender = senderWithPermissions("cc.test");
        GameMainCommand command = new GameMainCommand();

        assertTrue(command.onCommand(sender, null, "cc", new String[] {"test"}));
        assertEquals(List.of("test"), command.onTabComplete(sender, null, "cc", new String[] {""}));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("/cc game test"));
    }

    @Test
    void adminPermissionAlsoReachesTestChild() {
        CommandSender sender = senderWithPermissions("cc.admin");
        GameMainCommand command = new GameMainCommand();

        assertTrue(command.onCommand(sender, null, "cc", new String[] {"test"}));
        assertTrue(command.onTabComplete(sender, null, "cc", new String[] {""}).contains("test"));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("/cc game test"));
    }

    private CommandSender senderWithPermissions(String... permissions) {
        java.util.Set<String> allowed = java.util.Set.of(permissions);
        return (CommandSender)
                Proxy.newProxyInstance(
                        CommandSender.class.getClassLoader(),
                        new Class<?>[] {CommandSender.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("hasPermission"))
                                return allowed.contains(args[0]);
                            if (method.getName().equals("sendMessage")) {
                                messages.add((String) args[0]);
                                return null;
                            }
                            if (method.getName().equals("getName")) return "tester";
                            if (method.getName().equals("toString")) return "tester";
                            throw new AssertionError(method.getName());
                        });
    }
}
