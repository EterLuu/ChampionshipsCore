package ink.ziip.championshipscore.worker;

record WorkerPlayerPresentation(String label, String teamColorCode, boolean activePlayer) {
    static WorkerPlayerPresentation spectator() {
        // Core's uncoloured spectator placeholder inherits the TAB prefix colour.
        return new WorkerPlayerPresentation("旁观", null, false);
    }
}
