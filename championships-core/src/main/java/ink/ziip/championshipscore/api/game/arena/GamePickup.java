package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A round-owned visual. Games choose the preview independently of what collecting it grants. */
public final class GamePickup {
    public enum Motion {
        STILL,
        FLOAT_AND_SPIN
    }

    private final ItemDisplay display;
    private final ItemStack preview;
    private final Motion motion;
    private int readyAt;

    public GamePickup(Location anchor, ItemStack preview, Motion motion, boolean glowing) {
        this.preview = preview.clone();
        this.motion = motion;
        display =
                anchor.getWorld()
                        .spawn(
                                anchor,
                                ItemDisplay.class,
                                entity -> {
                                    entity.setPersistent(false);
                                    entity.setGlowing(glowing);
                                    entity.setItemStack(this.preview);
                                    entity.setTransformation(frame(0));
                                    if (motion == Motion.FLOAT_AND_SPIN)
                                        entity.setInterpolationDuration(2);
                                });
    }

    private static Transformation frame(int tick) {
        return new Transformation(
                new Vector3f(0, (float) (Math.sin(tick * .12) * .12), 0),
                new AxisAngle4f((float) Math.toRadians(45 + tick * 2.4), 0, 1, 0),
                new Vector3f(.6f),
                new AxisAngle4f());
    }

    public void tick(int tick) {
        if (!isValid()) return;
        if (readyAt != 0) {
            if (tick < readyAt) return;
            display.setItemStack(preview);
            readyAt = 0;
        }
        if (motion == Motion.FLOAT_AND_SPIN && tick % 2 == 0) {
            display.setInterpolationDelay(0);
            display.setTransformation(frame(tick));
        }
    }

    public void hideUntil(int respawnTick) {
        display.setItemStack(null);
        readyAt = respawnTick;
    }

    public boolean available() {
        return readyAt == 0 && isValid();
    }

    /** A loose pickup can fall or be pushed while its visual bobs about its current location. */
    public void follow(Location anchor) {
        if (!display.getLocation().equals(anchor)) display.teleport(anchor);
    }

    public boolean isValid() {
        return display.isValid();
    }

    public void remove() {
        display.remove();
    }

    public static Component heldName(Component title, Component description) {
        if (description.equals(Component.empty())) return title;
        return ComponentTemplates.render(
                        MessageConfig.codeText("game-pickup.held-name"),
                        Map.of("%title%", title, "%description%", description))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Each round owns its queue so that status-bar refreshes and simultaneous rewards coexist. */
    public static final class Hints {
        private record Hint(Component text, int until) {}

        private final Map<UUID, ArrayDeque<Hint>> pending = new HashMap<>();

        void add(UUID player, Component text, int tick) {
            current(player, tick);
            var queue = pending.computeIfAbsent(player, ignored -> new ArrayDeque<>());
            queue.addLast(new Hint(text, (queue.isEmpty() ? tick : queue.getLast().until()) + 100));
        }

        Component current(UUID player, int tick) {
            var queue = pending.get(player);
            if (queue == null) return null;
            while (!queue.isEmpty() && queue.getFirst().until() <= tick) queue.removeFirst();
            if (!queue.isEmpty()) return queue.getFirst().text();
            pending.remove(player);
            return null;
        }

        public void announce(Player player, Component message, int tick) {
            add(player.getUniqueId(), message, tick);
            refresh(player, tick);
        }

        public boolean refresh(Player player, int tick) {
            Component message = current(player.getUniqueId(), tick);
            if (message == null) return false;
            player.sendActionBar(message);
            return true;
        }

        public void clear(UUID player) {
            pending.remove(player);
        }

        public void clear() {
            pending.clear();
        }
    }
}
