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
import java.util.Collections;
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
            sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Insufficient permissions.");
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
            sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Insufficient permissions for this command.");
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
        sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + "Available commands:");
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

    private final class CountryCheckCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Usage: /geoplugin countrycheck <ip|player>");
                return;
            }

            String input = args[0];
            Player player = sender.getServer().getPlayerExact(input);
            
            if (player != null) {
                InetSocketAddress socketAddress = player.getAddress();
                if (socketAddress == null) {
                    sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Unable to resolve player address.");
                    return;
                }
                
                String countryCode = geoManager.getCountryCodeOrDefault(socketAddress.getAddress());
                sender.sendMessage(MSG_PREFIX + ChatColor.WHITE + input + 
                    ChatColor.GRAY + " -> " + ChatColor.YELLOW + countryCode);
                return;
            }

            if (!IP_PATTERN.matcher(input).matches()) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Invalid ip or player: " + ChatColor.WHITE + input);
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
                                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Failed to resolve: " + ChatColor.WHITE + input);
                            }
                        }.runTask(plugin);
                    }
                }
            }.runTaskAsynchronously(plugin);
        }

        @Override
        public String getDescription() {
            return "Check country code for ip address or player";
        }
    }

    private final class VpnCheckCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(plugin instanceof GeoPlugin)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Invalid plugin instance.");
                return;
            }

            GeoPlugin geoPlugin = (GeoPlugin) plugin;
            VpnDetector vpnDetector = geoPlugin.getVpnDetector();

            if (!vpnDetector.isEnabled()) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "VPN detection is disabled.");
                return;
            }

            if (args.length == 0) {
                sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + "Usage: " + ChatColor.WHITE + "/geoplugin vpncheck <ip|player>");
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
                                        sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Player not found: " + ChatColor.WHITE + input);
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
                                        sender.sendMessage(MSG_PREFIX + ChatColor.RED + "VPN/Proxy Detected");
                                        sender.sendMessage(ChatColor.GRAY + "IP: " + ChatColor.WHITE + ipAddress);
                                        sender.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + result.type());
                                        sender.sendMessage(ChatColor.GRAY + "Provider: " + ChatColor.YELLOW + result.provider());
                                        sender.sendMessage(ChatColor.GRAY + "Risk Score: " + ChatColor.RED + result.riskScore() + "%");
                                    } else {
                                        sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + "Clean IP");
                                        sender.sendMessage(ChatColor.GRAY + "IP: " + ChatColor.WHITE + ipAddress);
                                        if (!result.type().equals("disabled") && !result.type().equals("error")) {
                                            sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.YELLOW + result.type());
                                        }
                                    }
                                }
                            }.runTask(plugin);
                        });

                    } catch (Exception e) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Failed to check: " + ChatColor.WHITE + input);
                            }
                        }.runTask(plugin);
                    }
                }
            }.runTaskAsynchronously(plugin);
        }

        @Override
        public String getDescription() {
            return "Check if ip address or player is using VPN/Proxy";
        }
    }

    private final class IpCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Usage: /geoplugin ip <player>");
                return;
            }

            String playerName = args[0];
            Player target = sender.getServer().getPlayerExact(playerName);
            
            if (target == null) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Player not found: " + ChatColor.WHITE + playerName);
                return;
            }
            
            InetSocketAddress socketAddress = target.getAddress();
            if (socketAddress == null) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Unable to resolve player address.");
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
            return "Get player IP address (click to copy)";
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
                                sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + "Kicked " +
                                    ChatColor.WHITE + player.getName() +
                                    ChatColor.GRAY + " from " +
                                    ChatColor.YELLOW + countryCode);
                                kickedCount++;
                            }
                        }

                        if (kickedCount > 0) {
                            sender.sendMessage(MSG_PREFIX + ChatColor.YELLOW + "Kicked " + kickedCount +
                                " player(s) due to access control rules.");
                        }
                    }
                }

                sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + "Configuration reloaded successfully.");
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to reload config", e);
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Failed to reload configuration. Check console for details.");
            }
        }

        @Override
        public String getDescription() {
            return "Reload plugin configuration";
        }
    }

    private final class ListCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            List<? extends Player> onlinePlayers = sender.getServer().getOnlinePlayers().stream().toList();
            
            if (onlinePlayers.isEmpty()) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "No players online.");
                return;
            }
            
            Map<String, List<String>> playersByCountry = new LinkedHashMap<>();
            
            for (Player player : onlinePlayers) {
                InetSocketAddress socketAddress = player.getAddress();
                String countryCode = "Unknown";
                
                if (socketAddress != null) {
                    countryCode = geoManager.getCountryCodeOrDefault(socketAddress.getAddress());
                }
                
                playersByCountry.computeIfAbsent(countryCode, k -> new java.util.ArrayList<>()).add(player.getName());
            }
            
            sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + "Online players " + 
                ChatColor.DARK_GRAY + "(" + ChatColor.WHITE + onlinePlayers.size() + ChatColor.DARK_GRAY + "):");
            
            playersByCountry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String countryCode = entry.getKey();
                    List<String> players = entry.getValue();
                    
                    sender.sendMessage(ChatColor.YELLOW + countryCode + ChatColor.DARK_GRAY + " (" + 
                        ChatColor.WHITE + players.size() + ChatColor.DARK_GRAY + "):");
                    
                    players.forEach(playerName -> 
                        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + playerName)
                    );
                });
        }

        @Override
        public String getDescription() {
            return "List all online players with their countries";
        }
    }

    private final class StatsCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(plugin instanceof GeoPlugin)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Statistics unavailable.");
                return;
            }
            
            CountryStatistics stats = ((GeoPlugin) plugin).getStatistics();
            
            sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + "Country Statistics:");
            sender.sendMessage(ChatColor.DARK_GRAY + "Total connections: " + ChatColor.WHITE + stats.getTotalConnections());
            sender.sendMessage(ChatColor.DARK_GRAY + "Unique players: " + ChatColor.WHITE + stats.getTotalUniquePlayers());
            sender.sendMessage("");
            
            sender.sendMessage(ChatColor.YELLOW + "Top Countries by Total Connections:");
            List<Map.Entry<String, CountryStatistics.CountryData>> topByConnections = 
                stats.getTopCountriesByConnections(10);
            
            int rank = 1;
            for (Map.Entry<String, CountryStatistics.CountryData> entry : topByConnections) {
                sender.sendMessage(ChatColor.GRAY + "  " + rank + ". " + 
                    ChatColor.YELLOW + entry.getKey() + 
                    ChatColor.DARK_GRAY + " - " + 
                    ChatColor.WHITE + entry.getValue().getTotalConnections() + 
                    ChatColor.GRAY + " connections");
                rank++;
            }
            
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "Top Countries by Unique Players:");
            List<Map.Entry<String, CountryStatistics.CountryData>> topByUnique = 
                stats.getTopCountriesByUniquePlayers(10);
            
            rank = 1;
            for (Map.Entry<String, CountryStatistics.CountryData> entry : topByUnique) {
                sender.sendMessage(ChatColor.GRAY + "  " + rank + ". " + 
                    ChatColor.YELLOW + entry.getKey() + 
                    ChatColor.DARK_GRAY + " - " + 
                    ChatColor.WHITE + entry.getValue().getUniquePlayersCount() + 
                    ChatColor.GRAY + " unique players");
                rank++;
            }
        }

        @Override
        public String getDescription() {
            return "View country connection statistics";
        }
    }

    private final class NotifyCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "This command can only be used by players.");
                return;
            }
            
            if (!(plugin instanceof GeoPlugin)) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Notifications unavailable.");
                return;
            }
            
            GeoPlugin geoPlugin = (GeoPlugin) plugin;
            Player player = (Player) sender;
            
            boolean countryEnabled = geoPlugin.getAccessControl().isEnabled();
            boolean vpnEnabled = geoPlugin.getVpnDetector().isEnabled() && 
                                 geoPlugin.getVpnDetector().shouldBlockVpn();
            
            if (!countryEnabled && !vpnEnabled) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Cannot enable notifications: Both systems are disabled.");
                sender.sendMessage(ChatColor.GRAY + "Enable country-access-control or vpn-detection in config.yml");
                sender.sendMessage(ChatColor.GRAY + "and use " + ChatColor.WHITE + "/geoplugin reload");
                return;
            }
            
            NotificationManager notificationManager = geoPlugin.getNotificationManager();
            boolean newState = notificationManager.toggle(player);
            
            if (newState) {
                sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + "Block notifications enabled.");
                
                if (countryEnabled && vpnEnabled) {
                    sender.sendMessage(ChatColor.GRAY + "You will receive alerts for:");
                    sender.sendMessage(ChatColor.GRAY + "  • Country access control blocks");
                    sender.sendMessage(ChatColor.GRAY + "  • VPN/Proxy detection blocks");
                } else if (countryEnabled) {
                    sender.sendMessage(ChatColor.GRAY + "You will receive alerts when players are blocked by country restrictions.");
                    sender.sendMessage(ChatColor.DARK_GRAY + "  (VPN detection is disabled)");
                } else {
                    sender.sendMessage(ChatColor.GRAY + "You will receive alerts when players are blocked by VPN/Proxy detection.");
                    sender.sendMessage(ChatColor.DARK_GRAY + "  (Country access control is disabled)");
                }
            } else {
                sender.sendMessage(MSG_PREFIX + ChatColor.YELLOW + "Block notifications disabled.");
            }
        }

        @Override
        public String getDescription() {
            return "Toggle blocked connection notifications";
        }
    }

    private final class HelpCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            sendUsage(sender);
        }

        @Override
        public String getDescription() {
            return "Display available commands";
        }
    }
}