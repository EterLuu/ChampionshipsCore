package ink.ziip.championshipscore.api.team;

import lombok.Getter;
import org.bukkit.Bukkit;
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

    private Team() {
    }

    protected Team(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
    }

    protected Team(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.addMembers(members);
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

    protected boolean deleteMember(@NotNull String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null)
            return false;
        return deleteMember(player.getUniqueId());
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
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
}
