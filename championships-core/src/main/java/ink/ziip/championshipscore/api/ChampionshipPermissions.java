package ink.ziip.championshipscore.api;

/** Permission nodes owned by ChampionshipsCore and declared in plugin.yml. */
public final class ChampionshipPermissions {
    public static final String PLAYER = "cc.player";
    public static final String ADMIN = "cc.admin";
    public static final String TEST = "cc.test";
    /** Automated Core session controls; intentionally not inherited by cc.admin. */
    public static final String TEST_AUTOMATION = "cc.test.automation";
    public static final String REFEREE = "cc.refuge";

    private ChampionshipPermissions() {}
}
