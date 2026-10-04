package ink.ziip.championshipscore.api.game.riptiderush.model;

/** Runtime mechanic of a planned stage; templates and runtime stages are not the same hierarchy. */
public enum RiptideStageKind {
    PASS(RiptideStageGroup.MOVING),
    MATH(RiptideStageGroup.MOVING),
    RHYTHM(RiptideStageGroup.MOVING),
    COLOR_FLOOR(RiptideStageGroup.STOPPED),
    DODGE(RiptideStageGroup.STOPPED),
    SIDE_SWEEP(RiptideStageGroup.STOPPED);

    private final RiptideStageGroup group;

    RiptideStageKind(RiptideStageGroup group) {
        this.group = group;
    }

    public boolean stopsRaft() {
        return group == RiptideStageGroup.STOPPED;
    }

    public RiptideStageGroup group() {
        return group;
    }
}
