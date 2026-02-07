package me.palgato.geoplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class CountryStatistics {
    
    private final File dataFile;
    private final Logger logger;
    private final Map<String, CountryData> statistics;
    
    public CountryStatistics(File dataFolder, Logger logger) {
        this.dataFile = new File(dataFolder, "statistics.yml");
        this.logger = logger;
        this.statistics = new LinkedHashMap<>();
        load();
    }
    
    public void recordConnection(String countryCode, String playerName) {
        if (countryCode == null || playerName == null) {
            return;
        }
        
        CountryData data = statistics.computeIfAbsent(countryCode, k -> new CountryData());
        data.totalConnections++;
        data.uniquePlayers.add(playerName.toLowerCase());
        
        save();
    }
    
    public List<Map.Entry<String, CountryData>> getTopCountriesByConnections(int limit) {
        return statistics.entrySet().stream()
            .sorted(Map.Entry.<String, CountryData>comparingByValue(
                Comparator.comparingInt(d -> -d.totalConnections)
            ))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<Map.Entry<String, CountryData>> getTopCountriesByUniquePlayers(int limit) {
        return statistics.entrySet().stream()
            .sorted(Map.Entry.<String, CountryData>comparingByValue(
                Comparator.comparingInt(d -> -d.uniquePlayers.size())
            ))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public int getTotalConnections() {
        return statistics.values().stream()
            .mapToInt(d -> d.totalConnections)
            .sum();
    }
    
    public int getTotalUniquePlayers() {
        Set<String> allPlayers = new HashSet<>();
        statistics.values().forEach(data -> allPlayers.addAll(data.uniquePlayers));
        return allPlayers.size();
    }
    
    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection countriesSection = config.getConfigurationSection("countries");
            
            if (countriesSection == null) {
                return;
            }
            
            for (String countryCode : countriesSection.getKeys(false)) {
                ConfigurationSection countrySection = countriesSection.getConfigurationSection(countryCode);
                if (countrySection != null) {
                    CountryData data = new CountryData();
                    data.totalConnections = countrySection.getInt("total-connections", 0);
                    data.uniquePlayers = new HashSet<>(countrySection.getStringList("unique-players"));
                    statistics.put(countryCode, data);
                }
            }
            
            logger.info("Loaded statistics for " + statistics.size() + " countries.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load statistics", e);
        }
    }
    
    private void save() {
        try {
            dataFile.getParentFile().mkdirs();
            
            FileConfiguration config = new YamlConfiguration();
            
            for (Map.Entry<String, CountryData> entry : statistics.entrySet()) {
                String path = "countries." + entry.getKey();
                CountryData data = entry.getValue();
                
                config.set(path + ".total-connections", data.totalConnections);
                config.set(path + ".unique-players", new ArrayList<>(data.uniquePlayers));
            }
            
            config.save(dataFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save statistics", e);
        }
    }
    
    public static final class CountryData {
        private int totalConnections;
        private Set<String> uniquePlayers;
        
        public CountryData() {
            this.totalConnections = 0;
            this.uniquePlayers = new HashSet<>();
        }
        
        public int getTotalConnections() {
            return totalConnections;
        }
        
        public int getUniquePlayersCount() {
            return uniquePlayers.size();
        }
    }
}
