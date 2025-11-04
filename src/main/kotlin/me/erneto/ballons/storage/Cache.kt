package me.erneto.ballons.storage

import me.erneto.ballons.models.PlayerBalloonData
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Cache {
    private val playerDataCache = ConcurrentHashMap<UUID, PlayerBalloonData>()

    fun getPlayerData(playerId: UUID): PlayerBalloonData? {
        return playerDataCache[playerId]
    }

    fun updatePlayerData(playerId: UUID, data: PlayerBalloonData) {
        playerDataCache[playerId] = data
    }

    fun loadPlayerData(playerId: UUID, data: PlayerBalloonData) {
        playerDataCache[playerId] = data
    }

    fun removePlayerData(playerId: UUID) {
        playerDataCache.remove(playerId)
    }

    fun clear() {
        playerDataCache.clear()
    }

    fun size(): Int = playerDataCache.size
}