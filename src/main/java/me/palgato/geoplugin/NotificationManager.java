package me.palgato.geoplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NotificationManager {
    
    private final File dataFile;
    private final Logger logger;
    private final Set<String> subscribedPlayers;
    
    public NotificationManager(File dataFolder, Logger logger) {
        this.dataFile = new File(dataFolder, "notifications.yml");
        this.logger = logger;
        this.subscribedPlayers = new HashSet<>();
        load();
    }
    
    public boolean toggle(Player player) {
        String name = player.getName();
        boolean newState;
        
        if (subscribedPlayers.contains(name)) {
            subscribedPlayers.remove(name);
            newState = false;
        } else {
            subscribedPlayers.add(name);
            newState = true;
        }
        
        save();
        return newState;
    }
    
    public boolean isSubscribed(Player player) {
        return subscribedPlayers.contains(player.getName());
    }
    
    public Set<String> getSubscribedPlayerNames() {
        return new HashSet<>(subscribedPlayers);
    }
    
    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            subscribedPlayers.addAll(config.getStringList("subscribed-players"));
            logger.info("Loaded notification preferences for " + subscribedPlayers.size() + " players.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load notification preferences", e);
        }
    }
    
    private void save() {
        try {
            dataFile.getParentFile().mkdirs();
            
            FileConfiguration config = new YamlConfiguration();
            config.set("subscribed-players", subscribedPlayers.stream().toList());
            
            config.save(dataFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save notification preferences", e);
        }
    }
}
