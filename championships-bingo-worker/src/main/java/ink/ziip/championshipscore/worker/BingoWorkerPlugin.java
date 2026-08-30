package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.BingoPortalRouter;
import ink.ziip.championshipscore.platform.bukkit.proxy.PluginMessagePlayerRouter;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.NativeTeamService;
import ink.ziip.championshipscore.redis.RedisMatchConsumer;
import ink.ziip.championshipscore.redis.RedisMatchTransport;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;

/** Dedicated Folia execution plugin. ChampionshipsCore remains the authoritative control plane. */
public final class BingoWorkerPlugin extends JavaPlugin {
    private WorkerConfig workerConfig;
    private RedisMatchTransport transport;
    private DurableEventOutbox outbox;
    private RedisMatchConsumer consumer;
    private PluginMessagePlayerRouter router;
    private WorkerReturnRouter returnRouter;
    private WorkerMatchRegistry registry;
    private PlatformScheduler scheduler;
    private WorkerChampionshipPlaceholder placeholder;
    private WorkerWorldController worlds;
    private WorkerChatService chat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            workerConfig = WorkerConfig.load(getConfig());
        } catch (RuntimeException invalid) {
            getLogger().log(Level.SEVERE, "Invalid Bingo worker configuration", invalid);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!workerConfig.enabled()) {
            getLogger().warning("Bingo worker is disabled in config.yml; Redis and match runtime were not started");
            return;
        }

        scheduler = new PlatformScheduler(this);
        // Folia invokes plugin enablement from the server thread, which is not the
        // global region thread. World creation and game-rule changes must run there.
        scheduler.runGlobal(this::enableRuntime);
    }

    private void enableRuntime() {
        if (!isEnabled()) return;

        try {
            worlds = new WorkerWorldController(this, workerConfig);
            if (!loadWorlds()) {
                throw new IllegalStateException("Unable to load all configured Bingo dimensions");
            }
            transport = new RedisMatchTransport(workerConfig.redis());
            outbox = new DurableEventOutbox(transport, getDataFolder().toPath().resolve("outbox"));
            outbox.initialize();
            router = new PluginMessagePlayerRouter(this, workerConfig.proxyChannel());
            returnRouter = new WorkerReturnRouter(this, router, workerConfig.returnServer());
            registry = new WorkerMatchRegistry(this, workerConfig, outbox, returnRouter, worlds,
                    NativeTeamService.mainScoreboard());
            chat = new WorkerChatService(this, workerConfig, registry);
            if (getCommand("cc") != null) getCommand("cc").setExecutor(new WorkerPlayCommand(registry));
            registerPlaceholderApi();
            getServer().getPluginManager().registerEvents(new WorkerListener(this, registry, chat), this);
            getServer().getPluginManager().registerEvents(new BingoPortalRouter(workerConfig.overworld(),
                    workerConfig.nether(), workerConfig.end()), this);
            consumer = new RedisMatchConsumer(workerConfig.redis(), workerConfig.consumer(),
                    workerConfig.redis().commandStream(), registry::handle,
                    error -> getLogger().log(Level.SEVERE, "Redis command consumer failure", error));
        } catch (IOException | RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Unable to initialize Bingo worker runtime", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        outbox.replay()
                .thenCompose(replayed -> {
                    if (replayed > 0) getLogger().info("Replayed durable Bingo events: " + replayed);
                    return consumer.start();
                })
                .thenCompose(ignored -> chat.start().exceptionally(failure -> {
                    getLogger().log(Level.WARNING,
                            "Cross-server chat bridge is unavailable; Bingo matches remain enabled", failure);
                    return null;
                }))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        getLogger().info("Bingo worker ready: " + workerConfig.workerId());
                        scheduler.runGlobalTimer(this::scanOnlinePlayers, 20L, 20L);
                    } else {
                        getLogger().log(Level.SEVERE, "Unable to start Redis command consumer", failure);
                        scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
                    }
                });
    }

    private boolean loadWorlds() {
        return loadWorld(workerConfig.overworld(), World.Environment.NORMAL)
                && loadWorld(workerConfig.nether(), World.Environment.NETHER)
                && loadWorld(workerConfig.end(), World.Environment.THE_END);
    }

    private boolean loadWorld(String name, World.Environment environment) {
        World world = getServer().getWorld(name);
        if (world == null) world = new WorldCreator(name).environment(environment).createWorld();
        if (world == null) return false;
        worlds.configureAndFreeze(world);
        return true;
    }

    private void scanOnlinePlayers() {
        if (registry == null) return;
        List<Player> players = List.copyOf(getServer().getOnlinePlayers());
        for (Player player : players) registry.requestObserve(player);
    }

    private void registerPlaceholderApi() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI is not installed; %cc_*% worker placeholders are disabled");
            return;
        }
        placeholder = new WorkerChampionshipPlaceholder(this, registry);
        if (!placeholder.register()) {
            getLogger().warning("Unable to register the worker %cc_*% PlaceholderAPI expansion");
            placeholder = null;
        }
    }

    @Override
    public void onDisable() {
        // Do not synchronously abort live matches here: Folia disables plugins off
        // the global/region threads and abort mutates worlds and players. The Core
        // heartbeat fence treats this worker as unavailable; accepted events have
        // already been persisted by the durable outbox.
        if (placeholder != null) placeholder.unregister();
        placeholder = null;
        if (consumer != null) consumer.close();
        if (chat != null) chat.close();
        if (returnRouter != null) returnRouter.close();
        if (router != null) router.close();
        if (scheduler != null) scheduler.cancelGlobalAndAsyncTasks();
        if (outbox != null) outbox.closeGracefully(Duration.ofSeconds(5));
        else if (transport != null) transport.close();
    }
}
