package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.status.TeamStatusEnum;
import ink.ziip.championshipscore.api.player.CCPlayer;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Team {
    private final Set<UUID> members = new HashSet<>();
    @Getter
    private int id;
    @Getter
    private String name;
    @Getter
    private String colorName;
    @Getter
    private String colorCode;
    private TeamStatusEnum teamStatusEnum;
    private TeamDaoImpl teamDao;

    private Team() {
    }

    protected Team(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.teamStatusEnum = TeamStatusEnum.NONE;
        this.teamDao = new TeamDaoImpl();
    }

    protected Team(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.teamStatusEnum = TeamStatusEnum.NONE;
        this.addMembers(members);
        this.teamDao = new TeamDaoImpl();
    }

    protected boolean addMember(@NotNull UUID uuid) {
        synchronized (this) {
            if (members.contains(uuid)) {
                return false;
            }
            members.add(uuid);
        }
        return true;
    }

    protected boolean addMember(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return addMember(uuid);
    }

    protected boolean addMember(@NotNull String name) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        return addMember(offlinePlayer.getUniqueId());
    }

    protected void addMembers(@NotNull Set<UUID> members) {
        for (UUID uuid : members) {
            addMember(uuid);
        }
    }

    protected boolean deleteMember(@NotNull UUID uuid) {
        return members.remove(uuid);
    }

    protected boolean deleteMember(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return deleteMember(uuid);
    }

    @Deprecated
    protected boolean deleteMember(@NotNull String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null)
            return false;
        return deleteMember(player.getUniqueId());
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
    }

    public List<String> getTeamMemberNameList() {
        List<String> list = new ArrayList<>();
        for (TeamMemberEntry teamMemberEntry : teamDao.getTeamMembers(getId())) {
            list.add(teamMemberEntry.getUsername());
        }
        return list;
    }

    public boolean isTeamMember(@NotNull UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isTeamMember(@NotNull Player player) {
        return isTeamMember(player.getUniqueId());
    }

    public boolean isTeamMember(@NotNull OfflinePlayer offlinePlayer) {
        return isTeamMember(offlinePlayer.getUniqueId());
    }

    public List<Player> getOnlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                list.add(player);
            }
        }
        return list;
    }

    public List<CCPlayer> getOnlineCCPlayers() {
        List<CCPlayer> list = new ArrayList<>();
        for (UUID uuid : members) {
            CCPlayer ccPlayer = ChampionshipsCore.getInstance().getCcPlayerManager().getPlayer(uuid);
            list.add(ccPlayer);
        }
        return list;
    }

    public void sendMessageToAll(String message) {
        for (CCPlayer ccPlayer : getOnlineCCPlayers()) {
            ccPlayer.sendMessage(message);
        }
    }

    public void sendTitleToAll(String title, String subTitle) {
        for (CCPlayer ccPlayer : getOnlineCCPlayers()) {
            ccPlayer.sendTitle(title, subTitle);
        }
    }

    public void sendActionBarToAll(String message) {
        for (CCPlayer ccPlayer : getOnlineCCPlayers()) {
            ccPlayer.sendActionBar(message);
        }
    }

    public void teleportAllPlayers(Location location) {
        for (Player player : getOnlinePlayers()) {
            player.teleport(location);
        }
    }

    public void setGameModeForAllPlayers(GameMode gameMode) {
        for (Player player : getOnlinePlayers()) {
            player.setGameMode(gameMode);
        }
    }

    public void setTeamStatusEnum(TeamStatusEnum teamStatusEnum) {
        synchronized (this) {
            this.teamStatusEnum = teamStatusEnum;
        }
    }

    public TeamStatusEnum getTeamStatusEnum() {
        synchronized (this) {
            return this.teamStatusEnum;
        }
    }
}
