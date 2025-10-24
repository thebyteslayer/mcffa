package com.thebyteslayer.minecraft.ffa

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class environment(private val plugin: FFAPlugin) : CommandExecutor {

    fun setupEnvironment(sender: Player? = null): Boolean {
        // Set world border using config values
        val world = sender?.world ?: Bukkit.getWorlds().first()
        val border = world.worldBorder
        val centerX = plugin.config.getDouble("border.center_x", 0.0)
        val centerZ = plugin.config.getDouble("border.center_z", 0.0)
        val radius = plugin.config.getDouble("border.radius", 1000.0)
        border.center = Location(world, centerX, 0.0, centerZ)
        border.size = radius * 2 // Border size is diameter, radius is half

        // Set the current border size in FFA manager
        plugin.ffaManager.setCurrentBorderSize(radius * 2)

        // Prevent flying and other movements except walking, set to Adventure mode
        for (player in Bukkit.getOnlinePlayers()) {
            player.allowFlight = false
            player.isFlying = false
            player.walkSpeed = 0.2f
            player.flySpeed = 0.1f
            player.gameMode = GameMode.ADVENTURE
        }

        // Send success message if sender provided
        sender?.sendMessage("§aFFA environment initialized! Border set to ${radius.toInt()}x${radius.toInt()}, players set to Adventure mode and can only walk.")

        return true
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        if (!sender.hasPermission("eventffa.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }

        if (setupEnvironment(sender)) {
            // Message is already sent by setupEnvironment method
        }
        return true
    }
}
