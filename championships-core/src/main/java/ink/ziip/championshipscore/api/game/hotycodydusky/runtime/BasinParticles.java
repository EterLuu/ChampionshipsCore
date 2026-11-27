package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import org.bukkit.Color;
import org.bukkit.Particle;

final class BasinParticles {
    private static final Color COD_YELLOW = Color.fromRGB(0xFFD35D);
    private static final Color WATER_BLUE = Color.fromRGB(0x318DF0);
    private static final Color LIGHT_BLUE = Color.fromRGB(0x76D6FF);

    static final Particle.DustOptions COD = new Particle.DustOptions(COD_YELLOW, 1.0f);
    static final Particle.DustOptions SPRAY = new Particle.DustOptions(LIGHT_BLUE, 1.1f);
    static final Particle.DustTransition FISH_JUICE =
            new Particle.DustTransition(WATER_BLUE, LIGHT_BLUE, 1.3f);
    static final Particle.DustTransition METEOR_TRAIL =
            new Particle.DustTransition(COD_YELLOW, LIGHT_BLUE, 1.1f);

    private BasinParticles() {}
}
