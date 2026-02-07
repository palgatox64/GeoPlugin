package me.palgato.geoplugin;

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

    private static final String PERMISSION_ADMIN = "geoplugin.admin";
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
        commands.put("ipcheck", new IpCheckCommand());
        commands.put("list", new ListCommand());
        commands.put("stats", new StatsCommand());
        this.subCommands = commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Insufficient permissions.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            sendUsage(sender);
            return true;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        subCommand.execute(sender, subArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return subCommands.keySet().stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("ipcheck")) {
            return sender.getServer().getOnlinePlayers().stream()
                .map(player -> player.getName())
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }

        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MSG_PREFIX + ChatColor.GRAY + "Available commands:");
        subCommands.forEach((name, cmd) -> 
            sender.sendMessage(ChatColor.YELLOW + "/geoplugin " + name + 
                ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + cmd.getDescription())
        );
    }

    private interface SubCommand {
        void execute(CommandSender sender, String[] args);
        String getDescription();
    }

    private final class IpCheckCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.sendMessage(MSG_PREFIX + ChatColor.RED + "Usage: /geoplugin ipcheck <ip|player>");
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

    private final class ReloadCommand implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            plugin.reloadConfig();
            
            if (plugin instanceof GeoPlugin) {
                ((GeoPlugin) plugin).reloadAccessControl();
            }
            
            sender.sendMessage(MSG_PREFIX + ChatColor.GREEN + "Configuration reloaded successfully.");
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