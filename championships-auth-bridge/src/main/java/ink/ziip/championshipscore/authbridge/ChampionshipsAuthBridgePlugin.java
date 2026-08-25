package ink.ziip.championshipscore.authbridge;

import ink.ziip.championshipscore.authbridge.authme.AuthMeHashStore;
import ink.ziip.championshipscore.authbridge.bridge.BridgeApiClient;
import ink.ziip.championshipscore.authbridge.bridge.BridgeSynchronizer;
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
        if (secret.equals("change-me") || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            secret = generateSecret();
            getConfig().set("api.hmac-secret", secret);
            saveConfig();
            getLogger().warning("api.hmac-secret was not configured; generated a new secret and saved it to config.yml.");
            getLogger().warning("Configure the same value as BRIDGE_HMAC_SECRET on the cc-web deployment (docker compose env file), otherwise bridge sync will fail with HTTP 401.");
        }
        var state = new LocalAccessState(new File(getDataFolder(), "state.yml"));
        var client = new BridgeApiClient(
            getConfig().getString("api.base-url", "http://127.0.0.1:3000"),
            getConfig().getString("api.key-id", "cc-core"),
            secret,
            getConfig().getBoolean("api.allow-insecure-private-http", false),
            Duration.ofSeconds(getConfig().getLong("api.connect-timeout-seconds", 5)),
            Duration.ofSeconds(getConfig().getLong("api.request-timeout-seconds", 10))
        );
        var synchronizer = new BridgeSynchronizer(this, client, new AuthMeHashStore(), state,
                getConfig().getString("messages.username-updated",
                        "&#ff6b26你的 Minecraft 玩家名已修改，&#ededed请使用新名称重新登录。"),
                getConfig().getString("messages.access-revoked", "&#ff6b26你的服务器访问资格已被撤销。"));
        getServer().getPluginManager().registerEvents(new AccessListener(
            state,
            getConfig().getBoolean("access.fail-closed-before-first-sync", true),
            getConfig().getString("access.maintenance-message",
                    getConfig().getString("access.uuid-maintenance-message", "&#ff6b26服务器正在维护账号资料，&#ededed请稍后重新连接。")),
            getConfig().getString("access.unavailable-message", "&#ff6b26账号服务暂时不可用，&#ededed请稍后重新连接。"),
            getConfig().getString("access.uuid-mismatch-message", "&#ff6b26玩家身份校验失败，&#ededed请确认通过指定代理连接。")
        ), this);
        long period = Math.max(5L, getConfig().getLong("api.poll-seconds", 10)) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, synchronizer, 1L, period);
        getLogger().info("Auth bridge enabled; AuthMe " + fr.xephi.authme.api.v3.AuthMeApi.getInstance().getPluginVersion());
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
