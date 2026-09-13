package com.darkignite.proximityantixray.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import com.darkignite.proximityantixray.ProximityAntiXray;

public final class ProximityAntiXrayCommand implements CommandExecutor, TabCompleter {
    private final ProximityAntiXray plugin;

    public ProximityAntiXrayCommand(ProximityAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("proximityantixray.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("timings")) {
            boolean enabled = !plugin.isTimingsEnabled();
            plugin.setTimingsEnabled(enabled);
            sender.sendMessage(ChatColor.GOLD + "[ProximityAntiXray] " + ChatColor.YELLOW + "Timings log is now " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "ProximityAntiXray v1.0.0" + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.GRAY + "Author: " + ChatColor.WHITE + "DarkIgnite");
        sender.sendMessage(ChatColor.GRAY + "Active tracked players: " + ChatColor.AQUA + plugin.getPlayerData().size());
        sender.sendMessage(ChatColor.GRAY + "Status: " + (plugin.isRunning() ? ChatColor.GREEN + "Running" : ChatColor.RED + "Stopped"));
        sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/" + label + " timings");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            if ("timings".startsWith(args[0].toLowerCase())) {
                list.add("timings");
            }
        }
        return list;
    }
}
