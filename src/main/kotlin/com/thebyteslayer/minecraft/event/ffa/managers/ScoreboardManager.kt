package com.thebyteslayer.minecraft.event.ffa.managers

import com.thebyteslayer.minecraft.event.ffa.EventFFAPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import org.bukkit.scoreboard.Team

class ScoreboardManager(private val plugin: EventFFAPlugin) {

    private val scoreboardManager: ScoreboardManager = Bukkit.getScoreboardManager()
    private val scoreboard: Scoreboard = scoreboardManager.newScoreboard

    init {
        // Create teams for different kill counts
        createTeams()
    }

    private fun createTeams() {
        // Create teams with priorities (higher number = higher priority)
        for (i in 0..50) {
            val teamName = "kills_$i"
            var team = scoreboard.getTeam(teamName)
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName)
            }
            team.color = ChatColor.WHITE
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
        }
    }

    fun updatePlayerSuffix(player: Player) {
        if (!plugin.ffaManager.isFFAStarted()) {
            // Reset to no suffix if FFA not active
            player.scoreboard = scoreboardManager.mainScoreboard
            return
        }

        val stats = plugin.ffaManager.getPlayerStats(player.uniqueId)
        if (stats != null) {
            val kills = stats.kills
            val streak = stats.killStreak

            // Remove from current team
            scoreboard.teams.forEach { team ->
                team.removeEntry(player.name)
            }

            // Add to appropriate team
            val teamName = "kills_${minOf(kills, 50)}"
            val team = scoreboard.getTeam(teamName)
            team?.addEntry(player.name)

            // Set suffix
            val suffix = " §7[${kills}⚔ ${streak}🔥]"
            team?.suffix = suffix

            // Apply scoreboard
            player.scoreboard = scoreboard
        }
    }

    fun updateAllPlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            updatePlayerSuffix(player)
        }
    }

    fun resetAllPlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            player.scoreboard = scoreboardManager.mainScoreboard
        }
    }
}
