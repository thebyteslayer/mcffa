package com.thebyteslayer.minecraft.ffa

import com.thebyteslayer.minecraft.ffa.FFAPlugin
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class end(private val plugin: FFAPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.messagesConfig.getString("command.player_only")!!))
            return true
        }

        if (!sender.hasPermission("eventffa.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.messagesConfig.getString("command.no_permission")!!))
            return true
        }

        if (!plugin.ffaManager.isFFAStarted()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.messagesConfig.getString("command.end_no_session")!!))
            return true
        }

        // Start countdown
        startCountdown(sender)
        return true
    }

    private fun startCountdown(sender: Player) {
        val countdownTime = 10 // 10 seconds countdown
        val showTimes = setOf(10, 5, 3, 2, 1) // Only show countdown at these specific times

        // Show initial countdown message
        val initialMessage = plugin.messagesConfig.getString("command.end_countdown")!!
            .replace("{time}", countdownTime.toString())
            .replace("{time_label}", "seconds")
        for (player in plugin.server.onlinePlayers) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', initialMessage))
        }

        // Show countdown at specific intervals
        for (i in showTimes) {
            if (i != countdownTime) { // Skip initial time since we already showed it
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (plugin.ffaManager.isFFAStarted()) {
                        val timeLabel = if (i == 1) "second" else "seconds"
                        val message = plugin.messagesConfig.getString("command.end_countdown")!!
                            .replace("{time}", i.toString())
                            .replace("{time_label}", timeLabel)
                        for (player in plugin.server.onlinePlayers) {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
                        }
                    }
                }, (countdownTime - i) * 20L)
            }
        }

        // End the event after countdown
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (plugin.ffaManager.isFFAStarted()) {
                plugin.ffaManager.stopFFA()

                val leaderboard = plugin.ffaManager.getLeaderboard()
                val endMessage = plugin.messagesConfig.getString("command.end_success")!! 
                for (player in plugin.server.onlinePlayers) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', endMessage))
                    // Send leaderboard after end message
                    leaderboard.forEach { line ->
                        player.sendMessage(line)
                    }
                }
            }
        }, countdownTime * 20L)
    }
}
