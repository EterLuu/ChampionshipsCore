package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.dao.TeamDaoImpl;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ChampionshipTeam {
    private final Set<UUID> members = new HashSet<>();
    @Getter
    private int id;
    @Getter
    private String name;
    @Getter
    private String colorName;
    @Getter
    private String colorCode;
    @Getter
    private Team team;
    private TeamDaoImpl teamDao;

    private ChampionshipTeam() {
    }

    protected ChampionshipTeam(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, Team team) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.team = team;
        this.teamDao = new TeamDaoImpl();
    }

    protected ChampionshipTeam(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members, Team team) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.addMembers(members);
        this.team = team;
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

    public List<ChampionshipPlayer> getOnlineCCPlayers() {
        List<ChampionshipPlayer> list = new ArrayList<>();
        for (UUID uuid : members) {
            ChampionshipPlayer championshipPlayer = ChampionshipsCore.getInstance().getPlayerManager().getPlayer(uuid);
            list.add(championshipPlayer);
        }
        return list;
    }

    public void sendMessageToAll(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCPlayers()) {
            championshipPlayer.sendMessage(message);
        }
    }

    public void sendTitleToAll(String title, String subTitle) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCPlayers()) {
            championshipPlayer.sendTitle(title, subTitle);
        }
    }

    public void sendActionBarToAll(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCPlayers()) {
            championshipPlayer.sendActionBar(message);
        }
    }

    public void teleportAllPlayers(Location location) {
        for (Player player : getOnlinePlayers()) {
            player.teleport(location);
        }
    }

    public void changeLevelForAll(int level) {
        for (Player player : getOnlinePlayers()) {
            player.setLevel(level);
        }
    }

    public void setGameModeForAllPlayers(GameMode gameMode) {
        for (Player player : getOnlinePlayers()) {
            player.setGameMode(gameMode);
        }
    }

    public void cleanInventoryForAllPlayers() {
        for (Player player : getOnlinePlayers()) {
            player.getInventory().clear();
        }
    }

    public void playSoundToAllPlayers(Sound sound, float volume, float pitch) {
        for (Player player : getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public ItemStack getWool() {
        Material woolMaterial = Material.getMaterial(colorName + "_WOOL");
        if (woolMaterial == null)
            return null;
        ItemStack wool = new ItemStack(woolMaterial);
        wool.setAmount(64);
        return wool;
    }

    public ItemStack getBoots() {
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta bootsMeta = boots.hasItemMeta() ? boots.getItemMeta() : Bukkit.getItemFactory().getItemMeta(boots.getType());
        LeatherArmorMeta bootsArmorMeta = (LeatherArmorMeta) bootsMeta;
        if (bootsArmorMeta != null) {
            bootsArmorMeta.setColor(Utils.hex2rgb(colorCode));
            boots.setItemMeta(bootsArmorMeta);
        }
        return boots;
    }

    public ItemStack getHelmet() {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta helmetMeta = helmet.hasItemMeta() ? helmet.getItemMeta() : Bukkit.getItemFactory().getItemMeta(helmet.getType());
        LeatherArmorMeta helmetArmorMeta = (LeatherArmorMeta) helmetMeta;
        if (helmetArmorMeta != null) {
            helmetArmorMeta.setColor(Utils.hex2rgb(colorCode));
            helmet.setItemMeta(helmetArmorMeta);
        }
        return helmet;
    }

    public String getColoredName() {
        return Utils.translateColorCodes(colorCode + name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof ChampionshipTeam))
            return false;
        return this.name.equals(((ChampionshipTeam) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
