package me.palgato.geoplugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.logging.Logger;

public final class TranslationManager {

    public enum Language {
        EN,
        ES
    }

    private final Language language;

    private TranslationManager(Language language) {
        this.language = language;
    }

    public static TranslationManager fromConfig(FileConfiguration config, Logger logger) {
        String raw = config.getString("language", "en");
        String normalized = raw == null ? "en" : raw.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("es") || normalized.equals("spanish")) {
            return new TranslationManager(Language.ES);
        }

        if (!normalized.equals("en") && !normalized.equals("english")) {
            logger.warning("Invalid language value '" + raw + "'. Using 'en'.");
        }

        return new TranslationManager(Language.EN);
    }

    public static TranslationManager english() {
        return new TranslationManager(Language.EN);
    }

    public Language getLanguage() {
        return language;
    }

    public String getCode() {
        return language == Language.ES ? "es" : "en";
    }

    public String tr(String key, Object... args) {
        if ("cmd.kicked_players_count".equals(key)) {
            int count = toIntArg(args, 0);
            return select(
                String.format("Kicked %d %s due to access control rules.", count, pluralEn(count, "player", "players")),
                String.format("Se expulsó a %d %s por reglas de acceso por país.", count, pluralEs(count, "jugador", "jugadores"))
            );
        }

        if ("cmd.online_players".equals(key)) {
            int count = toIntArg(args, 0);
            return select(
                String.format("Online %s (%d):", pluralEn(count, "player", "players"), count),
                String.format("%s en línea (%d):", pluralEs(count, "Jugador", "Jugadores"), count)
            );
        }

        if ("cmd.connections_count".equals(key)) {
            int count = toIntArg(args, 0);
            return select(
                String.format("%d %s", count, pluralEn(count, "connection", "connections")),
                String.format("%d %s", count, pluralEs(count, "conexión", "conexiones"))
            );
        }

        if ("cmd.unique_players_count".equals(key)) {
            int count = toIntArg(args, 0);
            return select(
                String.format("%d %s", count, pluralEn(count, "unique player", "unique players")),
                String.format("%d %s", count, pluralEs(count, "jugador único", "jugadores únicos"))
            );
        }

        if ("log.country_access_enabled".equals(key)) {
            String mode = String.valueOf(args[0]);
            int count = toIntArg(args, 1);
            return select(
                String.format("Country access control enabled in %s mode with %d %s.", mode, count, pluralEn(count, "country", "countries")),
                String.format("Control de acceso por país activado en modo %s con %d %s.", mode, count, pluralEs(count, "país", "países"))
            );
        }

        if ("log.country_access_reloaded".equals(key)) {
            String mode = String.valueOf(args[0]);
            int count = toIntArg(args, 1);
            return select(
                String.format("Country access control reloaded: %s mode with %d %s.", mode, count, pluralEn(count, "country", "countries")),
                String.format("Control de acceso por país recargado: modo %s con %d %s.", mode, count, pluralEs(count, "país", "países"))
            );
        }

        String template = switch (key) {
            case "cmd.no_permission" -> select("Insufficient permissions.", "No tienes permisos suficientes.");
            case "cmd.no_permission_specific" -> select("Insufficient permissions for this command.", "No tienes permisos suficientes para este comando.");
            case "cmd.available_commands" -> select("Available commands:", "Comandos disponibles:");
            case "cmd.usage_countrycheck" -> "Usage: /geoplugin countrycheck <ip|player>";
            case "cmd.unable_resolve_player_address" -> select("Unable to resolve player address.", "No se pudo resolver la dirección del jugador.");
            case "cmd.invalid_ip_or_player" -> select("Invalid ip or player: %s", "IP o jugador inválido: %s");
            case "cmd.failed_resolve" -> select("Failed to resolve: %s", "No se pudo resolver: %s");
            case "cmd.desc_countrycheck" -> select("Check country code for ip address or player", "Revisar código de país para IP o jugador");
            case "cmd.invalid_plugin_instance" -> select("Invalid plugin instance.", "Instancia de plugin inválida.");
            case "cmd.vpn_detection_disabled" -> select("VPN detection is disabled.", "La detección de VPN está desactivada.");
            case "cmd.usage_vpncheck" -> "Usage: /geoplugin vpncheck <ip|player>";
            case "cmd.player_not_found" -> select("Player not found: %s", "Jugador no encontrado: %s");
            case "cmd.vpn_detected" -> select("VPN/Proxy Detected", "VPN/Proxy detectado");
            case "cmd.label_ip" -> "IP: ";
            case "cmd.label_type" -> select("Type: ", "Tipo: ");
            case "cmd.label_provider" -> select("Provider: ", "Proveedor: ");
            case "cmd.label_risk_score" -> select("Risk Score: ", "Puntaje de riesgo: ");
            case "cmd.clean_ip" -> select("Clean IP", "IP limpia");
            case "cmd.label_status" -> select("Status: ", "Estado: ");
            case "cmd.failed_check" -> select("Failed to check: %s", "No se pudo comprobar: %s");
            case "cmd.desc_vpncheck" -> select("Check if ip address or player is using VPN/Proxy", "Comprobar si una IP o jugador usa VPN/Proxy");
            case "cmd.usage_ip" -> "Usage: /geoplugin ip <player>";
            case "cmd.desc_ip" -> select("Get player IP address (click to copy)", "Obtener IP del jugador (clic para copiar)");
            case "cmd.kicked_player_from_country" -> select("Kicked %s from %s", "Expulsado %s de %s");
            case "cmd.reload_success" -> select("Configuration reloaded successfully.", "Configuración recargada correctamente.");
            case "cmd.reload_failed" -> select("Failed to reload configuration. Check console for details.", "No se pudo recargar la configuración. Revisa la consola para más detalles.");
            case "cmd.desc_reload" -> select("Reload plugin configuration", "Recargar configuración del plugin");
            case "cmd.no_players_online" -> select("No players online.", "No hay jugadores en línea.");
            case "cmd.usage_list" -> "Usage: /geoplugin list [page]";
            case "cmd.invalid_page" -> select("Invalid page number. Use a positive integer.", "Número de página inválido. Usa un entero positivo.");
            case "cmd.list_header" -> select("Online players (%d) - Page %d/%d:", "Jugadores en línea (%d) - Página %d/%d:");
            case "cmd.pagination_prev" -> select("[Prev]", "[Anterior]");
            case "cmd.pagination_next" -> select("[Next]", "[Siguiente]");
            case "cmd.pagination_prev_hover" -> select("Click to go to previous page", "Clic para ir a la página anterior");
            case "cmd.pagination_next_hover" -> select("Click to go to next page", "Clic para ir a la página siguiente");
            case "cmd.pagination_page" -> select("Page %d/%d", "Página %d/%d");
            case "cmd.pagination_console_hint" -> select("Page %d/%d. Use /geoplugin list <page> to navigate.", "Página %d/%d. Usa /geoplugin list <página> para navegar.");
            case "cmd.unknown_country" -> select("Unknown", "Desconocido");
            case "cmd.desc_list" -> select("List all online players with their countries", "Listar jugadores en línea con su país");
            case "cmd.stats_unavailable" -> select("Statistics unavailable.", "Estadísticas no disponibles.");
            case "cmd.country_statistics" -> select("Country Statistics:", "Estadísticas por país:");
            case "cmd.total_connections" -> select("Total connections: ", "Conexiones totales: ");
            case "cmd.unique_players" -> select("Unique players: ", "Jugadores únicos: ");
            case "cmd.top_countries_connections" -> select("Top %d Countries by Total Connections:", "Top %d países por conexiones totales:");
            case "cmd.top_countries_unique" -> select("Top %d Countries by Unique Players:", "Top %d países por jugadores únicos:");
            case "cmd.desc_stats" -> select("View country connection statistics", "Ver estadísticas de conexiones por país");
            case "cmd.players_only" -> select("This command can only be used by players.", "Este comando solo puede ser usado por jugadores.");
            case "cmd.notifications_unavailable" -> select("Notifications unavailable.", "Notificaciones no disponibles.");
            case "cmd.notifications_cannot_enable" -> select("Cannot enable notifications: Both systems are disabled.", "No se pueden activar notificaciones: ambos sistemas están desactivados.");
            case "cmd.notifications_enable_hint1" -> select("Enable country-access-control or vpn-detection in config.yml", "Activa country-access-control o vpn-detection en config.yml");
            case "cmd.notifications_enable_hint2" -> select("and use /geoplugin reload", "y usa /geoplugin reload");
            case "cmd.notifications_enabled" -> select("Block notifications enabled.", "Notificaciones de bloqueo activadas.");
            case "cmd.notifications_alerts_for" -> select("You will receive alerts for:", "Recibirás alertas de:");
            case "cmd.notifications_alert_country" -> select("  • Country access control blocks", "  • Bloqueos por control de acceso por país");
            case "cmd.notifications_alert_vpn" -> select("  • VPN/Proxy detection blocks", "  • Bloqueos por detección de VPN/Proxy");
            case "cmd.notifications_country_only" -> select("You will receive alerts when players are blocked by country restrictions.", "Recibirás alertas cuando jugadores sean bloqueados por restricciones de país.");
            case "cmd.notifications_vpn_disabled_note" -> select("  (VPN detection is disabled)", "  (la detección de VPN está desactivada)");
            case "cmd.notifications_vpn_only" -> select("You will receive alerts when players are blocked by VPN/Proxy detection.", "Recibirás alertas cuando jugadores sean bloqueados por detección de VPN/Proxy.");
            case "cmd.notifications_country_disabled_note" -> select("  (Country access control is disabled)", "  (el control de acceso por país está desactivado)");
            case "cmd.notifications_disabled" -> select("Block notifications disabled.", "Notificaciones de bloqueo desactivadas.");
            case "cmd.desc_notify" -> select("Toggle blocked connection notifications", "Alternar notificaciones de bloqueos");
            case "cmd.desc_help" -> select("Display available commands", "Mostrar comandos disponibles");

            case "log.discord_enabled" -> select("Discord webhook integration enabled.", "Integración de webhook de Discord activada.");
            case "log.custom_webhook_enabled" -> select("Custom webhook integration enabled.", "Integración de webhook personalizado activada.");
            case "log.suspicious_tracking_enabled" -> select("Suspicious activity tracking enabled.", "Seguimiento de actividad sospechosa activado.");
            case "log.vpn_enabled" -> select("VPN detection enabled.", "Detección de VPN activada.");
            case "log.placeholder_enabled" -> select("PlaceholderAPI integration enabled.", "Integración con PlaceholderAPI activada.");
            case "log.failed_geoip_init" -> select("Failed to initialize GeoIP database", "No se pudo inicializar la base de datos GeoIP");
            case "log.blocked_connection_country" -> select("Blocked connection from %s (Country: %s)", "Conexión bloqueada de %s (País: %s)");
            case "admin.blocked" -> select("Blocked: %s from %s", "Bloqueado: %s desde %s");
            case "log.blocked_vpn_connection" -> select("Blocked VPN connection from %s (%s - %s)", "Conexión VPN bloqueada de %s (%s - %s)");
            case "admin.blocked_vpn" -> select("Blocked VPN: %s (%s)", "VPN bloqueada: %s (%s)");
            case "log.player_connected_from" -> select("%s connected from: %s%s", "%s se conectó desde: %s%s");
            case "log.vpn_reloaded" -> select("VPN detection reloaded.", "Detección de VPN recargada.");
            case "log.country_access_disabled" -> select("Country access control is disabled.", "El control de acceso por país está desactivado.");

            case "warn.threshold_invalid" -> select("suspicious-activity.threshold must be >= 1. Using 5.", "suspicious-activity.threshold debe ser >= 1. Usando 5.");
            case "warn.timewindow_invalid" -> select("suspicious-activity.time-window-minutes must be >= 1. Using 10.", "suspicious-activity.time-window-minutes debe ser >= 1. Usando 10.");
            case "warn.webhook_null" -> select("%s is null. Webhook integration disabled.", "%s es null. Integración webhook desactivada.");
            case "warn.webhook_empty" -> select("%s is empty. Webhook integration disabled.", "%s está vacío. Integración webhook desactivada.");
            case "warn.webhook_protocol" -> select("%s must use http or https. Webhook integration disabled.", "%s debe usar http o https. Integración webhook desactivada.");
            case "warn.webhook_invalid" -> select("Invalid URL in %s: '%s'. Webhook integration disabled.", "URL inválida en %s: '%s'. Integración webhook desactivada.");

            case "warn.invalid_mode" -> select("Invalid country-access-control.mode value '%s'. Using 'blacklist'.", "Valor inválido en country-access-control.mode: '%s'. Usando 'blacklist'.");
            case "warn.invalid_country_code" -> select("Ignoring invalid country code in config: '%s'. Expected ISO 3166-1 alpha-2 (e.g. US, BR).", "Ignorando código de país inválido en config: '%s'. Se espera ISO 3166-1 alpha-2 (ej: US, BR).");
            case "warn.country_enabled_no_valid" -> select("country-access-control is enabled but no valid country codes were found.", "country-access-control está activado pero no se encontraron códigos de país válidos.");
            case "warn.cache_min" -> select("vpn-detection.cache-duration-minutes must be >= 1. Using 60.", "vpn-detection.cache-duration-minutes debe ser >= 1. Usando 60.");
            case "warn.cache_max" -> select("vpn-detection.cache-duration-minutes is too high (%d). Clamping to 1440.", "vpn-detection.cache-duration-minutes es demasiado alto (%d). Ajustando a 1440.");
            case "warn.risk_invalid" -> select("vpn-detection.min-risk-score must be between 0 and 100. Using 70.", "vpn-detection.min-risk-score debe estar entre 0 y 100. Usando 70.");
            case "warn.whitelist_ip_invalid" -> select("Ignoring invalid IP in vpn-detection.whitelist-ips: '%s'.", "Ignorando IP inválida en vpn-detection.whitelist-ips: '%s'.");
            case "warn.whitelist_uuid_invalid" -> select("Ignoring invalid UUID in vpn-detection.whitelist-player-uuids: '%s'.", "Ignorando UUID inválido en vpn-detection.whitelist-player-uuids: '%s'.");
            case "warn.vpn_no_api" -> select("vpn-detection is enabled without api-key. Detection works but may be less reliable due to rate limits.", "vpn-detection está activado sin api-key. Funciona, pero puede ser menos confiable por límites de tasa.");
            case "warn.failed_check_vpn" -> select("Failed to check VPN for IP: %s", "No se pudo comprobar VPN para IP: %s");
            case "warn.reload_failed" -> select("Failed to reload config", "No se pudo recargar config");

            case "vpn.type.disabled" -> select("Disabled", "Desactivado");
            case "vpn.type.private" -> select("Private", "Privada");
            case "vpn.type.whitelisted" -> select("Whitelisted", "En whitelist");
            case "vpn.type.clean" -> select("Clean", "Limpia");
            case "vpn.type.error" -> select("Error", "Error");
            case "vpn.type.vpn" -> "VPN";
            case "vpn.type.proxy" -> "Proxy";
            case "vpn.type.tor" -> "Tor";
            case "vpn.type.anonymous" -> select("Anonymous", "Anónima");
            case "vpn.type.hosting" -> select("Hosting", "Hosting");
            case "vpn.type.high_risk" -> select("High Risk", "Alto riesgo");
            default -> key;
        };

        if (args == null || args.length == 0) {
            return template;
        }

        return String.format(template, args);
    }

    private String select(String en, String es) {
        return language == Language.ES ? es : en;
    }

    private int toIntArg(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) {
            return 0;
        }

        Object value = args[index];
        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String pluralEn(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

    private String pluralEs(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }
}
