# Changelog

## 1.0.0 - 2026-04-07

### Added
- Core GeoIP detection using MaxMind country database (`countries.mmdb`).
- Country access control system with `whitelist`/`blacklist` modes.
- VPN/Proxy detection via proxycheck.io with login-time checks and optional blocking.
- Country statistics tracking (total connections + unique players by country).
- Admin notifications for blocked joins, plus suspicious activity tracking.
- Discord webhook integration for block/suspicious events.
- Custom webhook integration for external automations.
- PlaceholderAPI integration (optional, auto-register when installed).
- Command suite: `help`, `reload`, `countrycheck`, `vpncheck`, `ip`, `list`, `stats`, `notify`.
- EN/ES language support with plural-aware messages.
- Paginated `/geoplugin list` output and improved `/geoplugin stats` readability.
- Country/stats placeholders for scoreboards and HUDs.
- VPN bypass by player UUID (`vpn-detection.whitelist-player-uuids`).

### Improved
- Stronger config validation and clearer configuration comments.
- Better operational clarity around bypass permissions (`geoplugin.*`, `geoplugin.bypass`, `geoplugin.bypass.vpn`).
- Better compatibility testing coverage with standalone and proxy-based labs.
- Verified forwarding-based deployments with Velocity and Bungee-style setups.

### Compatibility
- Designed for Spigot/Paper API (`api-version: 1.20`).
- Tested in local lab scenarios behind Velocity and Bungee-style proxies.
- PlaceholderAPI support is optional (`softdepend`).

### Removed
- In-session IP-change kick logic.
- VPN status placeholders (`is_vpn`, `vpn_type`, and player-targeted VPN placeholders).
