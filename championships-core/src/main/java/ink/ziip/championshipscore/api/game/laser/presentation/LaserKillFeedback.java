package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Kill fireworks and private notes; ACE can finish during settlement, but never carry into a new
 * round.
 */
public final class LaserKillFeedback {
    private static final String KILL_FIREWORK = "kill-firework";
    private static final Color HEADSHOT_COLOR = Color.fromRGB(0xFFD700);
    private static final Note E = Note.natural(0, Note.Tone.E);
    // Bukkit octave boundaries start at F#, so E4 is in octave 0 and F#4-A4 in octave 1.
    private static final Note G = Note.natural(1, Note.Tone.G);
    private static final Note[] KILL_NOTES = {
        E, Note.sharp(1, Note.Tone.F), G, Note.natural(1, Note.Tone.A)
    };
    private static final long ACE_NOTE_INTERVAL_TICKS = 3L;

    private LaserKillFeedback() {}

    public static void burst(Player victim, Color teamColor, NamespacedKey itemKey) {
        burst(victim, teamColor, itemKey, false);
    }

    public static void burst(
            Player victim, Color teamColor, NamespacedKey itemKey, boolean headshot) {
        Location neck = victim.getEyeLocation().subtract(0, 0.25, 0);
        Firework firework =
                neck.getWorld()
                        .spawn(
                                neck,
                                Firework.class,
                                rocket -> {
                                    var meta = rocket.getFireworkMeta();
                                    meta.clearEffects();
                                    meta.addEffect(
                                            FireworkEffect.builder()
                                                    .with(FireworkEffect.Type.BALL)
                                                    .withColor(
                                                            teamColor,
                                                            headshot ? HEADSHOT_COLOR : Color.WHITE)
                                                    .build());
                                    meta.setPower(0);
                                    rocket.setFireworkMeta(meta);
                                    rocket.getPersistentDataContainer()
                                            .set(itemKey, PersistentDataType.STRING, KILL_FIREWORK);
                                    rocket.setPersistent(false);
                                    rocket.setShotAtAngle(true);
                                    rocket.setVelocity(new Vector());
                                });
        firework.detonate();
    }

    public static boolean isKillFirework(Entity entity, NamespacedKey itemKey) {
        return entity instanceof Firework
                && KILL_FIREWORK.equals(
                        entity.getPersistentDataContainer()
                                .get(itemKey, PersistentDataType.STRING));
    }

    public static void play(Player killer, int kills, Plugin plugin, BooleanSupplier currentRound) {
        if (!plugin.isEnabled()) return;
        Runnable playback =
                () -> play(killer, kills, plugin, currentRound, note -> playNote(killer, note));
        if (Bukkit.isOwnedByCurrentRegion(killer)) playback.run();
        else new PlatformScheduler(plugin).runEntity(killer, playback);
    }

    /** Keeps native sound rendering separate from entity-owned playback and its lifetime guard. */
    static void play(
            Player killer,
            int kills,
            Plugin plugin,
            BooleanSupplier currentRound,
            Consumer<Note> emit) {
        var id = killer.getUniqueId();
        BooleanSupplier currentPlayback =
                () ->
                        plugin.isEnabled()
                                && killer.isOnline()
                                && Bukkit.getPlayer(id) == killer
                                && currentRound.getAsBoolean();
        if (kills < 1 || !currentPlayback.getAsBoolean()) return;
        if (kills <= KILL_NOTES.length) {
            emit.accept(noteForKills(kills));
            return;
        }
        List<Note> melody = aceMelody();
        emit.accept(melody.getFirst());
        PlatformScheduler scheduler = new PlatformScheduler(plugin);
        for (int beat = 1; beat < melody.size(); beat++) {
            Note note = melody.get(beat);
            scheduler.runEntityLater(
                    killer,
                    () -> {
                        if (currentPlayback.getAsBoolean()) emit.accept(note);
                    },
                    beat * ACE_NOTE_INTERVAL_TICKS);
        }
    }

    public static Note noteForKills(int kills) {
        if (kills < 1 || kills > KILL_NOTES.length) throw new IllegalArgumentException("kills");
        return KILL_NOTES[kills - 1];
    }

    public static List<Note> aceMelody() {
        return List.of(E, E, E, G);
    }

    private static void playNote(Player killer, Note note) {
        var location = killer.getLocation();
        killer.playNote(location, Instrument.BIT, note);
    }
}
