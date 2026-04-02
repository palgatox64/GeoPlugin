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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        try {
            this.geoManager = initializeGeoManager();
            this.accessControl = new CountryAccessControl(getConfig(), getLogger());
            this.statistics = new CountryStatistics(getDataFolder(), getLogger());
            this.notificationManager = new NotificationManager(getDataFolder(), getLogger());
            
            if (getConfig().getBoolean("discord.enabled", false)) {
                String webhookUrl = validateWebhookUrl(getConfig().getString("discord.webhook-url", ""), "discord.webhook-url");
                if (webhookUrl != null) {
                    this.discordWebhook = new DiscordWebhook(webhookUrl, this, getLogger());
                    getLogger().info("Discord webhook integration enabled.");
                }
            }
            
            if (getConfig().getBoolean("custom-webhook.enabled", false)) {
                String webhookUrl = validateWebhookUrl(getConfig().getString("custom-webhook.webhook-url", ""), "custom-webhook.webhook-url");
                if (webhookUrl != null) {
                    org.bukkit.configuration.ConfigurationSection headersSection = 
                        getConfig().getConfigurationSection("custom-webhook.headers");
                    this.customWebhook = new CustomWebhook(webhookUrl, headersSection, this, getLogger());
                    getLogger().info("Custom webhook integration enabled.");
                }
            }
            
            if (getConfig().getBoolean("suspicious-activity.enabled", false)) {
                this.activityTracker = createSuspiciousActivityTracker();
                getLogger().info("Suspicious activity tracking enabled.");
            }
            
            this.vpnDetector = new VpnDetector(this);
            vpnDetector.reload(getConfig());
            if (vpnDetector.isEnabled()) {
                getLogger().info("VPN detection enabled.");
            }
            
            getServer().getPluginManager().registerEvents(this, this);
            
            GeoCommands commands = new GeoCommands(geoManager, this);
            getCommand("geoplugin").setExecutor(commands);
            getCommand("geoplugin").setTabCompleter(commands);

            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new GeoPlaceholderExpansion(this, geoManager).register();
                getLogger().info("PlaceholderAPI integration enabled.");
            }
            
            if (accessControl.isEnabled()) {
                getLogger().info("Country access control enabled in " + 
                    accessControl.getMode() + " mode with " + 
                    accessControl.getCountries().size() + " countries.");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize GeoIP database", e);
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
        String playerName = player.getName();
        String ipAddress = address.getHostAddress();
        
        if (accessControl.isEnabled() && !hasBypassPermission(player, PERM_BYPASS_COUNTRY)) {
            String countryCode = geoManager.getCountryCodeOrDefault(address);
            
            if (!accessControl.isAllowed(countryCode)) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, accessControl.getKickMessage());
                
                getLogger().info("Blocked connection from " + playerName + " (Country: " + countryCode + ")");
                
                notificationManager.getSubscribedPlayerNames().forEach(name -> {
                    org.bukkit.entity.Player admin = getServer().getPlayerExact(name);
                    if (admin != null && admin.isOnline()) {
                        admin.sendMessage(MSG_PREFIX + 
                            org.bukkit.ChatColor.RED + "Blocked: " + 
                            org.bukkit.ChatColor.WHITE + playerName + 
                            org.bukkit.ChatColor.GRAY + " from " + 
                            org.bukkit.ChatColor.YELLOW + countryCode);
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
        
        if (vpnDetector.isEnabled() && vpnDetector.shouldCheckOnLogin() && !hasBypassPermission(player, PERM_BYPASS_VPN)) {
            vpnDetector.checkIp(address).thenAccept(result -> {
                if (result.isVpn() && vpnDetector.shouldBlockVpn()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Player targetPlayer = getServer().getPlayerExact(playerName);
                            if (targetPlayer != null && targetPlayer.isOnline()) {
                                targetPlayer.kickPlayer(vpnDetector.getKickMessage());
                                
                                getLogger().info("Blocked VPN connection from " + playerName + 
                                    " (" + result.type() + " - " + result.provider() + ")");
                                
                                notificationManager.getSubscribedPlayerNames().forEach(name -> {
                                    Player admin = getServer().getPlayerExact(name);
                                    if (admin != null && admin.isOnline()) {
                                        admin.sendMessage(MSG_PREFIX + 
                                            org.bukkit.ChatColor.RED + "Blocked VPN: " + 
                                            org.bukkit.ChatColor.WHITE + playerName + 
                                            org.bukkit.ChatColor.GRAY + " (" + result.type() + ")");
                                    }
                                });
                                
                                if (discordWebhook != null && getConfig().getBoolean("discord.notify-on-vpn", false)) {
                                    discordWebhook.sendVpnBlockedAlert(playerName, ipAddress, result.type(), result.provider());
                                }
                                
                                if (customWebhook != null && getConfig().getBoolean("custom-webhook.notify-on-vpn", false)) {
                                    customWebhook.sendVpnBlockedAlert(playerName, ipAddress, result.type(), result.provider());
                                }
                            }
                        }
                    }.runTask(this);
                }
            });
        }
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
            } else if (hasBypassPermission(p, PERM_BYPASS_COUNTRY) || hasBypassPermission(p, PERM_BYPASS_VPN)) {
                bypassInfo = " [PARTIAL BYPASS]";
            }
            getLogger().info(p.getName() + " connected from: " + countryCode + bypassInfo);
        }
    }
    
    private boolean hasBypassPermission(Player player, String specificPermission) {
        return player.hasPermission(PERM_BYPASS_ALL) || player.hasPermission(specificPermission);
    }
    
    public void reloadAccessControl() {
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
        if (vpnDetector.isEnabled()) {
            getLogger().info("VPN detection reloaded.");
        }
        
        if (accessControl.isEnabled()) {
            getLogger().info("Country access control reloaded: " + 
                accessControl.getMode() + " mode with " + 
                accessControl.getCountries().size() + " countries.");
        } else {
            getLogger().info("Country access control is disabled.");
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

    private SuspiciousActivityTracker createSuspiciousActivityTracker() {
        int threshold = getConfig().getInt("suspicious-activity.threshold", 5);
        if (threshold < 1) {
            getLogger().warning("suspicious-activity.threshold must be >= 1. Using 5.");
            threshold = 5;
        }

        int timeWindow = getConfig().getInt("suspicious-activity.time-window-minutes", 10);
        if (timeWindow < 1) {
            getLogger().warning("suspicious-activity.time-window-minutes must be >= 1. Using 10.");
            timeWindow = 10;
        }

        return new SuspiciousActivityTracker(threshold, timeWindow);
    }

    private String validateWebhookUrl(String rawUrl, String configPath) {
        if (rawUrl == null) {
            getLogger().warning(configPath + " is null. Webhook integration disabled.");
            return null;
        }

        String url = rawUrl.trim();
        if (url.isEmpty()) {
            getLogger().warning(configPath + " is empty. Webhook integration disabled.");
            return null;
        }

        try {
            URL parsed = new URL(url);
            String protocol = parsed.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                getLogger().warning(configPath + " must use http or https. Webhook integration disabled.");
                return null;
            }
            return url;
        } catch (MalformedURLException e) {
            getLogger().warning("Invalid URL in " + configPath + ": '" + url + "'. Webhook integration disabled.");
            return null;
        }
    }

    @Override
    public void onDisable() {
        if (geoManager != null) {
            geoManager.close();
        }
    }
}