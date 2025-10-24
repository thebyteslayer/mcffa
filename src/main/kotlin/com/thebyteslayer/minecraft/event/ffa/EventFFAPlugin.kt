package com.thebyteslayer.minecraft.event.ffa

import org.bukkit.plugin.java.JavaPlugin
import com.thebyteslayer.minecraft.event.ffa.commands.FFACommand
import com.thebyteslayer.minecraft.event.ffa.listeners.PlayerListener
import com.thebyteslayer.minecraft.event.ffa.managers.FFAManager
import com.thebyteslayer.minecraft.event.ffa.managers.ScoreboardManager

class EventFFAPlugin : JavaPlugin() {

    lateinit var ffaManager: FFAManager
    lateinit var scoreboardManager: ScoreboardManager

    override fun onEnable() {
        saveDefaultConfig()

        ffaManager = FFAManager(this)
        scoreboardManager = ScoreboardManager(this)

        // Register commands
        getCommand("ffa")?.setExecutor(FFACommand(this))

        // Register listeners
        server.pluginManager.registerEvents(PlayerListener(this), this)

        logger.info("Event FFA Plugin has been enabled!")
    }

    override fun onDisable() {
        if (::ffaManager.isInitialized) {
            ffaManager.stopFFA()
        }
        logger.info("Event FFA Plugin has been disabled!")
    }
}
