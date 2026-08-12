package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TGTTOSTeamArea extends BaseMultiTeamGameInstance {
    private static final int ELYTRA_START_DOWNWARD_SAFE_BLOCKS = 3;
    @Getter
    private final List<BlockState> blockStates = new ArrayList<>();
    @Getter
    private final List<UUID> arrivedPlayers = new ArrayList<>();
    /** Players who have left the launch surface and are eligible for a landing reset. */
    private final Set<UUID> elytraAirbornePlayers = ConcurrentHashMap.newKeySet();
    private final Map<ChampionshipTeam, Integer> teamArrivedPlayers = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private int arrivedTeamNumbers = 0;

    public TGTTOSTeamArea(ChampionshipsCore plugin, TGTTOSConfig tgttosConfig) {
        super(plugin, GameTypeEnum.TGTTOS, new TGTTOSHandler(plugin), tgttosConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setTgttosTeamArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void resetArea() {
        cleanDroppedItems();

        arrivedPlayers.clear();
        elytraAirbornePlayers.clear();
        teamArrivedPlayers.clear();

        arrivedTeamNumbers = 0;

        for (BlockState blockState : blockStates) {
            blockState.setType(Material.AIR);
            blockState.update(true);
        }

        blockStates.clear();

        World world = getSpectatorSpawnLocation().getWorld();
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        BoundingBox boundingBox = new BoundingBox(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
        if (world != null) {
            for (Entity entity : world.getNearbyEntities(boundingBox)) {
                if (entity instanceof Boat) {
                    entity.remove();
                }
                if (entity instanceof Stray) {
                    entity.remove();
                }
                if (entity instanceof Chicken) {
                    entity.remove();
                }
            }
        }

        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.TGTTOS_START_PREPARATION,
                MessageConfig.TGTTOS_START_PREPARATION_TITLE, MessageConfig.TGTTOS_START_PREPARATION_SUBTITLE);

        timer = 10;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            showPreparationCountdown(timer);

            if (timer == 0) {
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
                startGameProgress();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllPlayerToSpawnPoints();

        resetPlayerHealthFoodEffectLevelInventory();

        if (getGameConfig().getAreaType().equals("BOAT")) {
            changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            giveBoatToAllPlayers();
        }
        if (getGameConfig().getAreaType().equals("ROAD")) {
            changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            giveRoadToolsToAllPlayers();
        }
        if (getGameConfig().getAreaType().equals("ELYTRA")) {
            changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
            giveElytraToAllPlayers();
        }
        if (getGameConfig().getAreaType().equals("NONE")) {
            changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
            giveTeamArmorToAllPlayers();
        }

        startFinalCountdown(MessageConfig.TGTTOS_GAME_START_SOON_TITLE,
                MessageConfig.TGTTOS_GAME_START_TITLE, MessageConfig.TGTTOS_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        spawnChicken();
        spawnMonsters();
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            updateGameTimerBossBar(MessageConfig.TGTTOS_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer)), timer, getGameConfig().getTimer());
        }, this::endGame);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return gameConfig.getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        cleanInventoryForAllGamePlayers();

        announceGameEnd(MessageConfig.TGTTOS_GAME_END_TITLE, MessageConfig.TGTTOS_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
    }

    public void playerArrivedAtEndPoint(Player player) {
        UUID uuid = player.getUniqueId();
        if (!arrivedPlayers.contains(uuid)) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null)
                return;

            int placement = arrivedPlayers.size();
            addPlayerPoints(uuid, Math.max(0, 64 - placement));

            if (placement < 12) {
                addPlayerPoints(uuid, 80 - 5 * placement);
            }

            arrivedPlayers.add(uuid);

            addTeamArrivedPlayer(championshipTeam);

            sendMessageToAllGamePlayers(MessageConfig.TGTTOS_ARRIVED_AT_POINT
                    .replace("%player%", Utils.formatPlayerName(player)));
        }
    }

    public void addTeamArrivedPlayer(ChampionshipTeam championshipTeam) {
        teamArrivedPlayers.put(championshipTeam, teamArrivedPlayers.getOrDefault(championshipTeam, 0) + 1);
        int finishedPlayerCount = teamArrivedPlayers.get(championshipTeam);
        if (finishedPlayerCount == championshipTeam.getMembers().size()) {
            if (arrivedTeamNumbers < 4) {
                addPlayerPointsToAllTeamMembers(championshipTeam, 24 - 6 * arrivedTeamNumbers);
                sendMessageToAllGamePlayers(MessageConfig.TGTTOS_TEAM_ARRIVED_AT_POINT.replace("%team%", championshipTeam.getColoredName()));
            }
            arrivedTeamNumbers++;
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS
                && getGameConfig().getAreaType().equals("ELYTRA")
                && !arrivedPlayers.contains(player.getUniqueId())) {
            scheduler.runTask(plugin, () -> {
                player.spigot().respawn();
                scheduler.runTask(plugin, () -> resetElytraPlayerToStart(player, "发生事故"));
            });
            return;
        }

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });
        player.teleport(getSpectatorSpawnLocation());
    }

    /** Terra Swoop Force failures restart the flight from the configured launch area. */
    public void resetElytraPlayerToStart(@NotNull Player player, @NotNull String reason) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !getGameConfig().getAreaType().equals("ELYTRA")
                || arrivedPlayers.contains(player.getUniqueId())) {
            return;
        }
        elytraAirbornePlayers.remove(player.getUniqueId());
        player.setGliding(false);
        player.setVelocity(new Vector());
        player.setFallDistance(0F);
        player.setGameMode(GameMode.ADVENTURE);
        giveElytraToPlayer(player);
        Location spawn = randomSpawnLocation(getGameConfig().getPlayerSpawnAreaPos1(),
                getGameConfig().getPlayerSpawnAreaPos2(), getGameConfig().getPlayerSpawnYaw(),
                getGameConfig().getPlayerSpawnPitch());
        if (spawn != null) player.teleport(spawn);
        Utils.sendActionBar(player, "&#fff566" + reason + "，返回起点");
    }

    /**
     * Records the flight phase and reports a real landing after the player has actually entered Elytra
     * gliding. Ordinary steps, small drops, and walking off the launch platform must not count as a flight
     * landing.
     */
    public boolean updateElytraLandingState(@NotNull Player player) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !getGameConfig().getAreaType().equals("ELYTRA")
                || isManagedSpectator(player)
                || arrivedPlayers.contains(player.getUniqueId())) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (player.isGliding()) {
            elytraAirbornePlayers.add(uuid);
            return false;
        }
        if (!player.isOnGround()) {
            return false;
        }
        return elytraAirbornePlayers.remove(uuid) && !isElytraLandingSafe(player.getLocation());
    }

    /** Start height is always safe; the finish platform gets a fifteen-block buffer on every axis. */
    private boolean isElytraLandingSafe(@NotNull Location location) {
        if (location.getWorld() == null || getSpectatorSpawnLocation().getWorld() == null
                || !location.getWorld().equals(getSpectatorSpawnLocation().getWorld())) return false;
        Vector start1 = getGameConfig().getPlayerSpawnAreaPos1();
        Vector start2 = getGameConfig().getPlayerSpawnAreaPos2();
        int playerFeetY = location.getBlockY();
        if (start1 != null && start2 != null && start1.getBlockY() == start2.getBlockY()
                && playerFeetY == start1.getBlockY() + 1) {
            return true;
        }
        if (start1 != null && start2 != null && start1.getBlockY() == start2.getBlockY()
                && location.getBlockX() >= Math.min(start1.getBlockX(), start2.getBlockX())
                && location.getBlockX() <= Math.max(start1.getBlockX(), start2.getBlockX())
                && location.getBlockZ() >= Math.min(start1.getBlockZ(), start2.getBlockZ())
                && location.getBlockZ() <= Math.max(start1.getBlockZ(), start2.getBlockZ())
                && playerFeetY >= start1.getBlockY() + 1 - ELYTRA_START_DOWNWARD_SAFE_BLOCKS) {
            return true;
        }

        return isElytraFinishSafeLocation(location);
    }

    /**
     * Tests the whole finish platform volume expanded by fifteen blocks on every axis. The upper bounds
     * include the platform's far block edge, so every block in a rectangular selection is protected.
     */
    public boolean isElytraFinishSafeLocation(@NotNull Location location) {
        Vector finish1 = getGameConfig().getChickenSpawnAreaPos1();
        Vector finish2 = getGameConfig().getChickenSpawnAreaPos2();
        if (finish1 == null || finish2 == null || location.getWorld() == null
                || getSpectatorSpawnLocation().getWorld() == null
                || !location.getWorld().equals(getSpectatorSpawnLocation().getWorld())) return false;
        double minX = Math.min(finish1.getX(), finish2.getX()) - 15D;
        double maxX = Math.max(finish1.getX(), finish2.getX()) + 16D;
        double minY = Math.min(finish1.getY(), finish2.getY()) - 15D;
        double maxY = Math.max(finish1.getY(), finish2.getY()) + 16D;
        double minZ = Math.min(finish1.getZ(), finish2.getZ()) - 15D;
        double maxZ = Math.max(finish1.getZ(), finish2.getZ()) + 16D;
        return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        // Disconnecting is not an elimination in Terra Swoop Force; a reconnect starts from launch again.
        elytraAirbornePlayers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.ADVENTURE);
            });
        }
        if (getGameStageEnum() == GameStageEnum.COUNTDOWN || getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.teleport(getSpectatorSpawnLocation());
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            if (getGameConfig().getAreaType().equals("ELYTRA")
                    && arrivedPlayers.contains(player.getUniqueId())) {
                championshipsCore.getServer().getScheduler().runTask(championshipsCore,
                        () -> player.setGameMode(GameMode.SPECTATOR));
                return;
            }
            switch (getGameConfig().getAreaType()) {
                case "BOAT" -> championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.SURVIVAL);
                    giveBoatToPlayer(player);
                });
                case "ROAD" -> championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.SURVIVAL);
                    giveRoadToolToPlayer(player);
                });
                case "ELYTRA" -> championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                    giveElytraToPlayer(player);
                    Location spawn = randomSpawnLocation(getGameConfig().getPlayerSpawnAreaPos1(),
                            getGameConfig().getPlayerSpawnAreaPos2(), getGameConfig().getPlayerSpawnYaw(),
                            getGameConfig().getPlayerSpawnPitch());
                    if (spawn != null) player.teleport(spawn);
                });
                default -> championshipsCore.getServer().getScheduler().runTask(championshipsCore,
                        () -> {
                            player.setGameMode(GameMode.ADVENTURE);
                            giveTeamArmorToPlayer(player);
                        });
            }
        }
    }

    private void giveRoadToolsToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            giveRoadToolToPlayer(player);
        }
    }

    public void giveRoadToolToPlayer(Player player) {
        if (player != null) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();

            ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta itemMeta = pickaxe.getItemMeta();
            if (itemMeta != null)
                itemMeta.setUnbreakable(true);
            pickaxe.setItemMeta(itemMeta);
            inventory.setItem(0, pickaxe);
            inventory.setHeldItemSlot(0);

            ItemStack blocks;
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam != null) {
                blocks = championshipTeam.getConcrete();
            } else {
                blocks = new ItemStack(Material.COBBLESTONE);
            }
            blocks.setAmount(64);
            itemMeta = blocks.getItemMeta();
            if (itemMeta != null)
                itemMeta.setUnbreakable(true);
            blocks.setItemMeta(itemMeta);
            inventory.setItemInOffHand(blocks);
            giveTeamArmorToPlayer(player);
            player.updateInventory();
        }
    }

    public int getArrivedPlayerNums() {
        return arrivedPlayers.size();
    }

    public int getPlayerTeamNotArrived(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        if (championshipTeam == null)
            return 0;

        return championshipTeam.getMembers().size() - teamArrivedPlayers.getOrDefault(championshipTeam, 0);
    }

    private void giveBoatToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                giveBoatToPlayer(player);
            }
        }
    }

    public void giveBoatToPlayer(Player player) {
        ItemStack itemStack = new ItemStack(Material.OAK_BOAT);
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();
        playerInventory.addItem(itemStack);
        giveTeamArmorToPlayer(player);
        player.updateInventory();
    }

    private void giveElytraToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) giveElytraToPlayer(player);
        }
    }

    public void giveElytraToPlayer(Player player) {
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setChestplate(null);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            elytra.setItemMeta(meta);
        }
        inventory.setChestplate(elytra);
        giveTeamArmorToPlayer(player);
        player.updateInventory();
    }

    private void giveTeamArmorToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            giveTeamArmorToPlayer(Bukkit.getPlayer(uuid));
        }
    }

    private void giveTeamArmorToPlayer(Player player) {
        if (player == null) return;
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null) return;
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(championshipTeam.getHelmet());
        inventory.setBoots(championshipTeam.getBoots());
    }

    public void teleportPlayerToSpawnPoint(Player player) {
        player.teleport(getSpectatorSpawnLocation());
    }

    private void teleportAllPlayerToSpawnPoints() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Location spawn = randomSpawnLocation(getGameConfig().getPlayerSpawnAreaPos1(),
                        getGameConfig().getPlayerSpawnAreaPos2(), getGameConfig().getPlayerSpawnYaw(),
                        getGameConfig().getPlayerSpawnPitch());
                if (spawn != null) player.teleport(spawn);
            }
        }
    }

    private void spawnMonsters() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;
        List<String> monsterSpawnPoints = getGameConfig().getMonsterSpawnPoints();
        if (monsterSpawnPoints == null) return;
        for (String stringLocation : monsterSpawnPoints) {
            LivingEntity entity = (LivingEntity) world.spawnEntity(Utils.getLocation(stringLocation), EntityType.STRAY);
            entity.setRemoveWhenFarAway(false);
        }
    }

    private void spawnChicken() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;

        for (UUID uuid : gamePlayers) {
            Location spawn = randomSpawnLocation(getGameConfig().getChickenSpawnAreaPos1(),
                    getGameConfig().getChickenSpawnAreaPos2(), 0F, 0F);
            if (spawn == null) continue;
            LivingEntity entity = (LivingEntity) world.spawnEntity(spawn, EntityType.CHICKEN);
            entity.setRemoveWhenFarAway(false);
        }
    }

    private Location randomSpawnLocation(Vector pos1, Vector pos2, Float yaw, Float pitch) {
        if (pos1 == null || pos2 == null || pos1.getBlockY() != pos2.getBlockY()) return null;
        Location spectatorSpawn = getSpectatorSpawnLocation();
        World world = spectatorSpawn == null ? null : spectatorSpawn.getWorld();
        if (world == null) return null;

        double minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        double maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1D;
        double minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        double maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1D;
        int minBlockX = (int) minX;
        int maxBlockX = (int) maxX - 1;
        int minBlockZ = (int) minZ;
        int maxBlockZ = (int) maxZ - 1;
        int spawnY = pos1.getBlockY() + 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = random.nextInt(minBlockX, maxBlockX + 1);
            int z = random.nextInt(minBlockZ, maxBlockZ + 1);
            if (world.getBlockAt(x, spawnY, z).getType().isAir()) {
                return new Location(world, x + 0.5D, spawnY,
                        z + 0.5D, yaw == null ? 0F : yaw, pitch == null ? 0F : pitch);
            }
        }

        // Small or heavily obstructed selections get a deterministic fallback scan so no blocked cell
        // is ever returned merely because random sampling missed the remaining open cells.
        long totalCells = (long) (maxBlockX - minBlockX + 1) * (maxBlockZ - minBlockZ + 1);
        long scanLimit = Math.min(totalCells, 4096L);
        for (long index = 0; index < scanLimit; index++) {
            int width = maxBlockX - minBlockX + 1;
            int x = minBlockX + (int) (index % width);
            int z = minBlockZ + (int) (index / width);
            if (world.getBlockAt(x, spawnY, z).getType().isAir()) {
                return new Location(world, x + 0.5D, spawnY,
                        z + 0.5D, yaw == null ? 0F : yaw, pitch == null ? 0F : pitch);
            }
        }
        return null;
    }

    @Override
    public TGTTOSConfig getGameConfig() {
        return (TGTTOSConfig) gameConfig;
    }

    @Override
    public TGTTOSHandler getGameHandler() {
        return (TGTTOSHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
