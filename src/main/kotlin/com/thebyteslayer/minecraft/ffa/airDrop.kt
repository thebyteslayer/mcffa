package com.thebyteslayer.minecraft.ffa

import org.bukkit.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class airDrop(private val plugin: FFAPlugin, private val ffaManager: ffa) {

    private var airDropTask: BukkitTask? = null

    fun startAirDrops() {
        val interval = plugin.config.getInt("air_drop.interval", 180) * 20L // Convert to ticks

        airDropTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!ffaManager.isFFAStarted()) return@Runnable

            spawnAirDrop()
        }, interval, interval)
    }

    fun stopAirDrops() {
        airDropTask?.cancel()
    }

    private fun spawnAirDrop() {
        val world = Bukkit.getWorlds().first()
        val border = world.worldBorder
        val radius = (ffaManager.getCurrentBorderSize() / 2).toInt()

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
                // Skip water, lava, and leaves - fall through them
                if (!block.isEmpty && block.type != Material.WATER && block.type != Material.LAVA &&
                    !block.type.name.contains("LEAVES")) {
                    location = Location(world, x, (y + 1).toDouble(), z)
                    break
                }
            }
            attempts++
        }

        if (location != null) {
            val dropTime = plugin.config.getInt("air_drop.drop_time", 30)
            val showTimes = setOf(30, 20, 10, 5, 3, 2, 1) // Only show countdown at these specific times

            // Announce air drop immediately
            val initialMessage = plugin.messagesConfig.getString("broadcast.airdrop_incoming")!!
                .replace("{x}", location.x.toInt().toString())
                .replace("{y}", location.z.toInt().toString())
                .replace("{time}", dropTime.toString())
                .replace("{time_label}", "seconds")
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', initialMessage))
            }

            // Play airdrop landing sound globally
            val landingSound = org.bukkit.Sound.valueOf(plugin.soundsConfig.getString("airdrop_landing")!!)
            for (player in Bukkit.getOnlinePlayers()) {
                player.playSound(player.location, landingSound, 1.0f, 1.0f)
            }

            // Show countdown messages
            for (remainingTime in showTimes) {
                if (remainingTime != dropTime) { // Skip initial time since we already showed it
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        if (!ffaManager.isFFAStarted()) return@Runnable
                        val timeLabel = if (remainingTime == 1) "second" else "seconds"
                        val countdownMessage = plugin.messagesConfig.getString("broadcast.airdrop_countdown")!!
                            .replace("{time}", remainingTime.toString())
                            .replace("{time_label}", timeLabel)
                        for (player in Bukkit.getOnlinePlayers()) {
                            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', countdownMessage))
                        }
                    }, (dropTime - remainingTime) * 20L)
                }
            }

            // Drop air drop after delay
            val dropTimeTicks = dropTime * 20L
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!ffaManager.isFFAStarted()) return@Runnable

                // Check if the chest location is empty (don't break blocks)
                if (!location.block.isEmpty) {
                    // Location is occupied, skip this air drop
                    // Note: Occupied check was removed, air drops now always land
                    return@Runnable
                }

                // Spawn chest at the drop location
                location.block.type = Material.CHEST
                val chest = location.block.state as? org.bukkit.block.Chest ?: return@Runnable

                // Fill with loot
                val inventory = chest.inventory
                val lootTable = loadLootTable()

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

                // Announce that the air drop has landed
                val landedMessage = plugin.messagesConfig.getString("broadcast.airdrop_landed")!!
                    .replace("{x}", location.x.toInt().toString())
                    .replace("{y}", location.z.toInt().toString())
                for (player in Bukkit.getOnlinePlayers()) {
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', landedMessage))
                }

                // Play airdrop landed sound globally
                val landedSound = org.bukkit.Sound.valueOf(plugin.soundsConfig.getString("airdrop_landed")!!)
                for (player in Bukkit.getOnlinePlayers()) {
                    player.playSound(player.location, landedSound, 1.0f, 1.0f)
                }

            }, dropTimeTicks)
        }
    }

    private fun loadLootTable(): List<Map<String, Any>> {
        return try {
            val lootTableFile = File(plugin.dataFolder, "loot.json")
            if (!lootTableFile.exists()) {
                plugin.saveResource("loot.json", false)
            }
            val gson = Gson()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            gson.fromJson(lootTableFile.readText(), type)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load loot table from loot.json: ${e.message}")
            emptyList()
        }
    }
}
