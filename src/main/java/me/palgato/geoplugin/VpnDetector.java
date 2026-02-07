package me.palgato.geoplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VpnDetector {

    private final GeoPlugin plugin;
    private boolean enabled;
    private String apiKey;
    private boolean blockVpn;
    private boolean checkOnLogin;
    private String kickMessage;
    private int cacheDurationMinutes;
    private Set<String> whitelistedIps;
    private final Map<String, CachedResult> cache;

    public record VpnCheckResult(boolean isVpn, String type, String provider, int riskScore) {}

    private record CachedResult(VpnCheckResult result, long timestamp) {}

    public VpnDetector(GeoPlugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
        startCacheCleanupTask();
    }

    public void reload(FileConfiguration config) {
        this.enabled = config.getBoolean("vpn-detection.enabled", false);
        this.apiKey = config.getString("vpn-detection.api-key", "");
        this.blockVpn = config.getBoolean("vpn-detection.block-vpn", true);
        this.checkOnLogin = config.getBoolean("vpn-detection.check-on-login", true);
        this.kickMessage = config.getString("vpn-detection.kick-message", "&cVPN/Proxy connections are not allowed");
        this.cacheDurationMinutes = config.getInt("vpn-detection.cache-duration-minutes", 60);
        this.whitelistedIps = Set.copyOf(config.getStringList("vpn-detection.whitelist-ips"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldBlockVpn() {
        return blockVpn;
    }

    public boolean shouldCheckOnLogin() {
        return checkOnLogin;
    }

    public String getKickMessage() {
        return kickMessage.replace("&", "§");
    }

    public CompletableFuture<VpnCheckResult> checkIp(InetAddress address) {
        return checkIp(address.getHostAddress());
    }

    public CompletableFuture<VpnCheckResult> checkIp(String ip) {
        if (!enabled) {
            return CompletableFuture.completedFuture(new VpnCheckResult(false, "disabled", "N/A", 0));
        }

        if (isPrivateIp(ip)) {
            return CompletableFuture.completedFuture(new VpnCheckResult(false, "private", "Private IP", 0));
        }

        if (whitelistedIps.contains(ip)) {
            return CompletableFuture.completedFuture(new VpnCheckResult(false, "whitelisted", "Whitelisted", 0));
        }

        CachedResult cached = cache.get(ip);
        if (cached != null && !isCacheExpired(cached)) {
            return CompletableFuture.completedFuture(cached.result());
        }

        CompletableFuture<VpnCheckResult> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    VpnCheckResult result = queryProxycheckApi(ip);
                    cache.put(ip, new CachedResult(result, System.currentTimeMillis()));
                    future.complete(result);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to check VPN for IP: " + ip, e);
                    future.complete(new VpnCheckResult(false, "error", "API Error", 0));
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    private VpnCheckResult queryProxycheckApi(String ip) throws Exception {
        String endpoint = "https://proxycheck.io/v2/" + ip + "?vpn=1&asn=1";
        if (!apiKey.isEmpty()) {
            endpoint += "&key=" + apiKey;
        }

        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return parseProxycheckResponse(ip, response.toString());
    }

    private VpnCheckResult parseProxycheckResponse(String ip, String json) {
        if (!json.contains("\"" + ip + "\"")) {
            return new VpnCheckResult(false, "clean", "Not VPN", 0);
        }

        boolean isVpn = json.contains("\"proxy\":\"yes\"");
        String type = extractJsonValue(json, "type");
        String provider = extractJsonValue(json, "provider");
        int riskScore = isVpn ? 100 : 0;

        if (type.isEmpty()) {
            type = "Unknown";
        }
        if (provider.isEmpty()) {
            provider = "Unknown Provider";
        }

        return new VpnCheckResult(isVpn, type, provider, riskScore);
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return "";
        }
        return json.substring(startIndex, endIndex);
    }

    private boolean isPrivateIp(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }

        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }

        try {
            int first = Integer.parseInt(octets[0]);
            int second = Integer.parseInt(octets[1]);

            if (first == 10) {
                return true;
            }
            if (first == 192 && second == 168) {
                return true;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        return false;
    }

    private boolean isCacheExpired(CachedResult cached) {
        long ageMinutes = (System.currentTimeMillis() - cached.timestamp()) / 60000;
        return ageMinutes >= cacheDurationMinutes;
    }

    private void startCacheCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cache.entrySet().removeIf(entry -> isCacheExpired(entry.getValue()));
            }
        }.runTaskTimerAsynchronously(plugin, 12000L, 12000L);
    }

    public void clearCache() {
        cache.clear();
    }
}
