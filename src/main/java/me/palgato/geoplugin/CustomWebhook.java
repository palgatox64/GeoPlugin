package me.palgato.geoplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CustomWebhook {
    
    private final String webhookUrl;
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, String> headers;
    
    public CustomWebhook(String webhookUrl, ConfigurationSection headersSection, Plugin plugin, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.plugin = plugin;
        this.logger = logger;
        this.headers = new HashMap<>();
        
        if (headersSection != null) {
            for (String key : headersSection.getKeys(false)) {
                headers.put(key, headersSection.getString(key));
            }
        }
    }
    
    public void sendBlockedConnectionAlert(String playerName, String countryCode, String ipAddress) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }
        
        String avatarUrl = "https://mc-heads.net/avatar/" + playerName;
        
        String json = String.format(
            "{\"event\":\"connection_blocked\",\"timestamp\":\"%s\",\"data\":{\"player\":\"%s\",\"country\":\"%s\",\"ip\":\"%s\",\"avatar_url\":\"%s\"}}",
            Instant.now().toString(), playerName, countryCode, ipAddress, avatarUrl
        );
        
        sendWebhook(json);
    }
    
    public void sendSuspiciousActivity(String countryCode, int attemptCount, int timeWindowMinutes) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }
        
        String json = String.format(
            "{\"event\":\"suspicious_activity\",\"timestamp\":\"%s\",\"data\":{\"country\":\"%s\",\"attempts\":%d,\"time_window_minutes\":%d}}",
            Instant.now().toString(), countryCode, attemptCount, timeWindowMinutes
        );
        
        sendWebhook(json);
    }
    
    private void sendWebhook(String jsonPayload) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(webhookUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("User-Agent", "GeoPlugin-Webhook");
                    
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                    
                    connection.setDoOutput(true);
                    
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        logger.warning("Custom webhook returned status code: " + responseCode);
                    }
                    
                    connection.disconnect();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to send custom webhook", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
