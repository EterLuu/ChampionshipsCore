package ink.ziip.championshipscore.command.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.AdminMessages;
import ink.ziip.championshipscore.configuration.config.message.ServerMessages;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ExtendWith(ConfigurationStateExtension.class)
class GameMainCommandTest {
    private List<String> messages;

    @BeforeEach
    void setUp() {
        messages = new ArrayList<>();
        ServerMessages.NO_PERMISSION = "no permission";
        AdminMessages.COMMAND_USAGE = "%usage%";
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource(
            nullValues = "none",
            value = {
                // Ordinary senders cannot enter game control at all.
                "none, true",
                // Manual test permission cannot reach automation control.
                "cc.test, true",
                // Admins see other children but not the automation test child.
                "cc.admin, false"
            })
    void permissionsWithoutAutomationCannotReachTestChild(
            String permission, boolean noCompletions) {
        CommandSender sender =
                permission == null ? senderWithPermissions() : senderWithPermissions(permission);
        GameMainCommand command = new GameMainCommand();

        assertTrue(command.onCommand(sender, null, "cc", new String[] {"test"}));
        List<String> completions = command.onTabComplete(sender, null, "cc", new String[] {""});
        if (noCompletions) assertEquals(List.of(), completions);
        else assertFalse(completions.contains("test"), completions.toString());
        assertEquals(List.of("no permission"), messages);
    }

    @Test
    void automationPermissionCanReachTestChild() {
        CommandSender sender = senderWithPermissions("cc.test.automation");
        GameMainCommand command = new GameMainCommand();

        assertTrue(command.onCommand(sender, null, "cc", new String[] {"test"}));
        assertTrue(command.onTabComplete(sender, null, "cc", new String[] {""}).contains("test"));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("/cc game test"));
    }

    private CommandSender senderWithPermissions(String... permissions) {
        Set<String> allowed = Set.of(permissions);
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
