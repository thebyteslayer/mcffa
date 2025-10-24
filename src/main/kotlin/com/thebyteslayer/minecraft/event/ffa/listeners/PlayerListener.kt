package com.thebyteslayer.minecraft.event.ffa.listeners

import com.thebyteslayer.minecraft.event.ffa.EventFFAPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class PlayerListener(private val plugin: EventFFAPlugin) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer

        // Suppress default Minecraft death messages during FFA
        if (plugin.ffaManager.isFFAStarted()) {
            event.deathMessage = null
        }

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
        if (item.type == Material.SNOWBALL && item.itemMeta?.displayName == "§bInfinite Snowball") {
            // Don't cancel the event - let normal snowball throwing happen
            // Give another snowball after throwing
            plugin.server.scheduler.runTask(plugin) { _ ->
                if (player.inventory.itemInMainHand.type == Material.SNOWBALL &&
                    player.inventory.itemInMainHand.itemMeta?.displayName == "§bInfinite Snowball") {
                    player.inventory.setItemInMainHand(ItemStack(Material.SNOWBALL).apply {
                        itemMeta = itemMeta?.apply {
                            setDisplayName("§bInfinite Snowball")
                        }
                    })
                } else if (player.inventory.itemInOffHand.type == Material.SNOWBALL &&
                           player.inventory.itemInOffHand.itemMeta?.displayName == "§bInfinite Snowball") {
                    player.inventory.setItemInOffHand(ItemStack(Material.SNOWBALL).apply {
                        itemMeta = itemMeta?.apply {
                            setDisplayName("§bInfinite Snowball")
                        }
                    })
                } else {
                    // Add to inventory if not holding
                    val snowball = ItemStack(Material.SNOWBALL)
                    snowball.itemMeta = snowball.itemMeta?.apply {
                        setDisplayName("§bInfinite Snowball")
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
}
