package com.thebyteslayer.minecraft.event.ffa.commands

import com.thebyteslayer.minecraft.event.ffa.EventFFAPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class FFACommand(private val plugin: EventFFAPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        if (!sender.hasPermission("eventffa.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "init" -> {
                if (plugin.ffaManager.initFFA(sender)) {
                    sender.sendMessage("§aFFA initialized successfully!")
                }
            }
            "start" -> {
                if (plugin.ffaManager.startFFA(sender)) {
                    sender.sendMessage("§aFFA started successfully!")
                }
            }
            "stop" -> {
                if (plugin.ffaManager.stopFFA()) {
                    sender.sendMessage("§aFFA stopped successfully!")
                } else {
                    sender.sendMessage("§cFFA is not currently active!")
                }
            }
            "leaderboard", "lb" -> {
                val leaderboard = plugin.ffaManager.getLeaderboard()
                leaderboard.forEach { line ->
                    sender.sendMessage(line)
                }
            }
            else -> sendHelp(sender)
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("init", "start", "stop", "leaderboard", "lb").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun sendHelp(sender: Player) {
        sender.sendMessage("§6=== FFA Commands ===")
        sender.sendMessage("§e/ffa init §7- Initialize FFA (set border, restrict movement)")
        sender.sendMessage("§e/ffa start §7- Start FFA session")
        sender.sendMessage("§e/ffa stop §7- Stop FFA session")
        sender.sendMessage("§e/ffa leaderboard §7- Show current leaderboard")
        sender.sendMessage("§e/ffa lb §7- Show current leaderboard")
    }
}
