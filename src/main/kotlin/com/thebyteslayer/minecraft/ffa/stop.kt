package com.thebyteslayer.minecraft.ffa

import com.thebyteslayer.minecraft.ffa.FFAPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class stop(private val plugin: FFAPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        if (!sender.hasPermission("eventffa.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }

        if (plugin.ffaManager.stopFFA()) {
            sender.sendMessage("§aFFA stopped successfully!")
        } else {
            sender.sendMessage("§cFFA is not currently active!")
        }
        return true
    }
}
