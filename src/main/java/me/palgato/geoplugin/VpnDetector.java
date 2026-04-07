package me.palgato.geoplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VpnDetector {

    private final GeoPlugin plugin;
    private boolean enabled;
    private String apiKey;
    private boolean blockVpn;
    private boolean checkOnLogin;
    private String kickMessage;
    private int cacheDurationMinutes;
    private boolean detectHosting;
    private int minRiskScore;
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
        this.apiKey = config.getString("vpn-detection.api-key", "").trim();
        this.blockVpn = config.getBoolean("vpn-detection.block-vpn", true);
        this.checkOnLogin = config.getBoolean("vpn-detection.check-on-login", true);
        this.kickMessage = config.getString("vpn-detection.kick-message", "&cVPN/Proxy connections are not allowed");

        int configuredCacheMinutes = config.getInt("vpn-detection.cache-duration-minutes", 60);
        if (configuredCacheMinutes < 1) {
            plugin.getLogger().warning(plugin.tr("warn.cache_min"));
            configuredCacheMinutes = 60;
        } else if (configuredCacheMinutes > 1440) {
            plugin.getLogger().warning(plugin.tr("warn.cache_max", configuredCacheMinutes));
            configuredCacheMinutes = 1440;
        }
        this.cacheDurationMinutes = configuredCacheMinutes;

        this.detectHosting = config.getBoolean("vpn-detection.detect-hosting", true);

        int configuredRisk = config.getInt("vpn-detection.min-risk-score", 70);
        if (configuredRisk < 0 || configuredRisk > 100) {
            plugin.getLogger().warning(plugin.tr("warn.risk_invalid"));
            configuredRisk = 70;
        }
        this.minRiskScore = configuredRisk;

        Set<String> validatedWhitelist = new HashSet<>();
        for (String rawIp : config.getStringList("vpn-detection.whitelist-ips")) {
            if (rawIp == null || rawIp.trim().isEmpty()) {
                continue;
            }

            String ip = rawIp.trim();
            try {
                InetAddress.getByName(ip);
                validatedWhitelist.add(ip);
            } catch (UnknownHostException e) {
                plugin.getLogger().warning(plugin.tr("warn.whitelist_ip_invalid", ip));
            }
        }
        this.whitelistedIps = Set.copyOf(validatedWhitelist);

        if (enabled && apiKey.isEmpty()) {
            plugin.getLogger().warning(plugin.tr("warn.vpn_no_api"));
        }
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
                    if (shouldCacheResult(result)) {
                        cache.put(ip, new CachedResult(result, System.currentTimeMillis()));
                    }
                    future.complete(result);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, plugin.tr("warn.failed_check_vpn", ip), e);
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
        String status = extractStringValue(json, "status").orElse("");
        if (status.equalsIgnoreCase("error") || status.equalsIgnoreCase("denied")) {
            String message = extractStringValue(json, "message").orElse("API Error");
            return new VpnCheckResult(false, "error", message, 0);
        }

        if (!json.contains("\"" + ip + "\"")) {
            return new VpnCheckResult(false, "error", "Invalid API payload", 0);
        }

        boolean proxyDetected = extractBooleanValue(json, "proxy").orElse(false);
        boolean vpnDetected = extractBooleanValue(json, "vpn").orElse(false);
        boolean torDetected = extractBooleanValue(json, "tor").orElse(false);
        boolean anonymousDetected = extractBooleanValue(json, "anonymous").orElse(false);
        boolean hostingDetected = extractBooleanValue(json, "hosting").orElse(false);

        int riskScore = extractIntValue(json, "risk").orElse(0);
        boolean riskTriggered = minRiskScore > 0 && riskScore >= minRiskScore;

        boolean detectedByCore = proxyDetected || vpnDetected || torDetected || anonymousDetected;
        boolean detectedByHosting = detectHosting && hostingDetected;

        boolean isVpn = detectedByCore || detectedByHosting || riskTriggered;

        String provider = extractStringValue(json, "provider").orElse("Unknown Provider");
        String networkType = extractStringValue(json, "type").orElse("");

        String type;
        if (vpnDetected) {
            type = "VPN";
        } else if (proxyDetected) {
            type = "Proxy";
        } else if (torDetected) {
            type = "Tor";
        } else if (anonymousDetected) {
            type = "Anonymous";
        } else if (detectedByHosting) {
            type = "Hosting";
        } else if (riskTriggered) {
            type = "High Risk";
        } else if (!networkType.isEmpty()) {
            type = networkType;
        } else {
            type = "clean";
        }

        return new VpnCheckResult(isVpn, type, provider, riskScore);
    }

    private boolean shouldCacheResult(VpnCheckResult result) {
        return result != null && !"error".equalsIgnoreCase(result.type());
    }

    private java.util.Optional<String> extractStringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(matcher.group(1));
    }

    private java.util.Optional<Boolean> extractBooleanValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false|\\\"yes\\\"|\\\"no\\\")", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String value = matcher.group(1).replace("\"", "").toLowerCase();
        return java.util.Optional.of(value.equals("true") || value.equals("yes"));
    }

    private java.util.Optional<Integer> extractIntValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
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
