# Changelog

## 1.0.0 - 2026-04-07

### Added
- Robust VPN/proxy detection parsing and classification.
- EN/ES language support with plural-aware messages.
- Paginated `/geoplugin list` output and improved stats readability.
- High-value placeholders for country and stats data.
- VPN bypass by player UUID (`vpn-detection.whitelist-player-uuids`).

### Improved
- Stronger config validation and clearer configuration comments.
- Better operational clarity around bypass permissions (`geoplugin.*`, `geoplugin.bypass`, `geoplugin.bypass.vpn`).
- Better compatibility testing coverage with Velocity and Bungee-style proxy labs.

### Removed
- In-session IP-change kick logic.
- VPN status placeholders (`is_vpn`, `vpn_type`, and player-targeted VPN placeholders).
