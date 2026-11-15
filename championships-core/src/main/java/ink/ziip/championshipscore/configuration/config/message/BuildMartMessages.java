package ink.ziip.championshipscore.configuration.config.message;

import ink.ziip.championshipscore.configuration.ConfigOption;

/** Message fields owned by the BuildMart domain; persisted by MessageConfig. */
public final class BuildMartMessages {
    private BuildMartMessages() {}

    @ConfigOption(path = "buildmart-blueprint.stars-range")
    public static String BUILD_MART_BLUEPRINT_STARS_RANGE;

    @ConfigOption(path = "buildmart-blueprint.selection-read-failed")
    public static String BUILD_MART_BLUEPRINT_SELECTION_READ_FAILED;

    @ConfigOption(path = "buildmart-blueprint.height-limit")
    public static String BUILD_MART_BLUEPRINT_HEIGHT_LIMIT;

    @ConfigOption(path = "buildmart-blueprint.footprint-limit")
    public static String BUILD_MART_BLUEPRINT_FOOTPRINT_LIMIT;

    @ConfigOption(path = "buildmart-blueprint.block-limit")
    public static String BUILD_MART_BLUEPRINT_BLOCK_LIMIT;

    @ConfigOption(path = "buildmart-blueprint.empty-selection")
    public static String BUILD_MART_BLUEPRINT_EMPTY_SELECTION;

    @ConfigOption(path = "buildmart-blueprint.save-failed")
    public static String BUILD_MART_BLUEPRINT_SAVE_FAILED;

    @ConfigOption(path = "buildmart-blueprint.exported-auto")
    public static String BUILD_MART_BLUEPRINT_EXPORTED_AUTO;

    @ConfigOption(path = "buildmart-blueprint.exported-manual")
    public static String BUILD_MART_BLUEPRINT_EXPORTED_MANUAL;

    @ConfigOption(path = "buildmart-blueprint.override-not-suggested")
    public static String BUILD_MART_BLUEPRINT_OVERRIDE_NOT_SUGGESTED;

    @ConfigOption(path = "buildmart-blueprint.map-missing")
    public static String BUILD_MART_BLUEPRINT_MAP_MISSING;

    @ConfigOption(path = "buildmart-blueprint.missing")
    public static String BUILD_MART_BLUEPRINT_MISSING;

    @ConfigOption(path = "buildmart-blueprint.page-out-of-range")
    public static String BUILD_MART_BLUEPRINT_PAGE_OUT_OF_RANGE;

    @ConfigOption(path = "buildmart-blueprint.stars-same")
    public static String BUILD_MART_BLUEPRINT_STARS_SAME;

    @ConfigOption(path = "buildmart-blueprint.stars-changed")
    public static String BUILD_MART_BLUEPRINT_STARS_CHANGED;

    @ConfigOption(path = "buildmart-blueprint.audit-header")
    public static String BUILD_MART_BLUEPRINT_AUDIT_HEADER;

    @ConfigOption(path = "buildmart-blueprint.audit-summary")
    public static String BUILD_MART_BLUEPRINT_AUDIT_SUMMARY;

    @ConfigOption(path = "buildmart-blueprint.audit-row")
    public static String BUILD_MART_BLUEPRINT_AUDIT_ROW;

    @ConfigOption(path = "buildmart-blueprint.audit-title")
    public static String BUILD_MART_BLUEPRINT_AUDIT_TITLE;

    @ConfigOption(path = "buildmart-blueprint.audit-structure")
    public static String BUILD_MART_BLUEPRINT_AUDIT_STRUCTURE;

    @ConfigOption(path = "buildmart-blueprint.audit-state")
    public static String BUILD_MART_BLUEPRINT_AUDIT_STATE;

    @ConfigOption(path = "buildmart-blueprint.coverage-no-map")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_NO_MAP;

    @ConfigOption(path = "buildmart-blueprint.coverage-no-zones")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_NO_ZONES;

    @ConfigOption(path = "buildmart-blueprint.audit-materials")
    public static String BUILD_MART_BLUEPRINT_AUDIT_MATERIALS;

    @ConfigOption(path = "buildmart-blueprint.coverage-full")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_FULL;

    @ConfigOption(path = "buildmart-blueprint.coverage-uncovered")
    public static String BUILD_MART_BLUEPRINT_COVERAGE_UNCOVERED;

    @ConfigOption(path = "buildmart-blueprint.audit-warning")
    public static String BUILD_MART_BLUEPRINT_AUDIT_WARNING;

    @ConfigOption(path = "buildmart-blueprint-editor.invalid-name")
    public static String BUILD_MART_EDITOR_INVALID_NAME;

    @ConfigOption(path = "buildmart-blueprint-editor.finish-first")
    public static String BUILD_MART_EDITOR_FINISH_FIRST;

    @ConfigOption(path = "buildmart-blueprint-editor.setup-missing")
    public static String BUILD_MART_EDITOR_SETUP_MISSING;

