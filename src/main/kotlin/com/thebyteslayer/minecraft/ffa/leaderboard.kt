package com.thebyteslayer.minecraft.ffa

import com.thebyteslayer.minecraft.ffa.FFAPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class leaderboard(private val plugin: FFAPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        if (!sender.hasPermission("eventffa.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }

        val leaderboard = plugin.ffaManager.getLeaderboard()
        leaderboard.forEach { line ->
            sender.sendMessage(line)
        }
        return true
    }
}
