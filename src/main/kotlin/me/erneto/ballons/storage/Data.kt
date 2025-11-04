package me.erneto.ballons.storage

import me.erneto.ballons.models.PlayerBalloonData
import java.util.UUID

object Data {
    private lateinit var cache: Cache
    private lateinit var balloonsDAO: BalloonsDAO

    fun load() {
        cache = Cache()
        balloonsDAO = BalloonsDAO(cache)
    }

    suspend fun addOwnedBalloon(playerId: UUID, balloonId: String) {
        val current = getPlayerData(playerId)
        current.ownedBalloons.add(balloonId)
        updatePlayerData(playerId, current)
    }

    suspend fun removeOwnedBalloon(playerId: UUID, balloonId: String) {
        val current = getPlayerData(playerId)
        current.ownedBalloons.remove(balloonId)
        updatePlayerData(playerId, current)
    }

    suspend fun hasBalloon(playerId: UUID, balloonId: String): Boolean {
        return getPlayerData(playerId).ownedBalloons.contains(balloonId)
    }

    suspend fun getOwnedBalloons(playerId: UUID): Set<String> {
        return getPlayerData(playerId).ownedBalloons
    }

    suspend fun setEquippedBalloon(playerId: UUID, balloonId: String?) {
        val current = getPlayerData(playerId)
        val updated = current.copy(equippedBalloon = balloonId)
        updatePlayerData(playerId, updated)
    }

    suspend fun getEquippedBalloon(playerId: UUID): String? {
        return getPlayerData(playerId).equippedBalloon
    }

    suspend fun loadPlayerAtCache(playerId: UUID) {
        cache.loadPlayerData(playerId, getPlayerData(playerId))
    }

    fun unloadPlayerFromCache(playerId: UUID) {
        cache.removePlayerData(playerId)
    }

    fun getFromCache(playerId: UUID): PlayerBalloonData? {
        return cache.getPlayerData(playerId)
    }

    suspend fun createPlayer(playerId: UUID) {
        balloonsDAO.createPlayer(playerId)
    }

    suspend fun deletePlayer(playerId: UUID) {
        balloonsDAO.deletePlayer(playerId)
    }

    private suspend fun getPlayerData(playerId: UUID): PlayerBalloonData {
        return balloonsDAO.getPlayerData(playerId)
    }

    private suspend fun updatePlayerData(playerId: UUID, data: PlayerBalloonData) {
        balloonsDAO.updatePlayerData(playerId, data)
    }
}
