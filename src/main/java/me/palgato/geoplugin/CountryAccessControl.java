package me.palgato.geoplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CountryAccessControl {

    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Z]{2}$");
    
    public enum Mode {
        WHITELIST,
        BLACKLIST
    }
    
    private boolean enabled;
    private Mode mode;
    private Set<String> countries;
    private String kickMessage;
    private final Logger logger;
    
    public CountryAccessControl(FileConfiguration config, Logger logger) {
        this.logger = logger;
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
        
        String modeRaw = section.getString("mode", "blacklist");
        String modeStr = modeRaw == null ? "blacklist" : modeRaw.trim().toLowerCase();
        if (modeStr.equals("whitelist")) {
            this.mode = Mode.WHITELIST;
        } else if (modeStr.equals("blacklist")) {
            this.mode = Mode.BLACKLIST;
        } else {
            this.mode = Mode.BLACKLIST;
            logger.warning("Invalid country-access-control.mode value '" + modeRaw + "'. Using 'blacklist'.");
        }
        
        List<String> countryList = section.getStringList("countries");
        this.countries = countryList.stream()
            .map(value -> value == null ? "" : value.trim().toUpperCase())
            .filter(value -> {
                boolean valid = COUNTRY_CODE_PATTERN.matcher(value).matches();
                if (!valid && !value.isEmpty()) {
                    logger.warning("Ignoring invalid country code in config: '" + value + "'. Expected ISO 3166-1 alpha-2 (e.g. US, BR).");
                }
                return valid;
            })
            .collect(Collectors.toSet());

        if (enabled && countries.isEmpty()) {
            logger.warning("country-access-control is enabled but no valid country codes were found.");
        }
        
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
