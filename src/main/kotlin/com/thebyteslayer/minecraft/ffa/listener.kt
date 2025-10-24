package com.thebyteslayer.minecraft.ffa

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class listener(private val plugin: FFAPlugin) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer

        // Suppress default Minecraft death messages always
        event.deathMessage = null

        plugin.ffaManager.handlePlayerDeath(victim, killer)

        // Update scoreboard for all players after death
        plugin.server.scheduler.runTask(plugin) { _ ->
            plugin.scoreboardManager.updateAllPlayers()
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        // Handle respawn teleport
        plugin.ffaManager.handlePlayerRespawn(player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Update scoreboard for joining player
        plugin.scoreboardManager.updatePlayerSuffix(player)

        // Set adventure mode for late joiners if FFA is not active
        if (!plugin.ffaManager.isFFAStarted()) {
            player.allowFlight = false
            player.isFlying = false
            player.walkSpeed = 0.2f
            player.flySpeed = 0.1f
            player.gameMode = org.bukkit.GameMode.ADVENTURE
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Clean up when player leaves
        plugin.scoreboardManager.resetAllPlayers()
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        // Handle infinite snowball - ensure normal snowball mechanics work
        if (item.type == Material.SNOWBALL && item.itemMeta?.displayName == "§bSnowball") {
            // Don't cancel the event - let normal snowball throwing happen
            // Give another snowball after throwing
            plugin.server.scheduler.runTask(plugin) { _ ->
                if (player.inventory.itemInMainHand.type == Material.SNOWBALL &&
                    player.inventory.itemInMainHand.itemMeta?.displayName == "§bSnowball") {
                    player.inventory.setItemInMainHand(ItemStack(Material.SNOWBALL).apply {
                        itemMeta = itemMeta?.apply {
                            setDisplayName("§bSnowball")
                        }
                    })
                } else if (player.inventory.itemInOffHand.type == Material.SNOWBALL &&
                           player.inventory.itemInOffHand.itemMeta?.displayName == "§bSnowball") {
                    player.inventory.setItemInOffHand(ItemStack(Material.SNOWBALL).apply {
                        itemMeta = itemMeta?.apply {
                            setDisplayName("§bSnowball")
                        }
                    })
                } else {
                    // Add to inventory if not holding
                    val snowball = ItemStack(Material.SNOWBALL)
                    snowball.itemMeta = snowball.itemMeta?.apply {
                        setDisplayName("§bSnowball")
                    }
                    player.inventory.addItem(snowball)
                }
            }
        }
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        // Only track damage during FFA
        if (!plugin.ffaManager.isFFAStarted()) return

        val damager = event.damager
        val victim = event.entity

        // Update scoreboard periodically during combat
        if (damager is Player && victim is Player) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                plugin.scoreboardManager.updateAllPlayers()
            }, 1L)
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        // Prevent all damage during FFA initialization and after FFA stops
        if (!plugin.ffaManager.isFFAStarted()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return

        // Prevent hunger loss always
        event.isCancelled = true
        player.foodLevel = 20
        player.saturation = 20.0f
    }
}
