package ink.ziip.championshipscore.api.team;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.entry.TeamMemberEntry;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChampionshipTeam {
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> memberNames = new ConcurrentHashMap<>();
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

    private ChampionshipTeam() {
    }

    protected ChampionshipTeam(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, Team team) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.team = team;
    }

    protected ChampionshipTeam(int id, @NotNull String name, @NotNull String colorName, @NotNull String colorCode, @NotNull Set<UUID> members, Team team) {
        this.id = id;
        this.name = name;
        this.colorName = colorName;
        this.colorCode = colorCode;
        this.addMembers(members);
        this.team = team;
    }

    protected ChampionshipTeam(int id, @NotNull String name, @NotNull String colorName,
                               @NotNull String colorCode, @NotNull Map<UUID, String> members, Team team) {
        this(id, name, colorName, colorCode, team);
        members.forEach(this::addMember);
    }

    protected boolean addMember(@NotNull UUID uuid) {
        return addMember(uuid, "unknown");
    }

    protected boolean addMember(@NotNull UUID uuid, @NotNull String username) {
        memberNames.put(uuid, username);
        return members.add(uuid);
    }

    protected void addMembers(@NotNull Set<UUID> members) {
        for (UUID uuid : members) {
            addMember(uuid);
        }
    }

    protected boolean deleteMember(@NotNull UUID uuid) {
        memberNames.remove(uuid);
        return members.remove(uuid);
    }

    protected boolean deleteMember(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return deleteMember(uuid);
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
    }

    public List<String> getTeamMemberNameList() {
        return getTeamMemberEntries().stream()
                .map(TeamMemberEntry::getUsername)
                .toList();
    }

    /** Returns the authoritative persisted identity for every member, including offline players. */
    public List<TeamMemberEntry> getTeamMemberEntries() {
        return members.stream()
                .map(uuid -> new TeamMemberEntry(0, uuid, memberNames.getOrDefault(uuid, "unknown"), id))
                .sorted(Comparator.comparing(TeamMemberEntry::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<UUID> getOfflineMembers() {
        List<UUID> offlineMembers = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                offlineMembers.add(uuid);
            }
        }

        return offlineMembers;
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
            if (player != null) {
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
            // Lifecycle transitions are already driven from the server thread. Applying the mode
            // synchronously keeps it ordered with the inventory clear and teleport in the same phase.
            if (Bukkit.isPrimaryThread()) {
                player.setGameMode(gameMode);
            } else {
                ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    if (player.isOnline()) player.setGameMode(gameMode);
                });
            }
        }
    }

    public void setHealthForAllPlayers(double health) {
        for (Player player : getOnlinePlayers()) {
            player.setHealth(health);
        }
    }

    public void setFoodLevelForAllPlayers(int level) {
        for (Player player : getOnlinePlayers()) {
            player.setFoodLevel(level);
        }
    }

    public void clearEffectsForAllPlayers() {
        for (Player player : getOnlinePlayers()) {
            for (PotionEffect potionEffect : player.getActivePotionEffects())
                player.removePotionEffect(potionEffect.getType());
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

    public ItemStack getConcrete() {
        Material concreteMaterial = Material.getMaterial(colorName + "_CONCRETE");
        if (concreteMaterial == null)
            return null;
        ItemStack concrete = new ItemStack(concreteMaterial);
        concrete.setAmount(64);
        return concrete;
    }

    public ItemStack getHelmet() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        return getItemStack(item);
    }

    @NotNull
    private ItemStack getItemStack(ItemStack item) {
        ItemMeta itemMeta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
        LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) itemMeta;
        if (leatherArmorMeta != null) {
            leatherArmorMeta.setColor(Utils.hex2rgb(colorCode));
            item.setItemMeta(leatherArmorMeta);
        }
        return item;
    }

    public ItemStack getLeggings() {
        ItemStack item = new ItemStack(Material.LEATHER_LEGGINGS);
        return getItemStack(item);
    }

    public ItemStack getBoots() {
        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        return getItemStack(item);
    }

    public ItemStack getChestPlate() {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        return getItemStack(item);
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
        ChampionshipTeam other = (ChampionshipTeam) o;
        // DAILY creates one transient team per match and intentionally reuses the visible colour
        // names (红队/绿队/...). Those runtime teams must not collide in GameManager's ownership
        // maps merely because they render with the same name. Persisted/formal teams retain the
        // historical name-based identity semantics.
        if (this.id < 0 || other.id < 0)
            return false;
        return this.name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return id < 0 ? System.identityHashCode(this) : name.hashCode();
    }
}
