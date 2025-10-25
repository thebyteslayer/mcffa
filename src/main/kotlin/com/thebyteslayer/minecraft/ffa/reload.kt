package com.thebyteslayer.minecraft.ffa

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File

class reload(private val plugin: FFAPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        if (!sender.hasPermission("eventffa.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }

        // Reload config.yml
        plugin.reloadConfig()
        sender.sendMessage("§aReloaded config.yml")

        // Reload messages.yml
        val messagesFile = File(plugin.dataFolder, "messages.yml")
        plugin.messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile)
        sender.sendMessage("§aReloaded messages.yml")

        // Reload sounds.yml
        val soundsFile = File(plugin.dataFolder, "sounds.yml")
        plugin.soundsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(soundsFile)
        sender.sendMessage("§aReloaded sounds.yml")

        // Reload loot.json - this will be loaded on next air drop spawn
        // The airDrop manager will reload it when needed
        sender.sendMessage("§aLoot.json will be reloaded on next air drop spawn")

        sender.sendMessage("§aAll configurations reloaded successfully!")
        return true
    }
}
