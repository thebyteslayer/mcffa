package com.thebyteslayer.minecraft.ffa

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.YamlConfiguration
import com.thebyteslayer.minecraft.ffa.listener
import com.thebyteslayer.minecraft.ffa.ffa
import com.thebyteslayer.minecraft.ffa.scoreboard
import java.io.File

class FFAPlugin : JavaPlugin() {

    lateinit var environmentManager: environment
    lateinit var ffaManager: ffa
    lateinit var scoreboardManager: scoreboard
    lateinit var rtpManager: rtp
    lateinit var messagesConfig: YamlConfiguration
    lateinit var soundsConfig: YamlConfiguration

    override fun onEnable() {
        saveDefaultConfig()
        saveResource("messages.yml", false)
        saveResource("sounds.yml", false)

        // Load messages configuration
        val messagesFile = File(dataFolder, "messages.yml")
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile)

        // Load sounds configuration
        val soundsFile = File(dataFolder, "sounds.yml")
        soundsConfig = YamlConfiguration.loadConfiguration(soundsFile)

        environmentManager = environment(this)
        ffaManager = ffa(this)
        scoreboardManager = scoreboard(this)
        rtpManager = rtp(this)

        // Setup environment on plugin load
        environmentManager.setupEnvironment()

        // Register commands
        getCommand("environment")?.setExecutor(environmentManager)
        getCommand("start")?.setExecutor(start(this))
        getCommand("stop")?.setExecutor(stop(this))
        getCommand("leaderboard")?.setExecutor(leaderboard(this))
        getCommand("rtp")?.setExecutor(rtpManager)
        getCommand("reload")?.setExecutor(reload(this))

        // Register listeners
        server.pluginManager.registerEvents(listener(this), this)

        logger.info("FFA Plugin has been enabled!")
    }

    override fun onDisable() {
        if (::ffaManager.isInitialized) {
            ffaManager.stopFFA()
        }
        logger.info("FFA Plugin has been disabled!")
    }
}
