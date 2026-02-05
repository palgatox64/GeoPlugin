package me.palgato.geoplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class GeoPlaceholderExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "geoplugin";
    private static final String AUTHOR = "palgato";
    private static final String VERSION = "1.0";
    private static final String IPCHECK_PREFIX = "ipcheck_";
    private static final String ERROR_INVALID = "INVALID";
    private static final String ERROR_OFFLINE = "OFFLINE";
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
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
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!params.startsWith(IPCHECK_PREFIX)) {
            return null;
        }

        String input = params.substring(IPCHECK_PREFIX.length());
        if (input.isEmpty()) {
            return ERROR_INVALID;
        }

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
}