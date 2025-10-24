package com.thebyteslayer.minecraft.ffa

import org.bukkit.entity.Player

class scoreboard(private val plugin: FFAPlugin) {

    private val suffixManager = suffix(plugin)

    fun updatePlayerSuffix(player: Player) {
        suffixManager.updatePlayerSuffix(player)
    }

    fun updateAllPlayers() {
        suffixManager.updateAllPlayers()
    }

    fun resetAllPlayers() {
        suffixManager.resetAllPlayers()
    }
}
