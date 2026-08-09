package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.platform.bukkit.bingo.BingoPortalRouter;

/** Backward-compatible Core listener name backed by the shared local/Folia portal router. */
public final class PortalListener extends BingoPortalRouter {
    public PortalListener(String baseWorldName) {
        super(baseWorldName);
    }
}
