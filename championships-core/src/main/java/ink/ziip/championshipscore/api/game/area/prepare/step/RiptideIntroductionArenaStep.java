package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideIntroductionArena;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Opens the shared Riptide rule-introduction arena from the normal map editor. */
public final class RiptideIntroductionArenaStep extends PrepareStep {
    public RiptideIntroductionArenaStep() {
        super(
                "riptide_introduction_arena",
                LegacyText.component(
                        GuiConfig.text(
                                "map-editor.menus.step-list.games.riptide-rush.items.introduction-arena.title")),
                LegacyText.component(
                        GuiConfig.line(
                                "map-editor.menus.step-list.games.riptide-rush.items.introduction-arena.lore",
                                0)),
                Material.OAK_BOAT,
                StepCaptureType.SELECT);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        // The venue is shared by all Riptide maps, so it is an editor utility rather than a
        // per-map publication prerequisite.
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        return GuiConfig.text(
                "map-editor.menus.step-list.games.riptide-rush.items.introduction-arena.states.available.title");
    }

    @Override
    public void openSelection(
            @NotNull PrepareSessionManager manager,
            @NotNull Player player,
            @NotNull PrepareSession session) {
        RiptideIntroductionArena arena =
                session.getPlugin().isLoaded() && session.getPlugin().getGameManager() != null
                        ? session.getPlugin()
                                .getGameManager()
                                .getRiptideRushManager()
                                .getIntroductionArena()
                        : null;
        Location destination = arena == null ? null : arena.editorSpawn();
        if (destination == null || destination.getWorld() == null) {
            CoreMessages.sendAdminError(
                    player, MessageConfig.MAP_EDITOR_RIPTIDE_INTRODUCTION_ARENA_UNAVAILABLE);
            return;
        }
        player.closeInventory();
        if (!player.teleport(destination)) {
            CoreMessages.sendAdminError(
                    player, MessageConfig.MAP_EDITOR_RIPTIDE_INTRODUCTION_ARENA_TELEPORT_FAILED);
            return;
        }
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        CoreMessages.sendAdminInfo(
                player, MessageConfig.MAP_EDITOR_RIPTIDE_INTRODUCTION_ARENA_TELEPORTED);
    }
}
