package ink.ziip.championshipscore.api.game.area.prepare;

/**
 * How a {@link PrepareStep} captures its value from the player. Drives the click routing in
 * {@code PrepareListener}: the simple types capture immediately on click; {@link #STAMP} opens an
 * anvil to read a count first; {@link #LIST} opens a small sub-GUI with add/clear actions.
 */
public enum StepCaptureType {
    /** Player stands in the correct game world and clicks to acknowledge (session-only flag). */
    CONFIRM_WORLD,
    /** WorldEdit selection saved to a schematic file. */
    SCHEMATIC,
    /** Stamp N schematic copies into the world and persist via {@code saveMap}; count comes from an anvil. */
    STAMP,
    /** Capture the player's current location into config. */
    STAND_AND_RUN,
    /** Capture the player's current WorldEdit selection (pos1/pos2) into config. */
    WE_SELECTION,
    /** A list of locations: add the player's current location, or clear. */
    LIST
}
