package me.erneto.ballons.listener


import me.erneto.ballons.BalloonManager
import me.erneto.ballons.storage.Data
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(private val balloonManager: BalloonManager) : Listener {

    @EventHandler
    suspend fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        Data.createPlayer(player.uniqueId)
        Data.loadPlayerAtCache(player.uniqueId)
        balloonManager.loadPlayerBalloon(player)
    }
}



