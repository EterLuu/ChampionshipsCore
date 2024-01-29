package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

public class DragonEggCarnivalArea extends BaseTeamArea {
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private int round;
    @Getter
    private int rightTeamPoints;
    @Getter
    private int leftTeamPoints;
    private Block dragonEgg;

    public DragonEggCarnivalArea(ChampionshipsCore plugin, DragonEggCarnivalConfig dragonEggCarnivalConfig, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.DragonEggCarnival, new DragonEggCarnivalHandler(plugin), dragonEggCarnivalConfig);

        getGameHandler().setDragonEggCarnivalArea(this);

        if (!firstTime) {
            loadMap(areaName);
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    @Override
    public void resetArea() {
        round = 0;
        rightTeamPoints = 0;
        leftTeamPoints = 0;

        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        rightChampionshipTeam.teleportAllPlayers(getGameConfig().getRightSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(getGameConfig().getLeftSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_SUBTITLE);

        timer = -4;

        dragonEgg = getGameConfig().getDragonEggSpawnPoint().getBlock();
        dragonEgg.setType(Material.DRAGON_EGG, true);

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        rightChampionshipTeam.teleportAllPlayers(getGameConfig().getRightSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(getGameConfig().getLeftSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer < 0) {
                String countDown = MessageConfig.DRAGON_EGG_CARNIVAL_COUNT_DOWN
                        .replace("%time%", String.valueOf(-timer));
                sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
                changeLevelForAllGamePlayers(-timer);
            } else {
                changeLevelForAllGamePlayers(timer);
            }

            if (timer == 0) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            if (timer % 20 == 0) {
                giveRandomKitToTeamMembers(rightChampionshipTeam);
                giveRandomKitToTeamMembers(leftChampionshipTeam);
            }


            if (timer == 80 || timer == 90 | timer == 95) {
                String message = MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWN_SOON.replace("%time%", String.valueOf(100 - timer));
                sendMessageToAllGamePlayers(message);
                sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWN_SOON_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWN_SOON_SUBTITLE);
            }

            if (timer == 100) {
                dragonEgg.setType(Material.AIR, true);
                sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWNED);
                Location location = getGameConfig().getDragonSpawnPoint();
                World world = location.getWorld();
                if (world != null) {
                    EnderDragon dragon = (EnderDragon) world.spawnEntity(location, EntityType.ENDER_DRAGON);
                    dragon.setHealth(60);
                    dragon.setPhase(EnderDragon.Phase.LEAVE_PORTAL);
                }
                giveDragonItemToAllGamePlayers();
            }

            sendActionBarToAllGameSpectators(MessageConfig.DRAGON_EGG_CARNIVAL_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            timer++;
        }, 0, 20L);
    }

    protected void endGameInForm(ChampionshipTeam championshipTeam) {
        setGameStageEnum(GameStageEnum.STOPPING);

        cleanInventoryForAllGamePlayers();

        changeGameModelForAllGamePlayers(GameMode.SPECTATOR);

        round++;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        if (championshipTeam.equals(rightChampionshipTeam))
            rightTeamPoints += 1;
        if (championshipTeam.equals(leftChampionshipTeam))
            leftTeamPoints += 1;

        if (rightTeamPoints == 3 || leftTeamPoints == 3) {
            endGame();
        } else {
            loadMap(getGameConfig().getAreaName());
            sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_RESTART);
            sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_RESTART_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_RESTART_SUBTITLE);

