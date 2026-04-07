# GeoPlugin

GeoPlugin is a GeoIP and VPN/Proxy protection plugin for Minecraft servers.

It supports country-based access rules, VPN/proxy checks on login, country statistics, suspicious activity alerts, and webhook notifications.

## Compatibility

- Paper
- Purpur
- Spigot
- Works behind proxy networks (Velocity/Bungee-style) when IP forwarding is configured correctly

## Optional integrations

- PlaceholderAPI (optional, auto-detected)
- Discord webhooks
- Custom webhooks (n8n, Make, Zapier, internal APIs)

## Features

- GeoIP country detection using MaxMind database (`countries.mmdb`)
- Country access control (`whitelist` or `blacklist` mode)
- VPN/Proxy detection via proxycheck.io
- Login-time VPN blocking (configurable)
- Country statistics (total and unique players)
- Suspicious activity tracking
- Discord embeds and custom webhook payloads
- EN/ES messages with plural-aware text
- Paginated online player list
- Bypass options by permission, IP whitelist, and player UUID whitelist

## Commands

- `/geoplugin help`
- `/geoplugin reload`
- `/geoplugin countrycheck <ip|player>`
- `/geoplugin vpncheck <ip|player>`
- `/geoplugin ip <player>`
- `/geoplugin list [page]`
- `/geoplugin stats`
- `/geoplugin notify`

## Permissions

- `geoplugin.bypass` -> bypass country + VPN checks
- `geoplugin.bypass.country` -> bypass country checks only
- `geoplugin.bypass.vpn` -> bypass VPN checks only

Note: wildcard permission sets like `geoplugin.*` may include bypass behavior depending on your permission setup.

## Proxy forwarding notes

If you run behind a proxy, configure forwarding correctly or the server will not see real client IPs.

- BungeeCord: `ip_forward: true`
- Spigot backend: `settings.bungeecord: true` in `spigot.yml`
- Velocity: modern forwarding with matching forward secret

## Installation

1. Build or download `GeoPlugin-<version>.jar`.
2. Put it in your server `plugins/` folder.
3. Start server once to generate config.
4. Configure `config.yml`.
5. Run `/geoplugin reload` or restart.

## Configuration highlights

In `config.yml`:

- `country-access-control.*` for country rules
- `vpn-detection.*` for VPN checks and blocking behavior
- `suspicious-activity.*` for threshold/window alert logic
- `discord.*` and `custom-webhook.*` for outbound alerts

## Dependencies

- Required: none
- Optional: PlaceholderAPI

## License

This project is licensed under the MIT License. See the LICENSE file.
