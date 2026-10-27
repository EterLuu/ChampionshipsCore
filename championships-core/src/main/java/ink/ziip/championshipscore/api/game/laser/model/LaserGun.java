package ink.ziip.championshipscore.api.game.laser.model;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Shared laser weapons. Damage is expressed against a 100 point combat pool; a normal body shot
 * therefore makes the rifle a three-shot weapon and the SMG/LMG six-shot weapons. The values are
 * deliberately independent of Minecraft attack damage so that every weapon uses the same ray-trace
 * and damage model.
 */
public enum LaserGun {
    SNIPER("sniper", Material.GOLDEN_SHOVEL, 2, 80, 2.75, 80, 0.1, 0.0, 90, 160, 75, 0.96),
    RIFLE("rifle", Material.COPPER_HOE, 12, 50, 5.25, 80, 0.75, 0.0, 34, 110, 22, 0.90),
    SMG("smg", Material.IRON_SPEAR, 35, 40, 15, 80, 1.8, 0.45, 18, 45, 11, 0.50),
    MACHINE("machine", Material.STONE_SPEAR, 60, 70, 15, 80, 2.5, 0.625, 22, 45, 14, 0.75),
    SHOTGUN("shotgun", Material.DIAMOND_AXE, 7, 50, 3.5, 16, 7.0, 7.0, 18, 26, 10, 0.25);

    private final String id;
    private final Material material;
    private final int magazine;
    private final int reloadTicks;
    private final double shotsPerSecond;
    private final int range;
    private final double spreadDegrees;
    private final double firstShotSpreadDegrees;
    private final double bodyDamage;
    private final double headDamage;
    private final double legDamage;
    private final double minimumFalloff;

    LaserGun(
            String id,
            Material material,
            int magazine,
            int reloadTicks,
            double shotsPerSecond,
            int range,
            double spreadDegrees,
            double firstShotSpreadDegrees,
            double bodyDamage,
            double headDamage,
            double legDamage,
            double minimumFalloff) {
        this.id = id;
        this.material = material;
        this.magazine = magazine;
        this.reloadTicks = reloadTicks;
        this.shotsPerSecond = shotsPerSecond;
        this.range = range;
        this.spreadDegrees = spreadDegrees;
        this.firstShotSpreadDegrees = firstShotSpreadDegrees;
        this.bodyDamage = bodyDamage;
        this.headDamage = headDamage;
        this.legDamage = legDamage;
        this.minimumFalloff = minimumFalloff;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public int magazine() {
        return magazine;
    }

    public int reloadTicks() {
        return reloadTicks;
    }

    public double shotsPerSecond() {
        return shotsPerSecond;
    }

    /** Nominal interval at 20 TPS; fractional deadlines retain the requested long-term cadence. */
    public double fireIntervalTicks() {
        return 20.0D / shotsPerSecond;
    }

    public int range() {
        return range;
    }

    public double spreadDegrees() {
        return spreadDegrees;
    }

    /**
     * Returns the spread for a shot in a continuous burst. The first three follow-up shots ramp
     * from first-shot accuracy to the weapon's normal maximum spread.
     */
    public double spreadDegrees(int burstShot) {
        if (this == SHOTGUN || burstShot >= 3) return spreadDegrees;
        if (burstShot <= 0) return firstShotSpreadDegrees;
        double progress = burstShot / 3.0D;
        return firstShotSpreadDegrees + (spreadDegrees - firstShotSpreadDegrees) * progress;
    }

    /**
     * Returns the spread while crouching. The machine gun is intentionally steadier in this stance
     * so that crouching gives it a stronger sustained-fire role than the other weapons.
     */
    public double crouchedSpread(double burstSpread, boolean moving) {
        if (this == MACHINE) {
            return moving ? burstSpread * 0.65D + spreadDegrees * 0.75D : burstSpread * 0.55D;
        }
        return moving ? burstSpread * 0.75D + spreadDegrees : burstSpread * 0.70D;
    }

    /** Deterministic upward kick applied after the first shot in a burst. */
    public double recoilPitchDegrees(int burstShot) {
        if (burstShot <= 0 || this == SNIPER || this == SHOTGUN) return 0.0D;
        return switch (this) {
            case RIFLE -> Math.min(0.12D * burstShot, 0.72D);
            case SMG -> Math.min(0.10D * burstShot, 0.60D);
            case MACHINE -> Math.min(0.15D * burstShot, 0.90D);
            default -> 0.0D;
        };
    }

    /** Deterministic horizontal kick pattern; random inaccuracy is applied separately. */
    public double recoilYawDegrees(int burstShot) {
        if (burstShot <= 0 || this == SNIPER || this == SHOTGUN) return 0.0D;
        int index = burstShot - 1;
        return switch (this) {
            case RIFLE ->
                    switch (index % 4) {
                        case 0 -> 0.06D;
                        case 1 -> -0.08D;
                        case 2 -> 0.09D;
                        default -> -0.10D;
                    };
            case SMG ->
                    switch (index % 5) {
                        case 0 -> 0.12D;
                        case 1 -> -0.16D;
                        case 2 -> 0.20D;
                        case 3 -> -0.16D;
                        default -> 0.12D;
                    };
            case MACHINE ->
                    switch (index % 5) {
                        case 0 -> 0.16D;
                        case 1 -> -0.24D;
                        case 2 -> 0.28D;
                        case 3 -> -0.28D;
                        default -> 0.20D;
                    };
            default -> 0.0D;
        };
    }

    public int pellets() {
        return this == SHOTGUN ? 8 : 1;
    }

    public double damage(HitRegion region, double distance) {
        double base =
                switch (region) {
                    case HEAD -> headDamage;
                    case LEGS -> legDamage;
                    case BODY -> bodyDamage;
                };
        double start = this == RIFLE ? 30.0D : 0.0D;
        double fraction = Math.clamp((distance - start) / (range - start), 0.0D, 1.0D);
        double falloff = 1.0D - (1.0D - minimumFalloff) * fraction;
        return base * falloff;
    }

    public static LaserGun fromId(String value) {
        if (value == null) return null;
        String id = value.startsWith("gun:") ? value.substring(4) : value;
        for (LaserGun gun : values()) if (gun.id.equalsIgnoreCase(id)) return gun;
        return null;
    }

    public static LaserGun fromMaterial(Material material) {
        if (material == null) return null;
        for (LaserGun gun : values()) if (gun.material == material) return gun;
        return null;
    }

    @Override
    public String toString() {
        return id.toUpperCase(Locale.ROOT);
    }

    public enum HitRegion {
        HEAD,
        BODY,
        LEGS
    }
}
