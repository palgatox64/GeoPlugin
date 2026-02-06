package me.palgato.geoplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CountryAccessControl {
    
    public enum Mode {
        WHITELIST,
        BLACKLIST
    }
    
    private boolean enabled;
    private Mode mode;
    private Set<String> countries;
    private String kickMessage;
    
    public CountryAccessControl(FileConfiguration config) {
        reload(config);
    }
    
    public void reload(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("country-access-control");
        if (section == null) {
            this.enabled = false;
            this.mode = Mode.BLACKLIST;
            this.countries = new HashSet<>();
            this.kickMessage = "§cYou cannot join from your country.";
            return;
        }
        
        this.enabled = section.getBoolean("enabled", false);
        
        String modeStr = section.getString("mode", "blacklist").toLowerCase();
        this.mode = modeStr.equals("whitelist") ? Mode.WHITELIST : Mode.BLACKLIST;
        
        List<String> countryList = section.getStringList("countries");
        this.countries = countryList.stream()
            .map(String::toUpperCase)
            .collect(Collectors.toSet());
        
        this.kickMessage = section.getString("kick-message", "§cYou cannot join from your country.")
            .replace('&', '§');
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isAllowed(String countryCode) {
        if (!enabled || countryCode == null) {
            return true;
        }
        
        String upperCode = countryCode.toUpperCase();
        boolean inList = countries.contains(upperCode);
        
        return mode == Mode.WHITELIST ? inList : !inList;
    }
    
    public String getKickMessage() {
        return kickMessage;
    }
    
    public Mode getMode() {
        return mode;
    }
    
    public Set<String> getCountries() {
        return new HashSet<>(countries);
    }
}
