package ink.ziip.championshipscore.worker;

record WorkerPlayerPresentation(String label, String teamColorCode, boolean activePlayer) {
    static WorkerPlayerPresentation spectator() {
        return new WorkerPlayerPresentation("&7旁观", null, false);
    }
}
