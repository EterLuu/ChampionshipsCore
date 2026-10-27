package ink.ziip.championshipscore.api.game.parkourwarrior.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.config.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

class ParkourWarriorPreparationTest {
    @ParameterizedTest
    @EnumSource(
            value = GameStageEnum.class,
            names = {"LOADING", "PREPARATION", "COUNTDOWN", "PROGRESS"})
    void missingProgressForRosterMembersHasZeroScore(GameStageEnum stage) throws Exception {
        var area = allocate(ParkourWarriorTeamArea.class);
        set(area, "gamePlayers", new ArrayList<>(List.of(UUID.randomUUID(), UUID.randomUUID())));
        set(area, "playerCheckpointProgress", new HashMap<>());
        set(area, "gameConfig", allocate(ParkourWarriorConfig.class));
        set(area, "gameStageEnum", stage);
        assertDoesNotThrow(area::calculatePoints);
    }

    @Test
    void startEquipmentTargetsOnlyOnlineGameplayParticipants() throws Exception {
        Object old = field(Bukkit.class, "server").get(null);
        try {
            var plugin = allocate(ChampionshipsCore.class);
            var manager = allocate(GameManager.class);
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(plugin, "gameManager", manager);
            var teams = allocate(Teams.class);
            teams.team = allocate(Team.class);
            teams.team.boots = allocate(ItemStack.class);
            set(plugin, "teamManager", teams);
            UUID active = UUID.randomUUID(),
                    eliminated = UUID.randomUUID(),
                    external = UUID.randomUUID(),
                    offline = UUID.randomUUID();
            Map<UUID, ItemStack> equipped = new HashMap<>();
            Map<UUID, Player> online = new HashMap<>();
            for (UUID id : List.of(active, eliminated, external)) {
                var inventory =
                        proxy(
                                PlayerInventory.class,
                                (p, m, a) -> {
                                    if (m.getName().equals("setBoots"))
                                        equipped.put(id, (ItemStack) a[0]);
                                    else throw new AssertionError(m.getName());
                                    return null;
                                });
                online.put(
                        id,
                        proxy(
                                Player.class,
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getGameMode" ->
                                                    id.equals(active)
                                                            ? GameMode.ADVENTURE
                                                            : GameMode.SPECTATOR;
                                            case "getInventory" -> inventory;
                                            default -> throw new AssertionError(m.getName());
                                        }));
            }
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) -> {
                                        if (m.getName().equals("getPlayer"))
                                            return online.get(a[0]);
                                        throw new AssertionError(m.getName());
                                    }));
            var area = allocate(ParkourWarriorTeamArea.class);
            set(area, "plugin", plugin);
            set(area, "gamePlayers", new ArrayList<>(List.of(active, eliminated, offline)));
            set(area, "spectators", new HashSet<>(Set.of(external)));
            var give = ParkourWarriorTeamArea.class.getDeclaredMethod("giveBootToAllPlayers");
            give.setAccessible(true);
            give.invoke(area);
            assertEquals(Set.of(active), equipped.keySet());
            assertSame(teams.team.boots, equipped.get(active));
        } finally {
            field(Bukkit.class, "server").set(null, old);
        }
    }

    private static final class Teams extends TeamManager {
        Team team;

        private Teams() {
            super(null);
        }

        @Override
        public ChampionshipTeam getTeamByPlayer(Player player) {
            return team;
        }
    }

    private static final class Team extends ChampionshipTeam {
        ItemStack boots;

        private Team() {
            super(0, "fixture", "red", "", null);
        }

        @Override
        public ItemStack getBoots() {
            return boots;
        }
    }
}
