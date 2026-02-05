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
    private static final String IPCHECK_PREFIX = "ipcheck_";
    private static final String IPCHECK_SMALLCAPS_PREFIX = "ipcheck_smallcaps_";
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
            "%geoplugin_ipcheck_<player/ip>%",
            "%geoplugin_ipcheck_smallcaps_<player/ip>%"
        );
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith(IPCHECK_SMALLCAPS_PREFIX)) {
            String input = params.substring(IPCHECK_SMALLCAPS_PREFIX.length());
            if (input.isEmpty()) {
                return ERROR_INVALID;
            }

            String countryCode = resolveCountryCode(input);
            return toSmallCaps(countryCode);
        }

        if (params.startsWith(IPCHECK_PREFIX)) {
            String input = params.substring(IPCHECK_PREFIX.length());
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
}