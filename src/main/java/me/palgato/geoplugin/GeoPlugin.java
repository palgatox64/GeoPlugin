package me.palgato.geoplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public final class GeoPlugin extends JavaPlugin implements Listener {

    private static final String DATABASE_FILENAME = "countries.mmdb";
    private static final String MSG_PREFIX = org.bukkit.ChatColor.DARK_GRAY + "[" + 
        org.bukkit.ChatColor.AQUA + "Geo" + org.bukkit.ChatColor.DARK_GRAY + "] ";
    
    private static final String PERM_BYPASS_ALL = "geoplugin.bypass";
    private static final String PERM_BYPASS_COUNTRY = "geoplugin.bypass.country";
    private static final String PERM_BYPASS_VPN = "geoplugin.bypass.vpn";

    private GeoManager geoManager;
    private CountryAccessControl accessControl;
    private CountryStatistics statistics;
    private NotificationManager notificationManager;
    private DiscordWebhook discordWebhook;
    private CustomWebhook customWebhook;
    private SuspiciousActivityTracker activityTracker;
    private VpnDetector vpnDetector;
    private TranslationManager i18n;
    private Set<UUID> vpnWhitelistedPlayerUuids = Set.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.i18n = TranslationManager.fromConfig(getConfig(), getLogger());
        
        try {
            this.geoManager = initializeGeoManager();
            this.accessControl = new CountryAccessControl(getConfig(), getLogger(), i18n);
            this.statistics = new CountryStatistics(getDataFolder(), getLogger());
            this.notificationManager = new NotificationManager(getDataFolder(), getLogger());
            
            if (getConfig().getBoolean("discord.enabled", false)) {
                String webhookUrl = validateWebhookUrl(getConfig().getString("discord.webhook-url", ""), "discord.webhook-url");
                if (webhookUrl != null) {
                    this.discordWebhook = new DiscordWebhook(webhookUrl, this, getLogger());
                    getLogger().info(tr("log.discord_enabled"));
                }
            }
            
            if (getConfig().getBoolean("custom-webhook.enabled", false)) {
                String webhookUrl = validateWebhookUrl(getConfig().getString("custom-webhook.webhook-url", ""), "custom-webhook.webhook-url");
                if (webhookUrl != null) {
                    org.bukkit.configuration.ConfigurationSection headersSection = 
                        getConfig().getConfigurationSection("custom-webhook.headers");
                    this.customWebhook = new CustomWebhook(webhookUrl, headersSection, this, getLogger());
                    getLogger().info(tr("log.custom_webhook_enabled"));
                }
            }
            
            if (getConfig().getBoolean("suspicious-activity.enabled", false)) {
                this.activityTracker = createSuspiciousActivityTracker();
                getLogger().info(tr("log.suspicious_tracking_enabled"));
            }
            
            this.vpnDetector = new VpnDetector(this);
            vpnDetector.reload(getConfig());
            reloadVpnUuidWhitelist();
            if (vpnDetector.isEnabled()) {
                getLogger().info(tr("log.vpn_enabled"));
            }
            
            getServer().getPluginManager().registerEvents(this, this);
            
            GeoCommands commands = new GeoCommands(geoManager, this);
            getCommand("geoplugin").setExecutor(commands);
            getCommand("geoplugin").setTabCompleter(commands);

            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new GeoPlaceholderExpansion(this, geoManager).register();
                getLogger().info(tr("log.placeholder_enabled"));
            }
            
            if (accessControl.isEnabled()) {
                getLogger().info(tr("log.country_access_enabled", accessControl.getMode(), accessControl.getCountries().size()));
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, tr("log.failed_geoip_init"), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private GeoManager initializeGeoManager() throws IOException {
        File dbFile = new File(getDataFolder(), DATABASE_FILENAME);
        
        if (!dbFile.exists()) {
            getDataFolder().mkdirs();
            saveResource(DATABASE_FILENAME, false);
        }

        return new GeoManager(dbFile);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        InetAddress address = event.getAddress();
        if (address == null) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String ipAddress = address.getHostAddress();
        
        if (accessControl.isEnabled() && !hasBypassPermission(player, PERM_BYPASS_COUNTRY)) {
            String countryCode = geoManager.getCountryCodeOrDefault(address);
            
            if (!accessControl.isAllowed(countryCode)) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, accessControl.getKickMessage());
                
                getLogger().info(tr("log.blocked_connection_country", playerName, countryCode));
                
                notificationManager.getSubscribedPlayerNames().forEach(name -> {
                    org.bukkit.entity.Player admin = getServer().getPlayerExact(name);
                    if (admin != null && admin.isOnline()) {
                        admin.sendMessage(MSG_PREFIX + 
                            org.bukkit.ChatColor.RED + tr("admin.blocked", playerName, countryCode));
                    }
                });
                
                if (discordWebhook != null && getConfig().getBoolean("discord.notify-on-block", true)) {
                    discordWebhook.sendBlockedConnectionAlert(playerName, countryCode, ipAddress);
                }
                
                if (customWebhook != null && getConfig().getBoolean("custom-webhook.notify-on-block", true)) {
                    customWebhook.sendBlockedConnectionAlert(playerName, countryCode, ipAddress);
                }
                
                if (activityTracker != null) {
                    boolean isSuspicious = activityTracker.recordAttempt(countryCode);
                    if (isSuspicious) {
                        int attemptCount = activityTracker.getAttemptCount(countryCode);
                        int timeWindow = getConfig().getInt("suspicious-activity.time-window-minutes", 10);
                        
                        if (discordWebhook != null && getConfig().getBoolean("discord.notify-on-suspicious-activity", true)) {
                            discordWebhook.sendSuspiciousActivity(countryCode, attemptCount, timeWindow);
                        }
                        
                        if (customWebhook != null && getConfig().getBoolean("custom-webhook.notify-on-suspicious-activity", true)) {
                            customWebhook.sendSuspiciousActivity(countryCode, attemptCount, timeWindow);
                        }
                    }
                }
                
                return;
            }
        }
        
        if (vpnDetector.isEnabled() && vpnDetector.shouldCheckOnLogin() && !hasVpnBypass(player)) {
            vpnDetector.checkIp(address).thenAccept(result -> {
                if (result.isVpn() && vpnDetector.shouldBlockVpn()) {
                    kickVpnPlayerWhenOnline(playerId, playerName, ipAddress, result);
                }
            });
        }
    }

    private void kickVpnPlayerWhenOnline(UUID playerId, String playerName, String ipAddress, VpnDetector.VpnCheckResult result) {
        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                attempts++;

                Player targetPlayer = getServer().getPlayer(playerId);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetPlayer.kickPlayer(vpnDetector.getKickMessage());

                    getLogger().info(tr("log.blocked_vpn_connection", playerName, mapVpnType(result.type()), result.provider()));

                    notificationManager.getSubscribedPlayerNames().forEach(name -> {
                        Player admin = getServer().getPlayerExact(name);
                        if (admin != null && admin.isOnline()) {
                            admin.sendMessage(MSG_PREFIX +
                                org.bukkit.ChatColor.RED + tr("admin.blocked_vpn", playerName, mapVpnType(result.type())));
                        }
                    });

                    if (discordWebhook != null && getConfig().getBoolean("discord.notify-on-vpn", false)) {
                        discordWebhook.sendVpnBlockedAlert(playerName, ipAddress, result.type(), result.provider());
                    }

                    if (customWebhook != null && getConfig().getBoolean("custom-webhook.notify-on-vpn", false)) {
                        customWebhook.sendVpnBlockedAlert(playerName, ipAddress, result.type(), result.provider());
                    }

                    cancel();
                    return;
                }

                if (attempts >= 80) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        InetSocketAddress socketAddress = event.getPlayer().getAddress();
        if (socketAddress == null) {
            return;
        }

        InetAddress address = socketAddress.getAddress();
        String countryCode = geoManager.getCountryCodeOrDefault(address);
        
        statistics.recordConnection(countryCode, event.getPlayer().getName());
        
        if (getConfig().getBoolean("log-player-connections", true)) {
            String bypassInfo = "";
            Player p = event.getPlayer();
            if (hasBypassPermission(p, PERM_BYPASS_ALL)) {
                bypassInfo = " [BYPASS]";
            } else if (hasBypassPermission(p, PERM_BYPASS_COUNTRY) || hasVpnBypass(p)) {
                bypassInfo = " [PARTIAL BYPASS]";
            }
            getLogger().info(tr("log.player_connected_from", p.getName(), countryCode, bypassInfo));
        }
    }

    private boolean hasVpnBypass(Player player) {
        if (hasBypassPermission(player, PERM_BYPASS_VPN)) {
            return true;
        }
        return vpnWhitelistedPlayerUuids.contains(player.getUniqueId());
    }

    private boolean hasBypassPermission(Player player, String specificPermission) {
        return player.hasPermission(PERM_BYPASS_ALL) || player.hasPermission(specificPermission);
    }

    private void reloadVpnUuidWhitelist() {
        Set<UUID> validated = new HashSet<>();

        for (String rawUuid : getConfig().getStringList("vpn-detection.whitelist-player-uuids")) {
            if (rawUuid == null || rawUuid.trim().isEmpty()) {
                continue;
            }

            String candidate = rawUuid.trim();
            try {
                validated.add(UUID.fromString(candidate));
            } catch (IllegalArgumentException e) {
                getLogger().warning(tr("warn.whitelist_uuid_invalid", candidate));
            }
        }

        this.vpnWhitelistedPlayerUuids = Set.copyOf(validated);
    }
    
    public void reloadAccessControl() {
        this.i18n = TranslationManager.fromConfig(getConfig(), getLogger());
        accessControl.reload(getConfig());
        
        if (getConfig().getBoolean("discord.enabled", false)) {
            String webhookUrl = validateWebhookUrl(getConfig().getString("discord.webhook-url", ""), "discord.webhook-url");
            this.discordWebhook = webhookUrl == null ? null : new DiscordWebhook(webhookUrl, this, getLogger());
        } else {
            this.discordWebhook = null;
        }
        
        if (getConfig().getBoolean("custom-webhook.enabled", false)) {
            String webhookUrl = validateWebhookUrl(getConfig().getString("custom-webhook.webhook-url", ""), "custom-webhook.webhook-url");
            if (webhookUrl != null) {
                org.bukkit.configuration.ConfigurationSection headersSection = 
                    getConfig().getConfigurationSection("custom-webhook.headers");
                this.customWebhook = new CustomWebhook(webhookUrl, headersSection, this, getLogger());
            } else {
                this.customWebhook = null;
            }
        } else {
            this.customWebhook = null;
        }
        
        if (getConfig().getBoolean("suspicious-activity.enabled", false)) {
            this.activityTracker = createSuspiciousActivityTracker();
        } else {
            this.activityTracker = null;
        }
        
        vpnDetector.reload(getConfig());
        reloadVpnUuidWhitelist();
        if (vpnDetector.isEnabled()) {
            getLogger().info(tr("log.vpn_reloaded"));
        }
        
        if (accessControl.isEnabled()) {
            getLogger().info(tr("log.country_access_reloaded", accessControl.getMode(), accessControl.getCountries().size()));
        } else {
            getLogger().info(tr("log.country_access_disabled"));
        }
    }
    
    public CountryStatistics getStatistics() {
        return statistics;
    }
    
    public NotificationManager getNotificationManager() {
        return notificationManager;
    }
    
    public CountryAccessControl getAccessControl() {
        return accessControl;
    }
    
    public VpnDetector getVpnDetector() {
        return vpnDetector;
    }

    public String tr(String key, Object... args) {
        return i18n.tr(key, args);
    }

    public TranslationManager getI18n() {
        return i18n;
    }

    private SuspiciousActivityTracker createSuspiciousActivityTracker() {
        int threshold = getConfig().getInt("suspicious-activity.threshold", 5);
        if (threshold < 1) {
            getLogger().warning(tr("warn.threshold_invalid"));
            threshold = 5;
        }

        int timeWindow = getConfig().getInt("suspicious-activity.time-window-minutes", 10);
        if (timeWindow < 1) {
            getLogger().warning(tr("warn.timewindow_invalid"));
            timeWindow = 10;
        }

        return new SuspiciousActivityTracker(threshold, timeWindow);
    }

    private String validateWebhookUrl(String rawUrl, String configPath) {
        if (rawUrl == null) {
            getLogger().warning(tr("warn.webhook_null", configPath));
            return null;
        }

        String url = rawUrl.trim();
        if (url.isEmpty()) {
            getLogger().warning(tr("warn.webhook_empty", configPath));
            return null;
        }

        try {
            URL parsed = new URL(url);
            String protocol = parsed.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                getLogger().warning(tr("warn.webhook_protocol", configPath));
                return null;
            }
            return url;
        } catch (MalformedURLException e) {
            getLogger().warning(tr("warn.webhook_invalid", configPath, url));
            return null;
        }
    }

    private String mapVpnType(String rawType) {
        if (rawType == null) {
            return tr("vpn.type.error");
        }

        return switch (rawType.toLowerCase()) {
            case "disabled" -> tr("vpn.type.disabled");
            case "private" -> tr("vpn.type.private");
            case "whitelisted" -> tr("vpn.type.whitelisted");
            case "clean" -> tr("vpn.type.clean");
            case "error" -> tr("vpn.type.error");
            case "vpn" -> tr("vpn.type.vpn");
            case "proxy" -> tr("vpn.type.proxy");
            case "tor" -> tr("vpn.type.tor");
            case "anonymous" -> tr("vpn.type.anonymous");
            case "hosting" -> tr("vpn.type.hosting");
            case "high risk" -> tr("vpn.type.high_risk");
            default -> rawType;
        };
    }

    @Override
    public void onDisable() {
        if (geoManager != null) {
            geoManager.close();
        }
    }
}