package me.palgato.geoplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class GeoPlaceholderExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "geoplugin";
    private static final String AUTHOR = "palgato";
    private static final String VERSION = "1.0";
    private static final String COUNTRY_PREFIX = "country_";
    private static final String COUNTRY_SMALLCAPS_PREFIX = "country_smallcaps_";
    private static final String TOTAL_CONNECTIONS = "total_connections";
    private static final String TOTAL_UNIQUE_PLAYERS = "total_unique_players";
    private static final String TOP_COUNTRY_1_CODE = "top_country_1_code";
    private static final String TOP_COUNTRY_1_CONNECTIONS = "top_country_1_connections";
    private static final String TOP_COUNTRY_1_UNIQUE = "top_country_1_unique";
    private static final String ERROR_INVALID = "INVALID";
    private static final String ERROR_OFFLINE = "OFFLINE";
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );
    
    private static final Map<Character, Character> SMALLCAPS_MAP = Map.ofEntries(
        Map.entry('A', 'ᴀ'), Map.entry('B', 'ʙ'), Map.entry('C', 'ᴄ'), Map.entry('D', 'ᴅ'),
        Map.entry('E', 'ᴇ'), Map.entry('F', 'ғ'), Map.entry('G', 'ɢ'), Map.entry('H', 'ʜ'),
        Map.entry('I', 'ɪ'), Map.entry('J', 'ᴊ'), Map.entry('K', 'ᴋ'), Map.entry('L', 'ʟ'),
        Map.entry('M', 'ᴍ'), Map.entry('N', 'ɴ'), Map.entry('O', 'ᴏ'), Map.entry('P', 'ᴘ'),
        Map.entry('Q', 'ǫ'), Map.entry('R', 'ʀ'), Map.entry('S', 's'), Map.entry('T', 'ᴛ'),
        Map.entry('U', 'ᴜ'), Map.entry('V', 'ᴠ'), Map.entry('W', 'ᴡ'), Map.entry('X', 'x'),
        Map.entry('Y', 'ʏ'), Map.entry('Z', 'ᴢ')
    );

    private final GeoPlugin plugin;
    private final GeoManager geoManager;

    public GeoPlaceholderExpansion(GeoPlugin plugin, GeoManager geoManager) {
        this.plugin = plugin;
        this.geoManager = geoManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return AUTHOR;
    }

    @Override
    public @NotNull String getVersion() {
        return VERSION;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        return List.of(
            "%geoplugin_country_<player/ip>%",
            "%geoplugin_country_smallcaps_<player/ip>%",
            "%geoplugin_total_connections%",
            "%geoplugin_total_unique_players%",
            "%geoplugin_top_country_1_code%",
            "%geoplugin_top_country_1_connections%",
            "%geoplugin_top_country_1_unique%"
        );
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        switch (params) {
            case TOTAL_CONNECTIONS:
                return String.valueOf(plugin.getStatistics().getTotalConnections());
            case TOTAL_UNIQUE_PLAYERS:
                return String.valueOf(plugin.getStatistics().getTotalUniquePlayers());
            case TOP_COUNTRY_1_CODE:
                return getTopCountryCode();
            case TOP_COUNTRY_1_CONNECTIONS:
                return String.valueOf(getTopCountryConnections());
            case TOP_COUNTRY_1_UNIQUE:
                return String.valueOf(getTopCountryUniquePlayers());
            default:
                break;
        }

        if (params.startsWith(COUNTRY_SMALLCAPS_PREFIX)) {
            String input = params.substring(COUNTRY_SMALLCAPS_PREFIX.length());
            if (input.isEmpty()) {
                return ERROR_INVALID;
            }

            String countryCode = resolveCountryCode(input);
            return toSmallCaps(countryCode);
        }

        if (params.startsWith(COUNTRY_PREFIX)) {
            String input = params.substring(COUNTRY_PREFIX.length());
            if (input.isEmpty()) {
                return ERROR_INVALID;
            }

            return resolveCountryCode(input);
        }

        return null;
    }

    private String resolveCountryCode(String input) {
        Player target = plugin.getServer().getPlayerExact(input);
        if (target != null) {
            return resolvePlayerCountry(target);
        }

        if (!IP_PATTERN.matcher(input).matches()) {
            return ERROR_INVALID;
        }

        return resolveIpCountry(input);
    }

    private String resolvePlayerCountry(Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        if (socketAddress == null) {
            return ERROR_OFFLINE;
        }
        return geoManager.getCountryCodeOrDefault(socketAddress.getAddress());
    }

    private String resolveIpCountry(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return geoManager.getCountryCodeOrDefault(address);
        } catch (UnknownHostException e) {
            return ERROR_INVALID;
        }
    }

    private String toSmallCaps(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            result.append(SMALLCAPS_MAP.getOrDefault(c, c));
        }
        return result.toString();
    }

    private String getTopCountryCode() {
        List<Map.Entry<String, CountryStatistics.CountryData>> top = plugin.getStatistics().getTopCountriesByConnections(1);
        if (top.isEmpty()) {
            return "NONE";
        }
        return top.getFirst().getKey();
    }

    private int getTopCountryConnections() {
        List<Map.Entry<String, CountryStatistics.CountryData>> top = plugin.getStatistics().getTopCountriesByConnections(1);
        if (top.isEmpty()) {
            return 0;
        }
        return top.getFirst().getValue().getTotalConnections();
    }

    private int getTopCountryUniquePlayers() {
        List<Map.Entry<String, CountryStatistics.CountryData>> top = plugin.getStatistics().getTopCountriesByConnections(1);
        if (top.isEmpty()) {
            return 0;
        }
        return top.getFirst().getValue().getUniquePlayersCount();
    }
}