    @ConfigOption(path = "buildmart-blueprint-editor.conflict")
    public static String BUILD_MART_EDITOR_CONFLICT;

    @ConfigOption(path = "buildmart-blueprint-editor.started")
    public static String BUILD_MART_EDITOR_STARTED;

    @ConfigOption(path = "buildmart-blueprint-editor.busy")
    public static String BUILD_MART_EDITOR_BUSY;

    @ConfigOption(path = "buildmart-blueprint-editor.unsupported")
    public static String BUILD_MART_EDITOR_UNSUPPORTED;

    @ConfigOption(path = "buildmart-blueprint-editor.reviewing")
    public static String BUILD_MART_EDITOR_REVIEWING;

    @ConfigOption(path = "buildmart-blueprint-editor.manifest-missing")
    public static String BUILD_MART_EDITOR_MANIFEST_MISSING;

    @ConfigOption(path = "buildmart-blueprint-editor.materials-rejected")
    public static String BUILD_MART_EDITOR_MATERIALS_REJECTED;

    @ConfigOption(path = "buildmart-blueprint-editor.invisible-warning")
    public static String BUILD_MART_EDITOR_INVISIBLE_WARNING;

    @ConfigOption(path = "buildmart-blueprint-editor.saved")
    public static String BUILD_MART_EDITOR_SAVED;

    @ConfigOption(path = "buildmart-blueprint-editor.cancelled")
    public static String BUILD_MART_EDITOR_CANCELLED;

    @ConfigOption(path = "buildmart.start-preparation", nullable = true)
    public static String BUILD_MART_START_PREPARATION;

    @ConfigOption(path = "buildmart.start-preparation-title", nullable = true)
    public static String BUILD_MART_START_PREPARATION_TITLE;

    @ConfigOption(path = "buildmart.start-preparation-subtitle", nullable = true)
    public static String BUILD_MART_START_PREPARATION_SUBTITLE;

    @ConfigOption(path = "buildmart.game-start-title", nullable = true)
    public static String BUILD_MART_GAME_START_TITLE;

    @ConfigOption(path = "buildmart.game-start-subtitle", nullable = true)
    public static String BUILD_MART_GAME_START_SUBTITLE;

    @ConfigOption(path = "buildmart.game-end-title", nullable = true)
    public static String BUILD_MART_GAME_END_TITLE;

    @ConfigOption(path = "buildmart.game-end-subtitle", nullable = true)
    public static String BUILD_MART_GAME_END_SUBTITLE;

    @ConfigOption(path = "buildmart.bossbar.title", nullable = true)
    public static String BUILD_MART_BOSSBAR_TITLE;

    @ConfigOption(path = "buildmart.bossbar.golden-none", nullable = true)
    public static String BUILD_MART_BOSSBAR_GOLDEN_NONE;

    @ConfigOption(path = "buildmart.bossbar.golden-active", nullable = true)
    public static String BUILD_MART_BOSSBAR_GOLDEN_ACTIVE;

    @ConfigOption(path = "buildmart.build-completed", nullable = true)
    public static String BUILD_MART_BUILD_COMPLETED;

    @ConfigOption(path = "buildmart.golden-build-completed", nullable = true)
    public static String BUILD_MART_GOLDEN_BUILD_COMPLETED;

    @ConfigOption(path = "buildmart.golden-refreshed", nullable = true)
    public static String BUILD_MART_GOLDEN_REFRESHED;

    @ConfigOption(path = "buildmart.golden-expired", nullable = true)
    public static String BUILD_MART_GOLDEN_EXPIRED;

    @ConfigOption(path = "buildmart.submit-differences")
    public static String BUILD_MART_SUBMIT_DIFFERENCES;

    @ConfigOption(path = "buildmart.submit-incomplete", nullable = true)
    public static String BUILD_MART_SUBMIT_INCOMPLETE;

    @ConfigOption(path = "buildmart.submit-locked", nullable = true)
    public static String BUILD_MART_SUBMIT_LOCKED;

    @ConfigOption(path = "buildmart.golden-submit-failed", nullable = true)
    public static String BUILD_MART_GOLDEN_SUBMIT_FAILED;

    @ConfigOption(path = "buildmart.golden-submit-confirm", nullable = true)
    public static String BUILD_MART_GOLDEN_SUBMIT_CONFIRM;

    @ConfigOption(path = "buildmart.blueprint-auto-refreshed", nullable = true)
    public static String BUILD_MART_BLUEPRINT_AUTO_REFRESHED;

    @ConfigOption(path = "buildmart.award-entrepreneur", nullable = true)
    public static String BUILD_MART_AWARD_ENTREPRENEUR;

    @ConfigOption(path = "buildmart.award-chef", nullable = true)
    public static String BUILD_MART_AWARD_CHEF;

    @ConfigOption(path = "buildmart.award-quality", nullable = true)
    public static String BUILD_MART_AWARD_QUALITY;
}
