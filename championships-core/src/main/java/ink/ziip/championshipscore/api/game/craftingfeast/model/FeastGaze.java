package ink.ziip.championshipscore.api.game.craftingfeast.model;

import java.util.random.RandomGenerator;

/**
 * Where a standing or seated guest looks while its native AI is off. The body settles smoothly on a
 * resting direction (the counter or the table), and the head eases between short idle glances,
 * never more than {@link #MAX_HEAD} degrees off the body. A cook who comes close draws the guest's
 * eyes, and an impatient guest glances around more often.
 */
public final class FeastGaze {
    static final float BODY_STEP = 30;
    static final float HEAD_STEP = 18;
    static final float PITCH_STEP = 8;
    static final float MAX_HEAD = 55;

    /** Cooks further round than this behind the guest's shoulder are ignored. */
    static final float ATTENTION_ARC = 100;

    private float rest;
    private float body;
    private float head;
    private float pitch;
    private float glanceYaw;
    private float glancePitch;
    private int glanceTicks;

    public FeastGaze(float yaw) {
        rest = body = head = yaw;
    }

    /** A new resting direction; the body turns to it over the following updates. */
    public void rest(float yaw) {
        rest = yaw;
    }

    public float body() {
        return body;
    }

    public float head() {
        return head;
    }

    public float pitch() {
        return pitch;
    }

    /**
     * @param attentionYaw absolute yaw towards something worth looking at, or NaN for none
     * @param restless an impatient guest glances around twice as often
     */
    public void update(
            int ticks,
            float attentionYaw,
            float attentionPitch,
            boolean restless,
            RandomGenerator random) {
        body = approach(body, rest, BODY_STEP);
        float targetYaw;
        float targetPitch;
        float toAttention = wrap(attentionYaw - body);
        if (!Float.isNaN(attentionYaw) && Math.abs(toAttention) <= ATTENTION_ARC) {
            targetYaw = body + Math.clamp(toAttention, -MAX_HEAD, MAX_HEAD);
            targetPitch = Math.clamp(attentionPitch, -30, 30);
        } else {
            if ((glanceTicks -= ticks) <= 0) {
                if (random.nextInt(5) < 2) {
                    glanceYaw = 0; // back to the front for a while
                    glancePitch = random.nextInt(3) == 0 ? 20 : 0;
                } else {
                    glanceYaw = (random.nextFloat() * 2 - 1) * (MAX_HEAD - 10);
                    glancePitch = random.nextFloat() * 30 - 10;
                }
                glanceTicks = restless ? 15 + random.nextInt(25) : 40 + random.nextInt(60);
            }
            targetYaw = body + glanceYaw;
            targetPitch = glancePitch;
        }
        head = approach(head, targetYaw, restless ? HEAD_STEP * 1.5f : HEAD_STEP);
        head = body + Math.clamp(wrap(head - body), -MAX_HEAD, MAX_HEAD);
        pitch += Math.clamp(targetPitch - pitch, -PITCH_STEP, PITCH_STEP);
    }

    static float wrap(float degrees) {
        return ((degrees % 360) + 540) % 360 - 180;
    }

    private static float approach(float from, float to, float step) {
        return from + Math.clamp(wrap(to - from), -step, step);
    }
}
