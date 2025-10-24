package com.thebyteslayer.minecraft.ffa

import com.thebyteslayer.minecraft.ffa.FFAPlugin
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.random.Random

class rtp(private val plugin: FFAPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }

        // Allow RTP command for regular players during active FFA
        if (!plugin.ffaManager.isFFAStarted()) {
            sender.sendMessage("§cFFA is not currently active!")
            return true
        }

        teleportToRandomLocation(sender, plugin.ffaManager.getCurrentBorderSize().toInt() / 2)
        sender.sendTitle("§aRTP!", plugin.messagesConfig.getString("respawn_rtp", ""), 10, 70, 20)
        sender.sendMessage("§aTeleported to a random location!")
        return true
    }

    private fun isDangerousBlock(block: org.bukkit.block.Block): Boolean {
        val type = block.type
        return when (type) {
            Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.MAGMA_BLOCK -> true
            else -> false
        }
    }

    private fun isTreeBlock(block: org.bukkit.block.Block): Boolean {
        val type = block.type
        val typeName = type.name
        return typeName.contains("LOG") || typeName.contains("LEAVES") || typeName.contains("WOOD")
    }

    private fun isInCave(world: org.bukkit.World, x: Int, y: Int, z: Int): Boolean {
        // Check if there are solid blocks above the player indicating cave/underground
        var solidBlocksAbove = 0
        for (checkY in y + 2..y + 10) {
            val block = world.getBlockAt(x, checkY, z)
            if (block.type.isSolid && !block.type.name.contains("LEAVES")) {
                solidBlocksAbove++
                if (solidBlocksAbove >= 3) { // If 3+ solid blocks above, likely in a cave
                    return true
                }
            }
        }
        return false
    }

    private fun isLocationSafe(world: org.bukkit.World, x: Int, y: Int, z: Int): Boolean {
        // Check the block we're standing on
        val standingBlock = world.getBlockAt(x, y, z)
        val groundBlock = world.getBlockAt(x, y - 1, z)

        // Must have solid ground and air to stand in
        if (!groundBlock.type.isSolid || !standingBlock.isEmpty) {
            return false
        }

        // Check for headroom (2 blocks above)
        val headBlock = world.getBlockAt(x, y + 1, z)
        if (!headBlock.isEmpty) {
            return false
        }

        // Check if location is in a cave
        if (isInCave(world, x, y, z)) {
            return false
        }

        // Check for dangerous blocks in a 3x3x3 area around the player
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val checkBlock = world.getBlockAt(x + dx, y + dy, z + dz)
                    if (isDangerousBlock(checkBlock)) {
                        return false
                    }
                    // Check for tree blocks in a 5x5x5 area around the player
                    if (isTreeBlock(checkBlock)) {
                        return false
                    }
                }
            }
        }

        // Additional tree check in a wider area to avoid teleporting near trees
        for (dx in -3..3) {
            for (dz in -3..3) {
                for (checkY in y - 1..y + 5) {
                    val checkBlock = world.getBlockAt(x + dx, checkY, z + dz)
                    if (isTreeBlock(checkBlock)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    fun teleportToRandomLocation(player: Player, radius: Int) {
        val world = player.world
        val border = world.worldBorder

        var attempts = 0
        val maxAttempts = 100 // Increased attempts for safer teleportation

        while (attempts < maxAttempts) {
            val angle = Random.nextDouble() * 2 * Math.PI
            val distance = Random.nextDouble() * radius

            val x = border.center.x + distance * Math.cos(angle)
            val z = border.center.z + distance * Math.sin(angle)

            // Find a safe Y position (above Y=64 to avoid caves)
            for (y in 320 downTo 64) {
                val blockX = x.toInt()
                val blockZ = z.toInt()

                // Use the new safety check
                if (isLocationSafe(world, blockX, y, blockZ)) {
                    val location = Location(world, x + 0.5, y.toDouble(), z + 0.5)
                    player.teleport(location)
                    return
                }
            }
            attempts++
        }

        // Fallback: use the old method if we can't find a safe spot
        val angle = Random.nextDouble() * 2 * Math.PI
        val distance = Random.nextDouble() * radius
        val x = border.center.x + distance * Math.cos(angle)
        val z = border.center.z + distance * Math.sin(angle)
        val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1.0
        val location = Location(world, x, y, z)
        player.teleport(location)
    }
}
