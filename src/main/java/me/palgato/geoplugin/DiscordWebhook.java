package me.palgato.geoplugin;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DiscordWebhook {
    
    private final String webhookUrl;
    private final Plugin plugin;
    private final Logger logger;
    
    public DiscordWebhook(String webhookUrl, Plugin plugin, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.plugin = plugin;
        this.logger = logger;
    }
    
    public void sendBlockedConnectionAlert(String playerName, String countryCode, String ipAddress) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("https://discord.com/api/webhooks/YOUR_WEBHOOK_URL")) {
            return;
        }
        
        String content = String.format(
            "**Connection Blocked**\\n" +
            "Player: `%s`\\n" +
            "Country: `%s`\\n" +
            "IP: `%s`",
            playerName, countryCode, ipAddress
        );
        
        String thumbnailUrl = "https://mc-heads.net/avatar/" + playerName;
        
        sendMessageWithThumbnail(content, 15158332, thumbnailUrl); // Red color
    }
    
    public void sendSuspiciousActivity(String playerName, String ipAddress, String countryCode, int attemptCount, int timeWindowMinutes, String source) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("https://discord.com/api/webhooks/YOUR_WEBHOOK_URL")) {
            return;
        }

        String sourceLabel = "vpn_proxy_block".equalsIgnoreCase(source)
            ? "VPN/Proxy Block"
            : "Country Access Block";
        
        String content = String.format(
            "**Suspicious Activity Detected**\\n" +
            "Player: `%s`\\n" +
            "IP: `%s`\\n" +
            "Country: `%s`\\n" +
            "Source: `%s`\\n" +
            "Blocked attempts: `%d` in last %d minutes",
            playerName, ipAddress, countryCode, sourceLabel, attemptCount, timeWindowMinutes
        );

        String thumbnailUrl = "https://mc-heads.net/avatar/" + playerName;
        
        sendMessageWithThumbnail(content, 16776960, thumbnailUrl); // Yellow color
    }
    
    public void sendVpnBlockedAlert(String playerName, String ipAddress, String vpnType, String provider) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("https://discord.com/api/webhooks/YOUR_WEBHOOK_URL")) {
            return;
        }
        
        String content = String.format(
            "**VPN Connection Blocked**\\n" +
            "Player: `%s`\\n" +
            "IP: `%s`\\n" +
            "Type: `%s`\\n" +
            "Provider: `%s`",
            playerName, ipAddress, vpnType, provider
        );
        
        String thumbnailUrl = "https://mc-heads.net/avatar/" + playerName;
        
        sendMessageWithThumbnail(content, 10181046, thumbnailUrl); // Purple color
    }
    
    private void sendMessage(String content, int color) {
        sendMessageWithThumbnail(content, color, null);
    }
    
    private void sendMessageWithThumbnail(String content, int color, String thumbnailUrl) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(webhookUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("User-Agent", "GeoPlugin-Webhook");
                    connection.setDoOutput(true);
                    
                    String thumbnailJson = "";
                    if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                        thumbnailJson = String.format(",\"thumbnail\":{\"url\":\"%s\"}", thumbnailUrl);
                    }
                    
                    String timestamp = java.time.Instant.now().toString();
                    
                    String json = String.format(
                        "{\"embeds\":[{\"author\":{\"name\":\"GeoPlugin\",\"icon_url\":\"https://imgur.com/3iso2BM.png\"},\"description\":\"%s\",\"color\":%d%s,\"timestamp\":\"%s\"}]}",
                        content, color, thumbnailJson, timestamp
                    );
                    
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = json.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode != 204) {
                        logger.warning("Discord webhook returned status code: " + responseCode);
                    }
                    
                    connection.disconnect();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to send Discord webhook", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