            timer = 10;
            scheduler.runTaskTimer(plugin, (task) -> {
                if (timer == 0) {
                    startGameProgress();
                    task.cancel();
                }

                changeLevelForAllGamePlayers(timer);

                timer--;
            }, 0, 20L);
        }
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        rightChampionshipTeam.teleportAllPlayers(getLobbyLocation());
        leftChampionshipTeam.teleportAllPlayers(getLobbyLocation());

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        calculatePoints();

        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));

        resetGame();
    }

    protected void calculatePoints() {
        if (rightTeamPoints >= 3) {
            Utils.sendMessageToAllPlayers(MessageConfig.DRAGON_EGG_CARNIVAL_WIN.replace("%team%", rightChampionshipTeam.getColoredName()));
            giveGoldenHelmetToTeamPlayers(rightChampionshipTeam);
        } else if (leftTeamPoints >= 3) {
            Utils.sendMessageToAllPlayers(MessageConfig.DRAGON_EGG_CARNIVAL_WIN.replace("%team%", leftChampionshipTeam.getColoredName()));
            giveGoldenHelmetToTeamPlayers(leftChampionshipTeam);
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                teleportPlayerToSpawnLocation(event.getEntity());
                event.getEntity().setGameMode(GameMode.ADVENTURE);
            });

            return;
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            teleportPlayerToSpawnLocation(event.getEntity());
            event.getEntity().setGameMode(GameMode.SURVIVAL);
        });

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {

            Player assailant = player.getKiller();
            EntityDamageEvent entityDamageEvent = player.getLastDamageCause();

            if (assailant != null) {
                ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

                if (playerTeam == null || assailantTeam == null)
                    return;

                if (playerTeam.equals(assailantTeam)) {
                    return;
                }

                String message = MessageConfig.DRAGON_EGG_CARNIVAL_KILL_PLAYER;

                if (entityDamageEvent != null) {
                    EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                    if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                        message = MessageConfig.DRAGON_EGG_CARNIVAL_KILL_PLAYER_BY_VOID;
                    }
                }

                message = message
                        .replace("%player%", playerTeam.getColoredColor() + player.getName())
                        .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());

                sendMessageToAllGamePlayers(message);

            } else {

                String message = MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_DEATH;

                if (entityDamageEvent != null) {
                    EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                    if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                        message = MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_DEATH_BY_VOID;
                    }
                }

                message = message.replace("%player%", player.getName());
                sendMessageToAllGamePlayers(message);
            }
        }
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_LEAVE.replace("%player%", player.getName()));
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            return;
        }

        player.teleport(getGameConfig().getSpectatorSpawnPoint());
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void loadMap(String areaName) {
        if (!plugin.isLoaded())
            return;

        scheduler.runTaskAsynchronously(plugin, () -> {
            scheduler.runTask(plugin, () -> {
                setGameStageEnum(GameStageEnum.END);
                getGameHandler().unRegister();
                plugin.getLogger().log(Level.INFO, GameTypeEnum.DragonEggCarnival + ", " + areaName + ", start loading world " + getWorldName());
            });

            File target = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), "decarnival_" + areaName);

            // If already has a same world, delete it.
            if (target.isDirectory()) {
                String[] list = target.list();
                if (list != null && list.length > 0) {
                    plugin.getWorldManager().deleteWorld("decarnival_" + areaName, true);
                }
            }

            File maps = new File(plugin.getDataFolder(), "maps");
            File source = new File(maps, "decarnival_" + areaName);

            // Copy world files to destination
            plugin.getWorldManager().copyWorldFiles(source, target);

            // Load world
            plugin.getWorldManager().loadWorld("decarnival_" + areaName, World.Environment.THE_END, false);

            scheduler.runTask(plugin, () -> {
                getGameConfig().initializeConfiguration(plugin.getFolder());
                getGameHandler().register();
                setGameStageEnum(GameStageEnum.WAITING);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.DragonEggCarnival + ", " + areaName + ", world " + getWorldName() + " loaded");
            });
        });
    }

    public void saveMap() {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return;

        scheduler.runTaskAsynchronously(plugin, () -> {
            scheduler.runTask(plugin, () -> {
                setGameStageEnum(GameStageEnum.END);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.DragonEggCarnival + ", " + gameConfig.getAreaName() + ", start saving world " + getWorldName());
            });

            World editWorld = plugin.getServer().getWorld("decarnival_" + getWorldName());
            if (editWorld != null) {
                for (Player player : editWorld.getPlayers()) {
                    player.teleport(CCConfig.LOBBY_LOCATION);
                }

                // Unload world but not remove files
                scheduler.runTask(plugin, () -> {
                    plugin.getWorldManager().unloadWorld("decarnival_" + getWorldName(), true);
                });

                File dataDirectory = new File(plugin.getDataFolder(), "maps");
                File target = new File(dataDirectory, "decarnival_" + getWorldName());

                // Delete old world files stored in maps
                plugin.getWorldManager().deleteWorldFiles(target);

                File source = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), "decarnival_" + getWorldName());

                plugin.getWorldManager().copyWorldFiles(source, target);
                plugin.getWorldManager().deleteWorldFiles(source);
            }

            loadMap(getGameConfig().getAreaName());

            scheduler.runTask(plugin, () -> {
                getGameConfig().initializeConfiguration(plugin.getFolder());
                setGameStageEnum(GameStageEnum.WAITING);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.DragonEggCarnival + ", " + gameConfig.getAreaName() + ", saving world " + getWorldName() + " done");
            });
        });
    }

    private void giveItemToAllGamePlayers() {
        for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
            giveItemToPlayer(player);
            giveEffectToPlayer(player);
        }
        for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
            giveItemToPlayer(player);
            giveEffectToPlayer(player);
        }
    }

    private void giveDragonItemToAllGamePlayers() {
        for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
            giveDragonPhaseItemToPlayer(player);
        }
        for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
            giveDragonPhaseItemToPlayer(player);
        }
        clearEffectsForAllGamePlayers();
    }

    public void teleportPlayerToSpawnLocation(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            if (championshipTeam.equals(rightChampionshipTeam)) {
                player.teleport(getGameConfig().getRightSpawnPoint());
                if (getGameStageEnum() == GameStageEnum.PREPARATION) {
                    player.setGameMode(GameMode.ADVENTURE);
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                return;
            }
            if (championshipTeam.equals(leftChampionshipTeam)) {
                player.teleport(getGameConfig().getLeftSpawnPoint());
                if (getGameStageEnum() == GameStageEnum.PREPARATION) {
                    player.setGameMode(GameMode.ADVENTURE);
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                return;
            }
        }

        player.teleport(getGameConfig().getSpectatorSpawnPoint());
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void giveEffectToPlayer(Player player) {
        PotionEffect potionEffect = new PotionEffect(PotionEffectType.HEAL, PotionEffect.INFINITE_DURATION, 1);
        player.addPotionEffect(potionEffect);
    }

    private void giveGoldenHelmetToTeamPlayers(ChampionshipTeam championshipTeam) {
        ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.DURABILITY, 3);
        for (Player player : championshipTeam.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            inventory.setHelmet(helmet.clone());
        }
    }

    private void giveRandomKitToTeamMembers(ChampionshipTeam championshipTeam) {
        List<Player> players = championshipTeam.getOnlinePlayers();
        try {
            giveRandomKitToPlayer(players.get((new Random()).nextInt(players.size())));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void giveRandomKitToPlayer(Player player) {
        List<ItemStack> kits = new ArrayList<>(getGameConfig().getKits());

        try {
            player.getInventory().addItem(kits.get((new Random()).nextInt(kits.size())).clone());

            player.sendMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAIN_KIT);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void giveDragonPhaseItemToPlayer(Player player) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemStack bow = new ItemStack(Material.BOW);
        ItemStack bucket = new ItemStack(Material.WATER_BUCKET);
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemStack cookedBeef = new ItemStack(Material.COOKED_BEEF);
        cookedBeef.setAmount(64);

        bow.addEnchantment(Enchantment.ARROW_INFINITE, 1);
        bow.addEnchantment(Enchantment.ARROW_DAMAGE, 2);

        PlayerInventory inventory = player.getInventory();

        inventory.clear();
        inventory.addItem(sword, bow, bucket, arrow, cookedBeef);
    }

    private void giveItemToPlayer(Player player) {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 5);
        helmet.addUnsafeEnchantment(Enchantment.DURABILITY, 3);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        elytra.addUnsafeEnchantment(Enchantment.DURABILITY, 3);

        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        leggings.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 5);
        leggings.addUnsafeEnchantment(Enchantment.DURABILITY, 3);

        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        boots.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 5);
        boots.addUnsafeEnchantment(Enchantment.DURABILITY, 3);

        ItemStack bucket = new ItemStack(Material.WATER_BUCKET);

        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 3);
        pickaxe.addUnsafeEnchantment(Enchantment.DURABILITY, 3);

        ItemStack torch = new ItemStack(Material.TORCH);
        torch.setAmount(64);

        ItemStack cobweb = new ItemStack(Material.COBWEB);
        cobweb.setAmount(5);

        ItemStack cookedBeef = new ItemStack(Material.COOKED_BEEF);
        cookedBeef.setAmount(64);

        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(helmet.clone());
        inventory.setChestplate(elytra.clone());
        inventory.setLeggings(leggings.clone());
        inventory.setBoots(boots.clone());
        inventory.addItem(bucket.clone());
        inventory.addItem(pickaxe.clone());
        inventory.addItem(torch.clone());
        inventory.addItem(cobweb.clone());
        inventory.addItem(cookedBeef.clone());
    }

    public String getWorldName() {
        return "decarnival_" + getGameConfig().getAreaName();
    }

    @Override
    public DragonEggCarnivalConfig getGameConfig() {
        return (DragonEggCarnivalConfig) gameConfig;
    }

    @Override
    public DragonEggCarnivalHandler getGameHandler() {
        return (DragonEggCarnivalHandler) gameHandler;
    }
}
