package me.palgato.geoplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Level;

public final class GeoPlugin extends JavaPlugin implements Listener {

    private static final String DATABASE_FILENAME = "GeoLite2-Country.mmdb";

    private GeoManager geoManager;

    @Override
    public void onEnable() {
        try {
            this.geoManager = initializeGeoManager();
            getServer().getPluginManager().registerEvents(this, this);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        InetSocketAddress socketAddress = event.getPlayer().getAddress();
        if (socketAddress == null) {
            return;
        }

        InetAddress address = socketAddress.getAddress();
        String countryCode = geoManager.getCountryCodeOrDefault(address);
        
        getLogger().info(event.getPlayer().getName() + " connected from: " + countryCode);
    }

    @Override
    public void onDisable() {
        if (geoManager != null) {
            geoManager.close();
        }
    }
}