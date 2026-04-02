package me.palgato.geoplugin;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class GeoCommands implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "geoplugin.use";
    private static final String PERM_RELOAD = "geoplugin.reload";
    private static final String PERM_COUNTRYCHECK = "geoplugin.countrycheck";
    private static final String PERM_VPNCHECK = "geoplugin.vpncheck";
    private static final String PERM_IP = "geoplugin.ip";
    private static final String PERM_LIST = "geoplugin.list";
    private static final String PERM_STATS = "geoplugin.stats";
    private static final String PERM_NOTIFY = "geoplugin.notify";
    private static final int LIST_PAGE_SIZE = 10;
    private static final int STATS_TOP_LIMIT = 10;
    
    private static final String MSG_PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "Geo" + ChatColor.DARK_GRAY + "] ";
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );
    
    private final GeoManager geoManager;
    private final Plugin plugin;
    private final Map<String, SubCommand> subCommands;

    public GeoCommands(GeoManager geoManager, Plugin plugin) {
        this.geoManager = geoManager;
        this.plugin = plugin;
        
        Map<String, SubCommand> commands = new LinkedHashMap<>();
        commands.put("help", new HelpCommand());
        commands.put("reload", new ReloadCommand());
        commands.put("countrycheck", new CountryCheckCommand());
        commands.put("vpncheck", new VpnCheckCommand());
        commands.put("ip", new IpCommand());
        commands.put("list", new ListCommand());
        commands.put("stats", new StatsCommand());
        commands.put("notify", new NotifyCommand());
        this.subCommands = commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sendUsage(sender);
            return true;
        }
        
        if (!hasCommandPermission(sender, subCommandName)) {
            sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.no_permission_specific"));
            return true;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        subCommand.execute(sender, subArgs);
        return true;
    }
    
    private boolean hasCommandPermission(CommandSender sender, String commandName) {
        if (commandName.equals("reload")) {
            return sender.hasPermission(PERM_RELOAD);
        } else if (commandName.equals("countrycheck")) {
            return sender.hasPermission(PERM_COUNTRYCHECK);
        } else if (commandName.equals("vpncheck")) {
            return sender.hasPermission(PERM_VPNCHECK);
        } else if (commandName.equals("ip")) {
            return sender.hasPermission(PERM_IP);
        } else if (commandName.equals("list")) {
            return sender.hasPermission(PERM_LIST);
        } else if (commandName.equals("stats")) {
            return sender.hasPermission(PERM_STATS);
        } else if (commandName.equals("notify")) {
            return sender.hasPermission(PERM_NOTIFY);
        } else {
            return sender.hasPermission(PERM_USE);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM_USE)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return subCommands.keySet().stream()
                .filter(cmd -> hasCommandPermission(sender, cmd))
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("countrycheck") && sender.hasPermission(PERM_COUNTRYCHECK)) {
                return sender.getServer().getOnlinePlayers().stream()
                    .map(player -> player.getName())
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            
            if (args[0].equalsIgnoreCase("vpncheck") && sender.hasPermission(PERM_VPNCHECK)) {
                return sender.getServer().getOnlinePlayers().stream()
                    .map(player -> player.getName())
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            
            if (args[0].equalsIgnoreCase("ip") && sender.hasPermission(PERM_IP)) {
                return sender.getServer().getOnlinePlayers().stream()
                    .map(player -> player.getName())
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + t("cmd.available_commands"));
        subCommands.forEach((name, cmd) -> {
            if (hasCommandPermission(sender, name)) {
                sender.sendMessage(ChatColor.YELLOW + "/geoplugin " + name + 
                    ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + cmd.getDescription());
            }
        });
    }

    private interface SubCommand {
        void execute(CommandSender sender, String[] args);
        String getDescription();
    }

    private record PlayerCountryEntry(String playerName, String countryCode) {}

    private final class CountryCheckCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.usage_countrycheck"));
                return;
            }

            String input = args[0];
            Player player = sender.getServer().getPlayerExact(input);
            
            if (player != null) {
                InetSocketAddress socketAddress = player.getAddress();
                if (socketAddress == null) {
                    sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.unable_resolve_player_address"));
                    return;
                }
                
                String countryCode = geoManager.getCountryCodeOrDefault(socketAddress.getAddress());
                sender.sendMessage(MSG_PREFIX + ChatColor.WHITE + input + 
                    ChatColor.GRAY + " -> " + ChatColor.YELLOW + countryCode);
                return;
            }

            if (!IP_PATTERN.matcher(input).matches()) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + String.format(t("cmd.invalid_ip_or_player"), ChatColor.WHITE + input));
                return;
            }
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        InetAddress address = InetAddress.getByName(input);
                        String countryCode = geoManager.getCountryCodeOrDefault(address);
                        
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                sender.sendMessage(MSG_PREFIX + ChatColor.WHITE + input + 
                                    ChatColor.GRAY + " -> " + ChatColor.YELLOW + countryCode);
                            }
                        }.runTask(plugin);
                        
                    } catch (UnknownHostException e) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                sender.sendMessage(MSG_PREFIX + ChatColor.RED + String.format(t("cmd.failed_resolve"), ChatColor.WHITE + input));
                            }
                        }.runTask(plugin);
                    }
                }
            }.runTaskAsynchronously(plugin);
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_countrycheck");
        }
    }

    private final class VpnCheckCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(plugin instanceof GeoPlugin)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.invalid_plugin_instance"));
                return;
            }

            GeoPlugin geoPlugin = (GeoPlugin) plugin;
            VpnDetector vpnDetector = geoPlugin.getVpnDetector();

            if (!vpnDetector.isEnabled()) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.vpn_detection_disabled"));
                return;
            }

            if (args.length == 0) {
                sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + t("cmd.usage_vpncheck"));
                return;
            }

            String input = args[0];

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        String ipAddress;
                        if (IP_PATTERN.matcher(input).matches()) {
                            ipAddress = input;
                        } else {
                            Player target = sender.getServer().getPlayer(input);
                            if (target == null || target.getAddress() == null) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        sender.sendMessage(MSG_PREFIX + ChatColor.RED + String.format(t("cmd.player_not_found"), ChatColor.WHITE + input));
                                    }
                                }.runTask(plugin);
                                return;
                            }
                            ipAddress = target.getAddress().getAddress().getHostAddress();
                        }

                        vpnDetector.checkIp(ipAddress).thenAccept(result -> {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (result.isVpn()) {
                                        sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.vpn_detected"));
                                        sender.sendMessage(ChatColor.GRAY + t("cmd.label_ip") + ChatColor.WHITE + ipAddress);
                                        sender.sendMessage(ChatColor.GRAY + t("cmd.label_type") + ChatColor.YELLOW + mapVpnType(result.type()));
                                        sender.sendMessage(ChatColor.GRAY + t("cmd.label_provider") + ChatColor.YELLOW + result.provider());
                                        sender.sendMessage(ChatColor.GRAY + t("cmd.label_risk_score") + ChatColor.RED + result.riskScore() + "%");
                                    } else {
                                        sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + t("cmd.clean_ip"));
                                        sender.sendMessage(ChatColor.GRAY + t("cmd.label_ip") + ChatColor.WHITE + ipAddress);
                                        if (!result.type().equals("disabled") && !result.type().equals("error")) {
                                            sender.sendMessage(ChatColor.GRAY + t("cmd.label_status") + ChatColor.YELLOW + mapVpnType(result.type()));
                                        }
                                    }
                                }
                            }.runTask(plugin);
                        });

                    } catch (Exception e) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                sender.sendMessage(MSG_PREFIX + ChatColor.RED + String.format(t("cmd.failed_check"), ChatColor.WHITE + input));
                            }
                        }.runTask(plugin);
                    }
                }
            }.runTaskAsynchronously(plugin);
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_vpncheck");
        }
    }

    private final class IpCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.usage_ip"));
                return;
            }

            String playerName = args[0];
            Player target = sender.getServer().getPlayerExact(playerName);
            
            if (target == null) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + String.format(t("cmd.player_not_found"), ChatColor.WHITE + playerName));
                return;
            }
            
            InetSocketAddress socketAddress = target.getAddress();
            if (socketAddress == null) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.unable_resolve_player_address"));
                return;
            }
            
            String ipAddress = socketAddress.getAddress().getHostAddress();
            
            if (sender instanceof Player) {
                Player player = (Player) sender;
                
                TextComponent prefix = new TextComponent("[Geo] ");
                prefix.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
                
                TextComponent nameComponent = new TextComponent(playerName);
                nameComponent.setColor(net.md_5.bungee.api.ChatColor.WHITE);
                
                TextComponent arrow = new TextComponent(" -> ");
                arrow.setColor(net.md_5.bungee.api.ChatColor.GRAY);
                
                TextComponent ipComponent = new TextComponent(ipAddress);
                ipComponent.setColor(net.md_5.bungee.api.ChatColor.AQUA);
                ipComponent.setUnderlined(true);
                ipComponent.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new Text("§aCopy")
                ));
                ipComponent.setClickEvent(new ClickEvent(
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    ipAddress
                ));
                
                player.spigot().sendMessage(prefix, nameComponent, arrow, ipComponent);
            } else {
                sender.sendMessage(MSG_PREFIX + ChatColor.WHITE + playerName + 
                    ChatColor.GRAY + " -> " + ChatColor.AQUA + ipAddress);
            }
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_ip");
        }
    }

    private final class ReloadCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            try {
                plugin.reloadConfig();

                if (plugin instanceof GeoPlugin) {
                    GeoPlugin geoPlugin = (GeoPlugin) plugin;
                    geoPlugin.reloadAccessControl();

                    if (geoPlugin.getAccessControl().isEnabled()) {
                        int kickedCount = 0;

                        for (Player player : sender.getServer().getOnlinePlayers()) {
                            InetSocketAddress socketAddress = player.getAddress();
                            if (socketAddress == null) {
                                continue;
                            }

                            String countryCode = geoManager.getCountryCodeOrDefault(socketAddress.getAddress());

                            if (!geoPlugin.getAccessControl().isAllowed(countryCode)) {
                                player.kickPlayer(geoPlugin.getAccessControl().getKickMessage());
                                sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + String.format(
                                    t("cmd.kicked_player_from_country"),
                                    ChatColor.WHITE + player.getName() + ChatColor.GRAY,
                                    ChatColor.YELLOW + countryCode + ChatColor.GRAY));
                                kickedCount++;
                            }
                        }

                        if (kickedCount > 0) {
                            sender.sendMessage(MSG_PREFIX + ChatColor.YELLOW + String.format(t("cmd.kicked_players_count"), kickedCount));
                        }
                    }
                }

                sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + t("cmd.reload_success"));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, t("warn.reload_failed"), e);
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.reload_failed"));
            }
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_reload");
        }
    }

    private final class ListCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 1) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.usage_list"));
                return;
            }

            int requestedPage = 1;
            if (args.length == 1) {
                try {
                    requestedPage = Integer.parseInt(args[0]);
                    if (requestedPage < 1) {
                        sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.invalid_page"));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.invalid_page"));
                    return;
                }
            }

            List<PlayerCountryEntry> entries = new ArrayList<>();
            List<? extends Player> onlinePlayers = sender.getServer().getOnlinePlayers().stream().toList();
            if (onlinePlayers.isEmpty()) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.no_players_online"));
                return;
            }

            for (Player player : onlinePlayers) {
                InetSocketAddress socketAddress = player.getAddress();
                String countryCode = t("cmd.unknown_country");

                if (socketAddress != null) {
                    countryCode = geoManager.getCountryCodeOrDefault(socketAddress.getAddress());
                }

                entries.add(new PlayerCountryEntry(player.getName(), countryCode));
            }

            entries.sort(Comparator
                .comparing(PlayerCountryEntry::countryCode)
                .thenComparing(PlayerCountryEntry::playerName, String.CASE_INSENSITIVE_ORDER));

            int totalPages = (int) Math.ceil((double) entries.size() / LIST_PAGE_SIZE);
            int page = Math.min(requestedPage, totalPages);
            int fromIndex = (page - 1) * LIST_PAGE_SIZE;
            int toIndex = Math.min(fromIndex + LIST_PAGE_SIZE, entries.size());
            
            sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + t("cmd.list_header", entries.size(), page, totalPages));

            for (int i = fromIndex; i < toIndex; i++) {
                PlayerCountryEntry entry = entries.get(i);
                sender.sendMessage(ChatColor.DARK_GRAY + "  #" + (i + 1) + " " +
                    ChatColor.WHITE + entry.playerName() +
                    ChatColor.GRAY + " -> " +
                    ChatColor.YELLOW + entry.countryCode());
            }

            sendListPagination(sender, page, totalPages, "/geoplugin list");
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_list");
        }
    }

    private final class StatsCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(plugin instanceof GeoPlugin)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.stats_unavailable"));
                return;
            }
            
            CountryStatistics stats = ((GeoPlugin) plugin).getStatistics();
            
            sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + t("cmd.country_statistics"));
            sender.sendMessage(ChatColor.DARK_GRAY + t("cmd.total_connections") + ChatColor.WHITE + stats.getTotalConnections());
            sender.sendMessage(ChatColor.DARK_GRAY + t("cmd.unique_players") + ChatColor.WHITE + stats.getTotalUniquePlayers());
            sender.sendMessage("");
            
            sender.sendMessage(ChatColor.YELLOW + t("cmd.top_countries_connections", STATS_TOP_LIMIT));
            List<Map.Entry<String, CountryStatistics.CountryData>> topByConnections = 
                stats.getTopCountriesByConnections(STATS_TOP_LIMIT);
            
            int rank = 1;
            for (Map.Entry<String, CountryStatistics.CountryData> entry : topByConnections) {
                sender.sendMessage(ChatColor.GRAY + "  " + rank + ". " + 
                    ChatColor.YELLOW + entry.getKey() + 
                    ChatColor.DARK_GRAY + " - " + 
                    ChatColor.WHITE + t("cmd.connections_count", entry.getValue().getTotalConnections()));
                rank++;
            }
            
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + t("cmd.top_countries_unique", STATS_TOP_LIMIT));
            List<Map.Entry<String, CountryStatistics.CountryData>> topByUnique = 
                stats.getTopCountriesByUniquePlayers(STATS_TOP_LIMIT);
            
            rank = 1;
            for (Map.Entry<String, CountryStatistics.CountryData> entry : topByUnique) {
                sender.sendMessage(ChatColor.GRAY + "  " + rank + ". " + 
                    ChatColor.YELLOW + entry.getKey() + 
                    ChatColor.DARK_GRAY + " - " + 
                    ChatColor.WHITE + t("cmd.unique_players_count", entry.getValue().getUniquePlayersCount()));
                rank++;
            }
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_stats");
        }
    }

    private final class NotifyCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.players_only"));
                return;
            }
            
            if (!(plugin instanceof GeoPlugin)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.notifications_unavailable"));
                return;
            }
            
            GeoPlugin geoPlugin = (GeoPlugin) plugin;
            Player player = (Player) sender;
            
            boolean countryEnabled = geoPlugin.getAccessControl().isEnabled();
            boolean vpnEnabled = geoPlugin.getVpnDetector().isEnabled() && 
                                 geoPlugin.getVpnDetector().shouldBlockVpn();
            
            if (!countryEnabled && !vpnEnabled) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + t("cmd.notifications_cannot_enable"));
                sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_enable_hint1"));
                sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_enable_hint2"));
                return;
            }
            
            NotificationManager notificationManager = geoPlugin.getNotificationManager();
            boolean newState = notificationManager.toggle(player);
            
            if (newState) {
                sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + t("cmd.notifications_enabled"));
                
                if (countryEnabled && vpnEnabled) {
                    sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_alerts_for"));
                    sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_alert_country"));
                    sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_alert_vpn"));
                } else if (countryEnabled) {
                    sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_country_only"));
                    sender.sendMessage(ChatColor.DARK_GRAY + t("cmd.notifications_vpn_disabled_note"));
                } else {
                    sender.sendMessage(ChatColor.GRAY + t("cmd.notifications_vpn_only"));
                    sender.sendMessage(ChatColor.DARK_GRAY + t("cmd.notifications_country_disabled_note"));
                }
            } else {
                sender.sendMessage(MSG_PREFIX + ChatColor.YELLOW + t("cmd.notifications_disabled"));
            }
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_notify");
        }
    }

    private final class HelpCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            sendUsage(sender);
        }

        @Override
        public String getDescription() {
            return t("cmd.desc_help");
        }
    }

    private String t(String key, Object... args) {
        if (plugin instanceof GeoPlugin) {
            return ((GeoPlugin) plugin).tr(key, args);
        }

        return TranslationManager.english().tr(key, args);
    }

    private String mapVpnType(String rawType) {
        if (rawType == null) {
            return t("vpn.type.error");
        }

        return switch (rawType.toLowerCase()) {
            case "disabled" -> t("vpn.type.disabled");
            case "private" -> t("vpn.type.private");
            case "whitelisted" -> t("vpn.type.whitelisted");
            case "clean" -> t("vpn.type.clean");
            case "error" -> t("vpn.type.error");
            case "vpn" -> t("vpn.type.vpn");
            case "proxy" -> t("vpn.type.proxy");
            case "tor" -> t("vpn.type.tor");
            case "anonymous" -> t("vpn.type.anonymous");
            case "hosting" -> t("vpn.type.hosting");
            case "high risk" -> t("vpn.type.high_risk");
            default -> rawType;
        };
    }

    private void sendListPagination(CommandSender sender, int page, int totalPages, String commandPrefix) {
        if (totalPages <= 1) {
            return;
        }

        if (sender instanceof Player player) {
            TextComponent prev = new TextComponent(t("cmd.pagination_prev"));
            prev.setColor(net.md_5.bungee.api.ChatColor.YELLOW);

            if (page > 1) {
                prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " " + (page - 1)));
                prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(t("cmd.pagination_prev_hover"))));
            } else {
                prev.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
            }

            TextComponent separator = new TextComponent(" " + t("cmd.pagination_page", page, totalPages) + " ");
            separator.setColor(net.md_5.bungee.api.ChatColor.GRAY);

            TextComponent next = new TextComponent(t("cmd.pagination_next"));
            next.setColor(net.md_5.bungee.api.ChatColor.YELLOW);

            if (page < totalPages) {
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " " + (page + 1)));
                next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(t("cmd.pagination_next_hover"))));
            } else {
                next.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
            }

            player.spigot().sendMessage(prev, separator, next);
            return;
        }

        sender.sendMessage(ChatColor.GRAY + t("cmd.pagination_console_hint", page, totalPages));
        sender.sendMessage(ChatColor.DARK_GRAY + commandPrefix + " <page>");
    }

}