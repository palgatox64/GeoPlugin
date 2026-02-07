package me.palgato.geoplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Level;

public final class GeoPlugin extends JavaPlugin implements Listener {

    private static final String DATABASE_FILENAME = "countries.mmdb";

    private GeoManager geoManager;
    private CountryAccessControl accessControl;
    private CountryStatistics statistics;
    private NotificationManager notificationManager;
    private DiscordWebhook discordWebhook;
    private SuspiciousActivityTracker activityTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        try {
            this.geoManager = initializeGeoManager();
            this.accessControl = new CountryAccessControl(getConfig());
            this.statistics = new CountryStatistics(getDataFolder(), getLogger());
            this.notificationManager = new NotificationManager(getDataFolder(), getLogger());
            
            if (getConfig().getBoolean("discord.enabled", false)) {
                String webhookUrl = getConfig().getString("discord.webhook-url", "");
                this.discordWebhook = new DiscordWebhook(webhookUrl, this, getLogger());
                getLogger().info("Discord webhook integration enabled.");
            }
            
            if (getConfig().getBoolean("discord.suspicious-activity.enabled", false)) {
                int threshold = getConfig().getInt("discord.suspicious-activity.threshold", 5);
                int timeWindow = getConfig().getInt("discord.suspicious-activity.time-window-minutes", 10);
                this.activityTracker = new SuspiciousActivityTracker(threshold, timeWindow);
                getLogger().info("Suspicious activity tracking enabled.");
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
        if (!accessControl.isEnabled()) {
            return;
        }
        
        InetAddress address = event.getAddress();
        if (address == null) {
            return;
        }
        
        String countryCode = geoManager.getCountryCodeOrDefault(address);
        
        if (!accessControl.isAllowed(countryCode)) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, accessControl.getKickMessage());
            
            String playerName = event.getPlayer().getName();
            String ipAddress = address.getHostAddress();
            getLogger().info("Blocked connection from " + playerName + " (Country: " + countryCode + ")");
            
            notificationManager.getSubscribedPlayerNames().forEach(name -> {
                org.bukkit.entity.Player admin = getServer().getPlayerExact(name);
                if (admin != null && admin.isOnline()) {
                    admin.sendMessage(org.bukkit.ChatColor.DARK_GRAY + "[" + 
                        org.bukkit.ChatColor.AQUA + "Geo" + 
                        org.bukkit.ChatColor.DARK_GRAY + "] " + 
                        org.bukkit.ChatColor.RED + "Blocked: " + 
                        org.bukkit.ChatColor.WHITE + playerName + 
                        org.bukkit.ChatColor.GRAY + " from " + 
                        org.bukkit.ChatColor.YELLOW + countryCode);
                }
            });
            
            if (discordWebhook != null && getConfig().getBoolean("discord.notify-on-block", true)) {
                discordWebhook.sendBlockedConnectionAlert(playerName, countryCode, ipAddress);
            }
            
            if (activityTracker != null) {
                boolean isSuspicious = activityTracker.recordAttempt(countryCode);
                if (isSuspicious && discordWebhook != null) {
                    int attemptCount = activityTracker.getAttemptCount(countryCode);
                    int timeWindow = getConfig().getInt("discord.suspicious-activity.time-window-minutes", 10);
                    discordWebhook.sendSuspiciousActivity(countryCode, attemptCount, timeWindow);
                }
            }
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
            getLogger().info(event.getPlayer().getName() + " connected from: " + countryCode);
        }
    }
    
    public void reloadAccessControl() {
        accessControl.reload(getConfig());
        
        if (getConfig().getBoolean("discord.enabled", false)) {
            String webhookUrl = getConfig().getString("discord.webhook-url", "");
            this.discordWebhook = new DiscordWebhook(webhookUrl, this, getLogger());
        } else {
            this.discordWebhook = null;
        }
        
        if (getConfig().getBoolean("discord.suspicious-activity.enabled", false)) {
            int threshold = getConfig().getInt("discord.suspicious-activity.threshold", 5);
            int timeWindow = getConfig().getInt("discord.suspicious-activity.time-window-minutes", 10);
            this.activityTracker = new SuspiciousActivityTracker(threshold, timeWindow);
        } else {
            this.activityTracker = null;
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

    @Override
    public void onDisable() {
        if (geoManager != null) {
            geoManager.close();
        }
    }
}