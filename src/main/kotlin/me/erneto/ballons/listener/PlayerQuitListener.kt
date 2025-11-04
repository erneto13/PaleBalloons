package me.erneto.ballons.listener

import me.erneto.ballons.BalloonManager
import me.erneto.ballons.storage.Data
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PlayerQuitListener(private val balloonManager: BalloonManager) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        balloonManager.removePlayerBalloon(player.uniqueId)
        Data.unloadPlayerFromCache(player.uniqueId)
    }
}