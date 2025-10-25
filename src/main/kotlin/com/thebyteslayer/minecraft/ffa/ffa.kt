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
            return false
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
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
        if (!isActive) return emptyList()

        val sortedStats = playerStats.entries
            .sortedByDescending { it.value.kills }
            .take(10)

        val leaderboard = mutableListOf("§6=== FFA Leaderboard ===")
        sortedStats.forEachIndexed { index, (uuid, stats) ->
            val player = Bukkit.getPlayer(uuid)
            val name = player?.name ?: "Unknown"
            val place = index + 1

            // Color the first three places differently
            val placeColor = when (place) {
                1 -> "§6" // Gold for 1st
                2 -> "§7" // Gray for 2nd
                3 -> "§c" // Red for 3rd
                else -> "§e" // Yellow for others
            }

            // Use icons from suffix system instead of text
            leaderboard.add("$placeColor$place. §f$name §7- §c${stats.kills}⚔ §7- §a${stats.killStreak}🔥")
        }

        return leaderboard
    }

    fun handlePlayerDeath(victim: Player, killer: Player?) {
        if (!isActive) return

        val victimStats = playerStats.getOrPut(victim.uniqueId) { PlayerStats() }

        // Check for kill streak broken
        if (killer != null && victimStats.killStreak >= 3) {
            val streakBrokenMessage = plugin.messagesConfig.getString("broadcast.streak_broken")!!
                .replace("{player}", victim.name)
                .replace("{killer}", killer.name)
                .replace("{streak}", victimStats.killStreak.toString())
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', streakBrokenMessage))

            // Play streak broken sound globally
            val streakBrokenSound = Sound.valueOf(plugin.soundsConfig.getString("streak_broken")!!)
            for (player in Bukkit.getOnlinePlayers()) {
                player.playSound(player.location, streakBrokenSound, 1.0f, 1.0f)
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

            // Check for kill streak announcement
            if (killerStats.killStreak >= 3) {
                val streakMessage = plugin.messagesConfig.getString("broadcast.streak")!!
                    .replace("{player}", killer.name)
                    .replace("{streak}", killerStats.killStreak.toString())
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', streakMessage))

                // Play streak sound globally
                val streakSound = Sound.valueOf(plugin.soundsConfig.getString("streak")!!)
                for (player in Bukkit.getOnlinePlayers()) {
                    player.playSound(player.location, streakSound, 1.0f, 1.0f)
                }
            }

            // Play kill sound globally
            val sound = Sound.valueOf(plugin.soundsConfig.getString("kill")!!)
            for (player in Bukkit.getOnlinePlayers()) {
                player.playSound(player.location, sound, 1.0f, 1.0f)
            }

            // Send kill message
            val killMessage = plugin.messagesConfig.getString("broadcast.kill")!!
                .replace("{killed_player}", victim.name)
                .replace("{killer}", killer.name)
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', killMessage))

            // Play death sound globally
            val deathSound = Sound.valueOf(plugin.soundsConfig.getString("death")!!)
            for (player in Bukkit.getOnlinePlayers()) {
                player.playSound(player.location, deathSound, 1.0f, 1.0f)
            }
        } else {
            // Send death message
            val deathMessage = plugin.messagesConfig.getString("broadcast.death")!!
                .replace("{player}", victim.name)
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', deathMessage))

            // Play death sound globally
            val deathSound = Sound.valueOf(plugin.soundsConfig.getString("death")!!)
            for (player in Bukkit.getOnlinePlayers()) {
                player.playSound(player.location, deathSound, 1.0f, 1.0f)
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
        val shrinkAmount = plugin.config.getInt("border.shrink.amount", 1)
        val interval = plugin.config.getInt("border.shrink.interval", 60) * 20L // Convert to ticks
        val limit = plugin.config.getInt("border.shrink.limit", 10)

        borderShrinkTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!isActive) return@Runnable

            currentBorderSize -= shrinkAmount
            if (currentBorderSize < limit) currentBorderSize = limit.toDouble()

            val world = Bukkit.getWorlds().first()
            world.worldBorder.size = currentBorderSize

            // Warn players
            val message = plugin.messagesConfig.getString("broadcast.border_shrink")!!
                .replace("{size}", currentBorderSize.toInt().toString())
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
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
