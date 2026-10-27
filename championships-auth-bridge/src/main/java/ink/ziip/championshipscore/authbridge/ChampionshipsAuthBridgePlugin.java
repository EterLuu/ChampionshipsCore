package ink.ziip.championshipscore.authbridge;

import ink.ziip.championshipscore.auth.AuthAdmissionOwner;
import ink.ziip.championshipscore.auth.BanPresentation;
import ink.ziip.championshipscore.authbridge.authme.AuthMeHashStore;
import ink.ziip.championshipscore.authbridge.bridge.BridgeApiClient;
import ink.ziip.championshipscore.authbridge.bridge.BridgeSynchronizer;
import ink.ziip.championshipscore.authbridge.bridge.BridgeUuidResolver;
import ink.ziip.championshipscore.authbridge.bridge.LocalAccessState;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

public final class ChampionshipsAuthBridgePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        String secret = getConfig().getString("api.hmac-secret", "");
        if (secret.equals("change-me")
                || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            secret = generateSecret();
            getConfig().set("api.hmac-secret", secret);
            saveConfig();
            getLogger()
                    .warning(
                            "api.hmac-secret was not configured; generated a new secret and saved"
                                    + " it to config.yml.");
            getLogger()
                    .warning(
                            "Configure the same value as BRIDGE_HMAC_SECRET on the cc-web"
                                    + " deployment (docker compose env file), otherwise bridge sync"
                                    + " will fail with HTTP 401.");
        }
        AuthAdmissionOwner admissionOwner =
                AuthAdmissionOwner.parse(
                        getConfig().getString("access.admission-owner", "PROXY"),
                        AuthAdmissionOwner.PROXY);
        var state = new LocalAccessState(new File(getDataFolder(), "state.yml"));
        var client =
                new BridgeApiClient(
                        getConfig().getString("api.base-url", "http://127.0.0.1:3000"),
                        getConfig().getString("api.key-id", "cc-core"),
                        secret,
                        getConfig().getBoolean("api.allow-insecure-private-http", false),
                        Duration.ofSeconds(getConfig().getLong("api.connect-timeout-seconds", 5)),
                        Duration.ofSeconds(getConfig().getLong("api.request-timeout-seconds", 10)));
        var banPresentation = BanPresentation.from(this::configuredText, "access.banned-message");
        var synchronizer =
                new BridgeSynchronizer(
                        this,
                        client,
                        new AuthMeHashStore(),
                        state,
                        configuredText("messages.username-updated"),
                        configuredText("messages.access-revoked"),
                        new BridgeUuidResolver(Duration.ofSeconds(5), Duration.ofSeconds(10)),
                        admissionOwner,
                        banPresentation);
        String maintenanceKey =
                !getConfig().contains("access.maintenance-message", true)
                                && getConfig().contains("access.uuid-maintenance-message", true)
                        ? "access.uuid-maintenance-message"
                        : "access.maintenance-message";
        getServer()
                .getPluginManager()
                .registerEvents(
                        new AccessListener(
                                state,
                                getConfig()
                                        .getBoolean("access.fail-closed-before-first-sync", true),
                                configuredText(maintenanceKey),
                                configuredText("access.unavailable-message"),
                                configuredText("access.uuid-mismatch-message"),
                                configuredText("access.unbound-message"),
                                banPresentation,
                                admissionOwner),
                        this);
        long period = Math.max(5L, getConfig().getLong("api.poll-seconds", 10)) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, synchronizer, 1L, period);
        getLogger()
                .info(
                        "Auth bridge enabled; AuthMe "
                                + fr.xephi.authme.api.v3.AuthMeApi.getInstance()
                                        .getPluginVersion());
    }

    private String configuredText(String path) {
        if (!getConfig().isString(path)) throw new IllegalArgumentException(path + " must be text");
        return getConfig().getString(path);
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
