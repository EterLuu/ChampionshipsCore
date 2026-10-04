package ink.ziip.championshipscore.api.game.laserbox.mechanics;

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
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Kill fireworks and private notes; ACE can finish during settlement, but never carry into a new
 * round.
 */
public final class LaserBoxKillFeedback {
    private static final String KILL_FIREWORK = "kill-firework";
    private static final Note E = Note.natural(0, Note.Tone.E);
    // Bukkit octave boundaries start at F#, so E4 is in octave 0 and F#4-A4 in octave 1.
    private static final Note G = Note.natural(1, Note.Tone.G);
    private static final Note[] KILL_NOTES = {
        E, Note.sharp(1, Note.Tone.F), G, Note.natural(1, Note.Tone.A)
    };
    private static final long ACE_NOTE_INTERVAL_TICKS = 3L;

    private LaserBoxKillFeedback() {}

    public static void burst(Player victim, Color teamColor, NamespacedKey itemKey) {
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
                                                    .withColor(teamColor, Color.WHITE)
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

    public static void play(
            Player killer,
            int kills,
            BukkitScheduler scheduler,
            Plugin plugin,
            BooleanSupplier currentRound) {
        if (kills < 1 || !killer.isOnline()) return;
        if (kills <= KILL_NOTES.length) {
            playNote(killer, noteForKills(kills), false);
            return;
        }
        List<Note> melody = aceMelody();
        playNote(killer, melody.getFirst(), true);
        for (int beat = 1; beat < melody.size(); beat++) {
            Note note = melody.get(beat);
            scheduler.runTaskLater(
                    plugin,
                    () -> {
                        if (killer.isOnline() && currentRound.getAsBoolean())
                            playNote(killer, note, true);
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

    private static void playNote(Player killer, Note note, boolean ace) {
        var location = killer.getLocation();
        killer.playNote(location, Instrument.BIT, note);
        if (ace) killer.playNote(location, Instrument.PLING, note);
    }
}
