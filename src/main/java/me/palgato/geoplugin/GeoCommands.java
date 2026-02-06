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
        this.subCommands = Map.of(
            "ipcheck", new IpCheckCommand(),
            "reload", new ReloadCommand(),
            "help", new HelpCommand()
        );
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