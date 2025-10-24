package com.thebyteslayer.minecraft.event.ffa.managers

import com.thebyteslayer.minecraft.event.ffa.EventFFAPlugin
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*
import kotlin.random.Random

class FFAManager(private val plugin: EventFFAPlugin) {

    private var isActive = false
    private val playerStats = mutableMapOf<UUID, PlayerStats>()
    private var borderShrinkTask: BukkitTask? = null
    private var airDropTask: BukkitTask? = null
    private var currentBorderSize = 1000.0

    data class PlayerStats(
        var kills: Int = 0,
        var killStreak: Int = 0,
        var bestKillStreak: Int = 0
    )

    fun initFFA(sender: Player): Boolean {
        if (isActive) {
            sender.sendMessage("§cFFA is already active!")
            return false
        }

        // Set world border
        val world = sender.world
        val border = world.worldBorder
        border.center = Location(world, 0.0, 0.0, 0.0)
        border.size = 1000.0
        currentBorderSize = 1000.0

        // Prevent flying and other movements except walking, set to Adventure mode
        for (player in Bukkit.getOnlinePlayers()) {
            player.allowFlight = false
            player.isFlying = false
            player.walkSpeed = 0.2f
            player.flySpeed = 0.1f
            player.gameMode = GameMode.ADVENTURE
        }

        sender.sendMessage("§aFFA initialized! Border set to 1000x1000, players set to Adventure mode and can only walk.")
        return true
    }

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
            teleportToRandomLocation(player, currentBorderSize.toInt() / 2)
            player.gameMode = GameMode.SURVIVAL
            player.health = 20.0
            player.foodLevel = 20
            player.inventory.clear()
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }

            // Send immersive message
            player.sendTitle("§6FFA Started!", plugin.config.getString("messages.first_rtp", ""), 10, 70, 20)
        }

        // Start border shrinking
        startBorderShrink()

        // Start air drops
        startAirDrops()

        sender.sendMessage("§aFFA started! Players teleported and session begun.")
        return true
    }

    fun stopFFA(): Boolean {
        if (!isActive) return false

        isActive = false

        // Cancel tasks
        borderShrinkTask?.cancel()
        airDropTask?.cancel()

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
            meta?.setDisplayName("§bInfinite Snowball")
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
            val streakBrokenMessage = plugin.config.getString("messages.streak_broken", "{player}'s &c{streak} &7kill streak was broken by &c{killer}&7!")
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
            val killMessage = plugin.config.getString("messages.kill", "{player} was killed by {killer}!")
                ?.replace("{player}", victim.name)
                ?.replace("{killer}", killer.name)
            if (killMessage != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', killMessage))
            }
        } else {
            // Send death message
            val deathMessage = plugin.config.getString("messages.death", "{player} died!")
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
            teleportToRandomLocation(player, currentBorderSize.toInt() / 2)
            player.sendTitle("§aRespawned!", plugin.config.getString("messages.respawn_rtp", ""), 10, 70, 20)
        }
    }

    private fun isDangerousBlock(block: org.bukkit.block.Block): Boolean {
        val type = block.type
        return when (type) {
            Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.MAGMA_BLOCK -> true
            else -> false
        }
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

        // Check for dangerous blocks in a 3x3x3 area around the player
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val checkBlock = world.getBlockAt(x + dx, y + dy, z + dz)
                    if (isDangerousBlock(checkBlock)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    private fun teleportToRandomLocation(player: Player, radius: Int) {
        val world = player.world
        val border = world.worldBorder

        var attempts = 0
        val maxAttempts = 100 // Increased attempts for safer teleportation

        while (attempts < maxAttempts) {
            val angle = Random.nextDouble() * 2 * Math.PI
            val distance = Random.nextDouble() * radius

            val x = border.center.x + distance * Math.cos(angle)
            val z = border.center.z + distance * Math.sin(angle)

            // Find a safe Y position
            for (y in 320 downTo 1) {
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

    private fun startAirDrops() {
        val cooldown = plugin.config.getInt("air_drop.cooldown", 180) * 20L // Convert to ticks

        airDropTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!isActive) return@Runnable

            spawnAirDrop()
        }, cooldown, cooldown)
    }

    private fun spawnAirDrop() {
        val world = Bukkit.getWorlds().first()
        val border = world.worldBorder
        val radius = (currentBorderSize / 2).toInt()

        // Find random air location within border
        var location: Location? = null
        var attempts = 0

        while (location == null && attempts < 50) {
            val angle = Random.nextDouble() * 2 * Math.PI
            val distance = Random.nextDouble() * radius

            val x = border.center.x + distance * Math.cos(angle)
            val z = border.center.z + distance * Math.sin(angle)

            // Find ground level
            val blockX = x.toInt()
            val blockZ = z.toInt()

            for (y in 320 downTo 0) {
                val block = world.getBlockAt(blockX, y, blockZ)
                if (!block.isEmpty && block.type != Material.WATER && block.type != Material.LAVA) {
                    location = Location(world, x, (y + 1).toDouble(), z)
                    break
                }
            }
            attempts++
        }

        if (location != null) {
            // Set gold block
            val goldBlockLocation = location.clone().subtract(0.0, 1.0, 0.0)
            goldBlockLocation.block.type = Material.GOLD_BLOCK

            // Spawn chest after delay
            val spawnTime = plugin.config.getInt("air_drop.spawn_time", 60) * 20L
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!isActive) return@Runnable

                // Replace gold block with chest
                goldBlockLocation.block.type = Material.CHEST
                val chest = goldBlockLocation.block.state as? org.bukkit.block.Chest ?: return@Runnable

                // Fill with loot
                val inventory = chest.inventory
                val lootTable = plugin.config.getList("loot_table") as? List<Map<String, Any>> ?: return@Runnable

                for (lootItem in lootTable) {
                    val chance = (lootItem["chance"] as? Number)?.toDouble() ?: 1.0
                    if (Random.nextDouble() <= chance) {
                        val materialName = lootItem["material"] as? String ?: continue
                        val material = Material.valueOf(materialName.uppercase())
                        val amountString = lootItem["amount"] as? String ?: "1"

                        val amount = if (amountString.contains("-")) {
                            val range = amountString.split("-").map { it.toInt() }
                            Random.nextInt(range[0], range[1] + 1)
                        } else {
                            amountString.toInt()
                        }

                        val itemStack = ItemStack(material, amount)

                        // Handle potions
                        if (material == Material.POTION) {
                            val data = lootItem["data"] as? String
                            if (data != null) {
                                // Parse potion data (type:duration:amplifier)
                                val parts = data.split(":")
                                if (parts.size >= 2) {
                                    val potionType = PotionEffectType.getByName(parts[0])
                                    val duration = parts[1].toInt() * 20 // Convert seconds to ticks
                                    val amplifier = if (parts.size >= 3) parts[2].toInt() else 0

                                    if (potionType != null) {
                                        val meta = itemStack.itemMeta as? org.bukkit.inventory.meta.PotionMeta
                                        meta?.addCustomEffect(PotionEffect(potionType, duration, amplifier), true)
                                        itemStack.itemMeta = meta
                                    }
                                }
                            }
                        }

                        // Add to random slot
                        val emptySlots = inventory.contents.indices.filter { inventory.getItem(it) == null }
                        if (emptySlots.isNotEmpty()) {
                            inventory.setItem(emptySlots.random(), itemStack)
                        }
                    }
                }

                // Announce air drop
                for (player in Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6Air drop spawned at ${goldBlockLocation.x.toInt()}, ${goldBlockLocation.z.toInt()}!")
                }
            }, spawnTime)
        }
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

    fun getPlayerStats(uuid: UUID): PlayerStats? = playerStats[uuid]
}
