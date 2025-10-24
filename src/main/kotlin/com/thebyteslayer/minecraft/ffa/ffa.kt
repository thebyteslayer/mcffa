package com.thebyteslayer.minecraft.ffa

import com.thebyteslayer.minecraft.ffa.rtp
import com.thebyteslayer.minecraft.ffa.airDrop
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.*

class ffa(private val plugin: FFAPlugin) {

    private var isActive = false
    private val playerStats = mutableMapOf<UUID, PlayerStats>()
    private var borderShrinkTask: BukkitTask? = null
    private var currentBorderSize = 1000.0
    private val airDropManager = airDrop(plugin, this)

    data class PlayerStats(
        var kills: Int = 0,
        var killStreak: Int = 0,
        var bestKillStreak: Int = 0
    )


    fun startFFA(sender: Player): Boolean {
        if (isActive) {
            sender.sendMessage("§cFFA is already active!")
            return false
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            sender.sendMessage("§cNo players online to start FFA!")
            return false
        }

        isActive = true

        // Reset player stats
        playerStats.clear()
        for (player in Bukkit.getOnlinePlayers()) {
            playerStats[player.uniqueId] = PlayerStats()
        }

        // Teleport all players to random locations within the border area
        for (player in Bukkit.getOnlinePlayers()) {
            plugin.rtpManager.teleportToRandomLocation(player, currentBorderSize.toInt() / 2)
            player.gameMode = GameMode.SURVIVAL
            player.health = 20.0
            player.foodLevel = 20
            player.inventory.clear()
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }

            // Send immersive message
            player.sendTitle("§6FFA Started!", plugin.messagesConfig.getString("first_rtp", ""), 10, 70, 20)
        }

        // Start border shrinking
        startBorderShrink()

        // Start air drops
        airDropManager.startAirDrops()

        sender.sendMessage("§aFFA started! Players teleported and session begun.")
        return true
    }

    fun stopFFA(): Boolean {
        if (!isActive) return false

        isActive = false

        // Cancel tasks
        borderShrinkTask?.cancel()
        airDropManager.stopAirDrops()

        // Get top players
        val sortedPlayers = playerStats.entries
            .sortedByDescending { it.value.kills }
            .take(3)

        // Teleport players to podium locations
        for (player in Bukkit.getOnlinePlayers()) {
            val stats = playerStats[player.uniqueId] ?: PlayerStats()

            // Heal and clear inventory
            player.health = 20.0
            player.foodLevel = 20
            player.inventory.clear()
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }

            // Give infinite snowball
            val snowball = ItemStack(Material.SNOWBALL)
            val meta = snowball.itemMeta
            meta?.setDisplayName("§bSnowball")
            snowball.itemMeta = meta
            player.inventory.addItem(snowball)

            // Teleport based on rank
            val rank = sortedPlayers.indexOfFirst { it.key == player.uniqueId }
            val locationKey = when (rank) {
                0 -> "first"
                1 -> "second"
                2 -> "third"
                else -> "other"
            }

            val location = getLocationFromConfig("podium.$locationKey")
            if (location != null) {
                player.teleport(location)
            }

            // Reset game mode
            player.gameMode = GameMode.SURVIVAL
        }

        // Reset border
        val world = Bukkit.getWorlds().first()
        world.worldBorder.size = 60000000.0 // Reset to default large size

        // Clear stats
        playerStats.clear()

        return true
    }

    fun getLeaderboard(): List<String> {
        if (!isActive) return listOf("§cFFA is not active!")

        val sortedStats = playerStats.entries
            .sortedByDescending { it.value.kills }
            .take(10)

        val leaderboard = mutableListOf("§6=== FFA Leaderboard ===")
        sortedStats.forEachIndexed { index, (uuid, stats) ->
            val player = Bukkit.getPlayer(uuid)
            val name = player?.name ?: "Unknown"
            val place = index + 1
            leaderboard.add("§e$place. §f$name §7- §c${stats.kills} kills §7- §a${stats.killStreak} streak")
        }

        return leaderboard
    }

    fun handlePlayerDeath(victim: Player, killer: Player?) {
        if (!isActive) return

        val victimStats = playerStats.getOrPut(victim.uniqueId) { PlayerStats() }

        // Check for kill streak broken
        if (killer != null && victimStats.killStreak >= 3) {
            val streakBrokenMessage = plugin.messagesConfig.getString("streak_broken", "{player}'s &c{streak} &7kill streak was broken by &c{killer}&7!")
                ?.replace("{player}", victim.name)
                ?.replace("{killer}", killer.name)
                ?.replace("{streak}", victimStats.killStreak.toString())
            if (streakBrokenMessage != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', streakBrokenMessage))
            }
        }

        // Reset victim's kill streak
        victimStats.killStreak = 0

        if (killer != null) {
            val killerStats = playerStats.getOrPut(killer.uniqueId) { PlayerStats() }
            killerStats.kills++
            killerStats.killStreak++

            if (killerStats.killStreak > killerStats.bestKillStreak) {
                killerStats.bestKillStreak = killerStats.killStreak
            }

            // Play kill sound globally
            val sound = Sound.valueOf(plugin.config.getString("sounds.kill", "ENTITY_PLAYER_LEVELUP") ?: "ENTITY_PLAYER_LEVELUP")
            for (player in Bukkit.getOnlinePlayers()) {
                player.playSound(player.location, sound, 1.0f, 1.0f)
            }

            // Send kill message
            val killMessage = plugin.messagesConfig.getString("kill", "{player} was killed by {killer}!")
                ?.replace("{player}", victim.name)
                ?.replace("{killer}", killer.name)
            if (killMessage != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', killMessage))
            }
        } else {
            // Send death message
            val deathMessage = plugin.messagesConfig.getString("death", "{player} died!")
                ?.replace("{player}", victim.name)
            if (deathMessage != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', deathMessage))
            }
        }
    }

    fun handlePlayerRespawn(player: Player) {
        if (!isActive) return

        // Schedule teleport for next tick to avoid issues
        plugin.server.scheduler.runTask(plugin) { _ ->
            plugin.rtpManager.teleportToRandomLocation(player, currentBorderSize.toInt() / 2)
            player.sendTitle("§aRespawned!", plugin.messagesConfig.getString("respawn_rtp", ""), 10, 70, 20)
        }
    }

    private fun startBorderShrink() {
        val shrinkAmount = plugin.config.getInt("border.shrink_per_time", 50)
        val interval = plugin.config.getInt("border.shrink_interval", 300) * 20L // Convert to ticks

        borderShrinkTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!isActive) return@Runnable

            currentBorderSize -= shrinkAmount
            if (currentBorderSize < 50) currentBorderSize = 50.0

            val world = Bukkit.getWorlds().first()
            world.worldBorder.size = currentBorderSize

            // Warn players
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage("§cBorder shrinking to ${currentBorderSize.toInt()}x${currentBorderSize.toInt()} blocks!")
            }
        }, interval, interval)
    }

    private fun getLocationFromConfig(path: String): Location? {
        val section = plugin.config.getConfigurationSection(path) ?: return null
        val worldName = section.getString("world") ?: return null
        val world = Bukkit.getWorld(worldName) ?: return null
        val x = section.getDouble("x")
        val y = section.getDouble("y")
        val z = section.getDouble("z")

        return Location(world, x, y, z)
    }

    fun isFFAStarted(): Boolean = isActive
    fun getCurrentBorderSize(): Double = currentBorderSize
    fun setCurrentBorderSize(size: Double) {
        currentBorderSize = size
    }
    fun getPlayerStats(uuid: UUID): PlayerStats? = playerStats[uuid]
}
