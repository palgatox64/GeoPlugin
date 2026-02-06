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

    private static final String DATABASE_FILENAME = "Countries.mmdb";

    private GeoManager geoManager;
    private CountryAccessControl accessControl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        try {
            this.geoManager = initializeGeoManager();
            this.accessControl = new CountryAccessControl(getConfig());
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
            getLogger().info("Blocked connection from " + event.getPlayer().getName() + 
                " (Country: " + countryCode + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("log-player-connections", true)) {
            return;
        }

        InetSocketAddress socketAddress = event.getPlayer().getAddress();
        if (socketAddress == null) {
            return;
        }

        InetAddress address = socketAddress.getAddress();
        String countryCode = geoManager.getCountryCodeOrDefault(address);
        
        getLogger().info(event.getPlayer().getName() + " connected from: " + countryCode);
    }
    
    public void reloadAccessControl() {
        accessControl.reload(getConfig());
        
        if (accessControl.isEnabled()) {
            getLogger().info("Country access control reloaded: " + 
                accessControl.getMode() + " mode with " + 
                accessControl.getCountries().size() + " countries.");
        } else {
            getLogger().info("Country access control is disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (geoManager != null) {
            geoManager.close();
        }
    }
}